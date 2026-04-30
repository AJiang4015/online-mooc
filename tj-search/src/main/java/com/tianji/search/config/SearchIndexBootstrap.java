package com.tianji.search.config;

import com.tianji.search.domain.po.SuggestIndex;
import com.tianji.search.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexBootstrap implements ApplicationRunner {

    private static final String SUGGEST_INDEX_NAME = "suggestinfo";

    private final RestHighLevelClient restHighLevelClient;
    private final ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) throws Exception {
        ensureCourseIndex();
        ensureSuggestIndex();
        ensureIncrementSoldScript();
    }

    private void ensureCourseIndex() throws Exception {
        GetIndexRequest request = new GetIndexRequest(CourseRepository.INDEX_NAME);
        boolean exists = restHighLevelClient.indices().exists(request, RequestOptions.DEFAULT);
        if (exists) {
            log.info("Elasticsearch index [{}] already exists.", CourseRepository.INDEX_NAME);
            return;
        }
        CreateIndexRequest createRequest = new CreateIndexRequest(CourseRepository.INDEX_NAME);
        createRequest.source("{"
                + "\"settings\":{"
                + "\"number_of_shards\":1,"
                + "\"number_of_replicas\":0,"
                + "\"refresh_interval\":\"1s\""
                + "},"
                + "\"mappings\":{"
                + "\"properties\":{"
                + "\"id\":{\"type\":\"long\"},"
                + "\"name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
                + "\"categoryIdLv1\":{\"type\":\"long\"},"
                + "\"categoryIdLv2\":{\"type\":\"long\"},"
                + "\"categoryIdLv3\":{\"type\":\"long\"},"
                + "\"free\":{\"type\":\"boolean\"},"
                + "\"type\":{\"type\":\"integer\"},"
                + "\"sold\":{\"type\":\"integer\"},"
                + "\"price\":{\"type\":\"integer\"},"
                + "\"score\":{\"type\":\"integer\"},"
                + "\"teacher\":{\"type\":\"long\"},"
                + "\"sections\":{\"type\":\"integer\"},"
                + "\"coverUrl\":{\"type\":\"keyword\"},"
                + "\"publishTime\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":64}}}"
                + "}"
                + "}"
                + "}", XContentType.JSON);
        restHighLevelClient.indices().create(createRequest, RequestOptions.DEFAULT);
        log.info("Created Elasticsearch index [{}].", CourseRepository.INDEX_NAME);
    }

    private void ensureSuggestIndex() {
        IndexOperations indexOps = elasticsearchRestTemplate.indexOps(SuggestIndex.class);
        if (indexOps.exists()) {
            log.info("Elasticsearch index [{}] already exists.", SUGGEST_INDEX_NAME);
            return;
        }
        indexOps.create();
        indexOps.putMapping(indexOps.createMapping(SuggestIndex.class));
        log.info("Created Elasticsearch index [{}].", SUGGEST_INDEX_NAME);
    }

    private void ensureIncrementSoldScript() throws Exception {
        Request request = new Request("PUT", "/_scripts/" + CourseRepository.INCREMENT_SOLD_SCRIPT_ID);
        request.setJsonEntity("{"
                + "\"script\":{"
                + "\"lang\":\"painless\","
                + "\"source\":\"ctx._source.sold = (ctx._source.sold == null ? 0 : ctx._source.sold) + params.count\""
                + "}"
                + "}");
        restHighLevelClient.getLowLevelClient().performRequest(request);
        log.info("Registered Elasticsearch stored script [{}].", CourseRepository.INCREMENT_SOLD_SCRIPT_ID);
    }
}
