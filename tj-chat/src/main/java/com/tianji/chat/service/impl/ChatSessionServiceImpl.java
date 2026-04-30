package com.tianji.chat.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.chat.config.AiConfig;
import com.tianji.chat.config.PersistentChatMemoryStore;
import com.tianji.chat.domain.po.ChatSession;
import com.tianji.chat.domain.query.RecordQuery;
import com.tianji.chat.mapper.ChatSessionMapper;
import com.tianji.chat.rag.KnowledgeAnswerService;
import com.tianji.chat.rag.KnowledgeRetrievalService;
import com.tianji.chat.rag.KnowledgeSearchResult;
import com.tianji.chat.service.IChatSessionService;
import com.tianji.chat.tools.runtime.ChatToolExecutionResult;
import com.tianji.chat.tools.runtime.ChatToolManager;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {
    private static final String SSE_NEWLINE_MARKER = "__TJ_CHAT_NL__";

    private final AiConfig.AssistantRedis assistantRedis;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ChatToolManager chatToolManager;
    private final PersistentChatMemoryStore chatMemoryStore;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final KnowledgeAnswerService knowledgeAnswerService;

    @Autowired
    public ChatSessionServiceImpl(@Lazy AiConfig.AssistantRedis assistantRedis,
                                  StreamingChatLanguageModel streamingChatLanguageModel,
                                  ChatToolManager chatToolManager,
                                  @Lazy PersistentChatMemoryStore chatMemoryStore,
                                  KnowledgeRetrievalService knowledgeRetrievalService,
                                  KnowledgeAnswerService knowledgeAnswerService) {
        this.assistantRedis = assistantRedis;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.chatToolManager = chatToolManager;
        this.chatMemoryStore = chatMemoryStore;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.knowledgeAnswerService = knowledgeAnswerService;
    }

    @Override
    public String chat(String sessionId, String message) {
        Optional<ChatToolExecutionResult> routedResponse = tryHandleBuiltinCapability(sessionId, message);
        return routedResponse.map(ChatToolExecutionResult::getResponse)
                .orElseGet(() -> assistantRedis.chat(sessionId, message));
    }

    @Override
    public PageDTO<ChatSession> getRecord(RecordQuery query) {
        List<ChatSession> mergedSessions = chatMemoryStore.getMergedSessions(query.getSessionId());
        if (mergedSessions.isEmpty()) {
            return new PageDTO<>(0L, 0L, Collections.emptyList());
        }

        mergedSessions.sort(Comparator.comparing(
                ChatSession::getSegmentIndex,
                Comparator.nullsFirst(Integer::compareTo)).reversed());

        int fromIndex = Math.min(query.from(), mergedSessions.size());
        int toIndex = Math.min(fromIndex + query.getPageSize(), mergedSessions.size());
        long total = mergedSessions.size();
        long pages = (total + query.getPageSize() - 1L) / query.getPageSize();
        return new PageDTO<>(total, pages, mergedSessions.subList(fromIndex, toIndex));
    }

    private String formatSseMessage(String data) {
        return data == null ? "" : data;
    }

    @Override
    public SseEmitter test(String sessionId, String message) {
        if (UserContext.getUser() == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("请先登录"));
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(1800000L);
        StringBuilder responseBuilder = new StringBuilder();

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> log.info("测试SSE流已完成"));
        emitter.onError(error -> log.error("测试SSE流发生错误", error));

        try {
            streamingChatLanguageModel.generate(message, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    try {
                        responseBuilder.append(token);
                        emitter.send(SseEmitter.event()
                                .data(formatSseMessage(token), MediaType.TEXT_PLAIN)
                                .name("message"));
                    } catch (IOException e) {
                        log.error("发送测试SSE消息失败", e);
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    try {
                        sendDone(emitter);
                        emitter.complete();
                        log.info("测试数据接收完成：{}", responseBuilder);
                    } catch (IOException e) {
                        log.error("发送测试完成消息失败", e);
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error("测试生成过程发生错误", error);
                    emitter.completeWithError(error);
                }
            });
        } catch (Exception e) {
            log.error("测试生成过程发生异常", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public SseEmitter stream(String memoryId, String message) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("请先登录"));
            return emitter;
        }

        Optional<ChatToolExecutionResult> routedResponse = tryHandleBuiltinCapability(memoryId, message);
        if (routedResponse.isPresent()) {
            return buildDirectResponseEmitter(routedResponse.get().getResponse());
        }

        SseEmitter emitter = new SseEmitter(1800000L);
        StringBuilder responseBuilder = new StringBuilder();
        AtomicBoolean isStreamCompleted = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            log.warn("SSE流超时，但无法终止TokenStream");
            try {
                sendDone(emitter);
            } catch (IOException e) {
                log.error("发送超时完成消息失败", e);
            }
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            if (!isStreamCompleted.get()) {
                log.warn("SSE流被客户端主动关闭，TokenStream可能仍在运行");
            }
        });

        emitter.onError(error -> {
            log.error("SSE流发生错误", error);
            try {
                sendDone(emitter);
            } catch (IOException e) {
                log.error("发送错误完成消息失败", e);
            }
            emitter.complete();
        });

        try {
            TokenStream stream = assistantRedis.stream(memoryId, message);
            stream.onNext(token -> {
                try {
                    responseBuilder.append(token);
                    emitter.send(SseEmitter.event()
                            .data(formatSseMessage(token), MediaType.TEXT_PLAIN)
                            .name("message"));
                } catch (IOException e) {
                    log.error("发送SSE消息失败", e);
                    try {
                        sendDone(emitter);
                    } catch (IOException ex) {
                        log.error("发送失败完成消息失败", ex);
                    }
                    emitter.completeWithError(e);
                }
            }).onComplete(ignored -> {
                isStreamCompleted.set(true);
                try {
                    sendDone(emitter);
                    emitter.complete();
                    log.info("数据接收完成：{}", responseBuilder);
                } catch (IOException e) {
                    log.error("发送完成消息失败", e);
                }
            }).onError(error -> {
                isStreamCompleted.set(true);
                log.error("生成过程发生错误", error);
                log.info("数据接收完成：{}", responseBuilder);
                try {
                    sendDone(emitter);
                } catch (IOException e) {
                    log.error("发送错误完成消息失败", e);
                }
                emitter.complete();
            }).start();
        } catch (Exception e) {
            log.error("初始化TokenStream失败", e);
            try {
                sendDone(emitter);
            } catch (IOException ex) {
                log.error("发送初始化失败完成消息失败", ex);
            }
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public SseEmitter fileStream(String sessionId, String message) {
        if (UserContext.getUser() == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("请先登录"));
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(1800000L);
        StringBuilder originBuilder = new StringBuilder();
        AtomicBoolean isStreamCompleted = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            log.warn("文件SSE流超时，但无法终止TokenStream");
            try {
                sendDone(emitter);
            } catch (IOException e) {
                log.error("发送超时完成消息失败", e);
            }
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            if (!isStreamCompleted.get()) {
                log.warn("文件SSE流被客户端主动关闭，TokenStream可能仍在运行");
            }
        });

        emitter.onError(error -> {
            log.error("文件SSE流发生错误", error);
            try {
                sendDone(emitter);
            } catch (IOException e) {
                log.error("发送错误完成消息失败", e);
            }
            emitter.complete();
        });

        Long userId = UserContext.getUser();
        try {
            KnowledgeSearchResult searchResult = knowledgeRetrievalService.retrieve(userId, message);
            TokenStream stream = knowledgeAnswerService.advise(sessionId, message, searchResult);
            stream.onNext(token -> {
                try {
                    String sse = formatSseMessage(token);
                    originBuilder.append(sse);
                    emitter.send(SseEmitter.event()
                            .data(sse, MediaType.TEXT_PLAIN)
                            .name("message"));
                } catch (IOException e) {
                    log.error("发送SSE消息失败", e);
                    try {
                        sendDone(emitter);
                    } catch (IOException ex) {
                        log.error("发送失败完成消息失败", ex);
                    }
                    emitter.completeWithError(e);
                }
            }).onComplete(ignored -> {
                isStreamCompleted.set(true);
                try {
                    sendDone(emitter);
                    emitter.complete();
                    log.info("纯发送的消息：\n{}", originBuilder);
                } catch (IOException e) {
                    log.error("发送完成消息失败", e);
                }
            }).onError(error -> {
                isStreamCompleted.set(true);
                log.error("生成过程发生错误", error);
                try {
                    sendDone(emitter);
                } catch (IOException e) {
                    log.error("发送错误完成消息失败", e);
                }
                emitter.complete();
            }).start();
        } catch (Exception e) {
            log.error("生成过程发生异常", e);
            try {
                sendDone(emitter);
            } catch (IOException ex) {
                log.error("发送初始化失败完成消息失败", ex);
            }
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private Optional<ChatToolExecutionResult> tryHandleBuiltinCapability(String sessionId, String message) {
        Optional<ChatToolExecutionResult> result = chatToolManager.tryExecute(message);
        String userText = message == null ? "" : message.trim();
        result.ifPresent(toolResult -> appendConversationToMemory(sessionId, userText, toolResult.getResponse()));
        return result;
    }

    private void appendConversationToMemory(String sessionId, String userText, String aiText) {
        chatMemoryStore.updateMessages(sessionId, Collections.singletonList(UserMessage.from(userText)));
        chatMemoryStore.updateMessages(sessionId, Collections.singletonList(AiMessage.from(aiText)));
    }

    private SseEmitter buildDirectResponseEmitter(String response) {
        SseEmitter emitter = new SseEmitter(1800000L);
        try {
            sendDirectResponse(emitter, response);
            sendDone(emitter);
            emitter.complete();
            log.info("数据接收完成：{}", response);
        } catch (IOException e) {
            log.error("发送内置能力响应失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void sendDirectResponse(SseEmitter emitter, String response) throws IOException {
        String normalizedResponse = formatSseMessage(response).replace("\r\n", "\n");
        String[] lines = normalizedResponse.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                emitter.send(SseEmitter.event()
                        .data(lines[i], MediaType.TEXT_PLAIN)
                        .name("message"));
            }
            if (i < lines.length - 1) {
                emitter.send(SseEmitter.event()
                        .data(SSE_NEWLINE_MARKER, MediaType.TEXT_PLAIN)
                        .name("message"));
            }
        }
    }

    private void sendDone(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event()
                .data(formatSseMessage("[DONE]"), MediaType.TEXT_PLAIN)
                .name("message"));
    }
}
