package com.tianji.chat.rag;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class KnowledgeSearchResult {

    @Builder.Default
    private List<KnowledgeChunkHit> retrievedHits = Collections.emptyList();

    @Builder.Default
    private List<KnowledgeChunkHit> referenceHits = Collections.emptyList();

    private double bestScore;

    public boolean hasReferenceContext() {
        return referenceHits != null && !referenceHits.isEmpty();
    }

    public String buildReferenceContext() {
        if (!hasReferenceContext()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < referenceHits.size(); i++) {
            KnowledgeChunkHit hit = referenceHits.get(i);
            builder.append("片段").append(i + 1).append("：\n")
                    .append("标题：").append(hit.displayTitle()).append("\n")
                    .append("内容：\n").append(hit.displayContent());
            if (i < referenceHits.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }
}
