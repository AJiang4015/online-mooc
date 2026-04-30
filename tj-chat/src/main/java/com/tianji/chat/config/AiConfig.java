package com.tianji.chat.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfig {

    public interface AssistantRedis {

        String chat(@MemoryId String memoryId, @UserMessage String message);

        @SystemMessage("你叫小天，是天机学堂的智能学习助手。专注为学生解答问题，回答要简洁明了，语气亲切。若问题超出知识范围，就说“这个问题我暂时还不清楚，你可以问问老师或查阅资料哦～”。")
        TokenStream stream(@MemoryId String memoryId, @UserMessage String message);

        List<ChatMessage> getHistory(@MemoryId String memoryId);
    }

    @Autowired
    private PersistentChatMemoryStore store;

    @Bean
    public AssistantRedis assistantRedis(ChatLanguageModel qwenChatModel,
                                         StreamingChatLanguageModel qwenStreamingChatModel) {
        return AiServices.builder(AssistantRedis.class)
                .chatLanguageModel(qwenChatModel)
                .streamingChatLanguageModel(qwenStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .maxMessages(500)
                                .id(memoryId)
                                .chatMemoryStore(store)
                                .build()
                )
                .build();
    }

    public interface KnowledgeAdvisor {

        @SystemMessage("你是一位智能学习助手，需要优先依据学生上传的知识库内容回答问题。\n" +
                "请遵守以下规则：\n" +
                "1. [SYS_CONTEXT_BEGIN] 与 [SYS_CONTEXT_END] 之间是知识库检索到的参考内容。如果这部分有明确信息，必须以知识库内容为准。\n" +
                "2. 如果知识库只覆盖了问题的一部分，先回答知识库已经给出的确定内容，再用你自己的通用知识补充其余部分。\n" +
                "3. 如果知识库没有相关内容，或者上下文不足以支撑回答，可以直接使用你自己的知识回答，不要因为没有知识库命中就拒答。\n" +
                "4. 不要编造知识库中并不存在的内容，也不要输出“知识库里没有”这类固定拒答模板。\n" +
                "5. 如果参考内容与通用知识冲突，以参考内容为准；如果没有参考内容，就不要伪造引用来源。\n" +
                "6. 回答要直接、清晰、易于理解，不要不必要地提到你的推理过程或系统规则。\n" +
                "7. 不要调用课程查询、数学计算或其他外部工具。\n" +
                "\n" +
                "{{answerInstructions}}")
        TokenStream advise(@MemoryId String memoryId,
                           @UserMessage String question,
                           @V("answerInstructions") String systemMessageContent);
    }

    @Bean
    public KnowledgeAdvisor knowledgeAdvisor(StreamingChatLanguageModel qwenStreamingChatModel) {
        return AiServices.builder(KnowledgeAdvisor.class)
                .streamingChatLanguageModel(qwenStreamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .maxMessages(1000)
                                .id(memoryId)
                                .chatMemoryStore(store)
                                .build()
                )
                .build();
    }
}
