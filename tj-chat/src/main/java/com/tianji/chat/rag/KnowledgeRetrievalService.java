package com.tianji.chat.rag;

import com.tianji.chat.domain.vo.MarkdownChunk;
import com.tianji.chat.utils.QdrantEmbeddingUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.tianji.chat.constants.AiConstants.KNOWLEDGE_MATCH_THRESHOLD;
import static com.tianji.chat.constants.AiConstants.KNOWLEDGE_REFERENCE_LIMIT;
import static com.tianji.chat.constants.AiConstants.KNOWLEDGE_SEARCH_LIMIT;
import static com.tianji.chat.constants.AiConstants.QDRANT_COLLECTION;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;

    public KnowledgeSearchResult retrieve(Long userId, String question) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(matchKeyword("user_id", userId.toString()))
                    .build();
            List<Points.ScoredPoint> results = qdrantClient.searchAsync(Points.SearchPoints.newBuilder()
                    .setCollectionName(QDRANT_COLLECTION)
                    .addAllVector(queryEmbedding.vectorAsList())
                    .setLimit(KNOWLEDGE_SEARCH_LIMIT)
                    .setWithPayload(enable(true))
                    .setWithVectors(WithVectorsSelectorFactory.enable(true))
                    .setFilter(filter)
                    .build()).get();

            List<EmbeddingMatch<TextSegment>> matches = results.stream()
                    .map(point -> QdrantEmbeddingUtils.toEmbeddingMatch(point, queryEmbedding, "text_segment"))
                    .collect(Collectors.toList());

            List<KnowledgeChunkHit> retrievedHits = matches.stream()
                    .filter(match -> match.embedded() != null)
                    .map(this::toKnowledgeChunkHit)
                    .collect(Collectors.toList());
            List<KnowledgeChunkHit> referenceHits = retrievedHits.stream()
                    .filter(hit -> hit.getScore() >= KNOWLEDGE_MATCH_THRESHOLD)
                    .limit(KNOWLEDGE_REFERENCE_LIMIT)
                    .collect(Collectors.toList());
            double bestScore = retrievedHits.isEmpty() ? 0D : retrievedHits.get(0).getScore();

            log.info("知识库检索完成，question={}, retrievedCount={}, referenceCount={}, bestScore={}",
                    question, retrievedHits.size(), referenceHits.size(), bestScore);

            return KnowledgeSearchResult.builder()
                    .retrievedHits(retrievedHits)
                    .referenceHits(referenceHits)
                    .bestScore(bestScore)
                    .build();
        } catch (Exception e) {
            log.error("知识库检索失败，question={}", question, e);
            return KnowledgeSearchResult.builder()
                    .retrievedHits(Collections.emptyList())
                    .referenceHits(Collections.emptyList())
                    .bestScore(0D)
                    .build();
        }
    }

    private KnowledgeChunkHit toKnowledgeChunkHit(EmbeddingMatch<TextSegment> match) {
        String rawText = match.embedded().text();
        try {
            MarkdownChunk markdownChunk = MarkdownChunk.fromString(rawText);
            return new KnowledgeChunkHit(match.score(), markdownChunk.getTitle(), markdownChunk.getContent(), rawText);
        } catch (Exception ignored) {
            return new KnowledgeChunkHit(match.score(), null, rawText, rawText);
        }
    }
}
