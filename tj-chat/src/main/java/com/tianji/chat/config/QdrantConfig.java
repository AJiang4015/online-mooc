package com.tianji.chat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

import static com.tianji.chat.constants.AiConstants.QDRANT_COLLECTION;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "qdrant")
public class QdrantConfig {

    private String host;
    private int port;
    private boolean secure;
    private int vectorSize = 1024;

    @PostConstruct
    public void debugPrint() {
        System.out.printf("[QdrantConfig] host=%s, port=%d, secure=%b%n", host, port, secure);
    }

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(host, port, secure);
        QdrantClient client = new QdrantClient(grpcClientBuilder.build());
        ensureCollection(client);
        return client;
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(QDRANT_COLLECTION)
                .build();
    }

    private void ensureCollection(QdrantClient client) {
        try {
            List<String> collections = client.listCollectionsAsync().get();
            if (!collections.contains(QDRANT_COLLECTION)) {
                Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                        .setDistance(Collections.Distance.Cosine)
                        .setSize(vectorSize)
                        .build();
                Collections.VectorsConfig vectorsConfig = Collections.VectorsConfig.newBuilder()
                        .setParams(vectorParams)
                        .build();
                Collections.CreateCollection request = Collections.CreateCollection.newBuilder()
                        .setCollectionName(QDRANT_COLLECTION)
                        .setVectorsConfig(vectorsConfig)
                        .build();
                client.createCollectionAsync(request).get();
                log.info("Created Qdrant collection `{}` with vector size {}", QDRANT_COLLECTION, vectorSize);
                return;
            }
            Collections.CollectionInfo collectionInfo = client.getCollectionInfoAsync(QDRANT_COLLECTION).get();
            long actualVectorSize = collectionInfo.getConfig()
                    .getParams()
                    .getVectorsConfig()
                    .getParams()
                    .getSize();
            if (actualVectorSize != vectorSize) {
                throw new IllegalStateException("Qdrant collection `" + QDRANT_COLLECTION + "` vector size is "
                        + actualVectorSize + ", but chat-service expects " + vectorSize
                        + ". Please recreate the collection or align `qdrant.vector-size`.");
            }
            log.info("Qdrant collection `{}` is ready, vector size={}", QDRANT_COLLECTION, actualVectorSize);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Qdrant collection `" + QDRANT_COLLECTION + "`", e);
        }
    }
}
