package com.tianji.chat.rag;

import com.tianji.chat.config.AiConfig;
import com.tianji.chat.domain.dto.PromptBuilder;
import dev.langchain4j.service.TokenStream;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAnswerService {

    private final AiConfig.KnowledgeAdvisor knowledgeAdvisor;

    public KnowledgeAnswerService(@Lazy AiConfig.KnowledgeAdvisor knowledgeAdvisor) {
        this.knowledgeAdvisor = knowledgeAdvisor;
    }

    public TokenStream advise(String sessionId, String question, KnowledgeSearchResult searchResult) {
        String retrievalInstructions = buildRetrievalInstructions(searchResult);
        String promptPayload = PromptBuilder.buildSystemMessage(
                searchResult.buildReferenceContext(),
                retrievalInstructions
        );
        return knowledgeAdvisor.advise(sessionId, question, promptPayload);
    }

    private String buildRetrievalInstructions(KnowledgeSearchResult searchResult) {
        if (searchResult.hasReferenceContext()) {
            return "\n【检索结论】当前已命中高相关知识库内容，请优先依据上述参考片段回答；" +
                    "如果知识库只覆盖部分信息，可以在明确区分的前提下补充你的通用知识。";
        }
        return "\n【检索结论】当前没有命中高置信度知识库内容，请直接依据你的通用知识回答，" +
                "不要假装引用了知识库。";
    }
}
