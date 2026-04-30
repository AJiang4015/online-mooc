package com.tianji.chat.constants;

public interface AiConstants {

    /**
     * markdown文档集合名称 在qdrant向量数据库前的
     */
    String QDRANT_COLLECTION = "ai-chat";

    double KNOWLEDGE_MATCH_THRESHOLD = 0.75D;

    int KNOWLEDGE_SEARCH_LIMIT = 5;

    int KNOWLEDGE_REFERENCE_LIMIT = 3;

}
