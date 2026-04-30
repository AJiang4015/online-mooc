package com.tianji.chat.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tianji.chat.domain.dto.PromptBuilder;
import com.tianji.chat.domain.po.ChatSession;
import com.tianji.chat.domain.po.UserSession;
import com.tianji.chat.service.IChatSessionService;
import com.tianji.chat.service.IUserSessionService;
import com.tianji.chat.utils.DataDelayTaskHandler;
import com.tianji.common.utils.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tianji.chat.constants.RedisConstants.CHAT_MEMORY_KEY_PREFIX;
import static com.tianji.chat.constants.RedisConstants.DELAY_TASK_EXECUTE_TIME;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messageToJson;

@Configuration
@Slf4j
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final IChatSessionService chatSessionService;
    private final DataDelayTaskHandler dataDelayTaskHandler;
    private final IUserSessionService userSessionService;

    public PersistentChatMemoryStore(StringRedisTemplate redisTemplate,
                                     @Lazy IChatSessionService chatSessionService,
                                     DataDelayTaskHandler dataDelayTaskHandler,
                                     IUserSessionService userSessionService) {
        this.redisTemplate = redisTemplate;
        this.chatSessionService = chatSessionService;
        this.dataDelayTaskHandler = dataDelayTaskHandler;
        this.userSessionService = userSessionService;
    }

    private Long resolveUserId(Object sessionId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            UserSession session = userSessionService.lambdaQuery()
                    .eq(UserSession::getSessionId, sessionId)
                    .one();
            if (session == null) {
                return null;
            }
            userId = session.getUserId();
        }
        return userId;
    }

    private String getKey(Object sessionId) {
        Long userId = resolveUserId(sessionId);
        return CHAT_MEMORY_KEY_PREFIX + userId + ":" + sessionId;
    }

    private List<String> loadDatabaseMessages(Object sessionId, Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<ChatSession> chatSessionList = chatSessionService.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getSessionId, sessionId)
                .orderByAsc(ChatSession::getSegmentIndex)
                .list();
        if (CollUtil.isEmpty(chatSessionList)) {
            return Collections.emptyList();
        }
        return chatSessionList.stream()
                .map(ChatSession::getContent)
                .collect(Collectors.toList());
    }

    public List<ChatSession> getMergedSessions(String sessionId) {
        Long userId = resolveUserId(sessionId);
        if (userId == null) {
            return Collections.emptyList();
        }

        List<ChatSession> databaseSessions = chatSessionService.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getSessionId, sessionId)
                .orderByAsc(ChatSession::getSegmentIndex)
                .list();
        List<String> redisMessages = redisTemplate.opsForList().range(getKey(sessionId), 0, -1);
        if (CollUtil.isEmpty(databaseSessions) && CollUtil.isEmpty(redisMessages)) {
            return Collections.emptyList();
        }

        List<ChatSession> mergedSessions = new ArrayList<>();
        if (CollUtil.isNotEmpty(databaseSessions)) {
            mergedSessions.addAll(databaseSessions);
        }
        if (CollUtil.isEmpty(redisMessages)) {
            return mergedSessions;
        }

        int nextSegmentIndex = mergedSessions.isEmpty()
                ? 0
                : Optional.ofNullable(mergedSessions.get(mergedSessions.size() - 1).getSegmentIndex()).orElse(-1) + 1;
        LocalDateTime baseTime = mergedSessions.isEmpty()
                ? LocalDateTime.now()
                : Optional.ofNullable(mergedSessions.get(mergedSessions.size() - 1).getCreateTime()).orElse(LocalDateTime.now());

        for (int i = 0; i < redisMessages.size(); i++) {
            mergedSessions.add(ChatSession.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .segmentIndex(nextSegmentIndex + i)
                    .content(redisMessages.get(i))
                    .createTime(baseTime.plusNanos(i + 1L))
                    .build());
        }
        return mergedSessions;
    }

    @Override
    public List<ChatMessage> getMessages(Object sessionId) {
        try {
            Long userId = resolveUserId(sessionId);
            List<String> redisMessages = redisTemplate.opsForList().range(getKey(sessionId), 0, -1);
            log.info("getMessages redisMessages:{}", redisMessages);

            List<String> databaseMessages = loadDatabaseMessages(sessionId, userId);
            if (CollUtil.isEmpty(redisMessages) && CollUtil.isEmpty(databaseMessages)) {
                return Collections.emptyList();
            }

            List<String> mergedMessages = CollUtil.newArrayList();
            if (CollUtil.isNotEmpty(databaseMessages)) {
                mergedMessages.addAll(databaseMessages);
            }
            if (CollUtil.isNotEmpty(redisMessages)) {
                mergedMessages.addAll(redisMessages);
            }

            log.info("getMessages merged dbCount={}, redisCount={}, totalCount={}",
                    databaseMessages.size(),
                    redisMessages == null ? 0 : redisMessages.size(),
                    mergedMessages.size());
            return messagesFromJson(mergedMessages.toString());
        } catch (Exception e) {
            log.error("读取对话历史失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            for (ChatMessage message : messages) {
                if (!(message instanceof UserMessage || message instanceof AiMessage)) {
                    return;
                }
            }
            ChatMessage chatMessage = messages.get(messages.size() - 1);
            String json = messageToJson(chatMessage);
            if (chatMessage instanceof UserMessage) {
                JSONObject root = JSON.parseObject(json);
                JSONArray contents = root.getJSONArray("contents");
                JSONObject firstContent = contents.getJSONObject(0);
                String originalText = firstContent.getString("text");
                String processedText = PromptBuilder.extractOriginalMessage(originalText);
                firstContent.put("text", processedText);
                json = root.toJSONString();
            }

            redisTemplate.opsForList().rightPush(getKey(sessionId), json);
            log.info("存数据到redis中 sessionId{}:json:{}", sessionId, json);

            Map<String, Object> map = new HashMap<>();
            map.put("key", getKey(sessionId));
            map.put("num", messages.size());
            String jsonStr = JSONUtil.toJsonStr(map);
            dataDelayTaskHandler.addDelayedTask(jsonStr, DELAY_TASK_EXECUTE_TIME, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("更新对话历史失败", e);
        }
    }

    @Override
    public void deleteMessages(Object sessionId) {
        try {
            redisTemplate.delete(getKey(sessionId));
        } catch (Exception e) {
            log.error("删除对话历史失败", e);
        }
    }
}
