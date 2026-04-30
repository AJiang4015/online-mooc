package com.tianji.chat.tools.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatToolManager {

    private final List<ChatToolHandler> toolHandlers;

    public Optional<ChatToolExecutionResult> tryExecute(String message) {
        if (!StringUtils.hasText(message)) {
            return Optional.empty();
        }
        for (ChatToolHandler toolHandler : toolHandlers) {
            if (!toolHandler.supports(message)) {
                continue;
            }
            String response = toolHandler.execute(message);
            log.info("命中普通聊天工具: {}", toolHandler.toolName());
            return Optional.of(new ChatToolExecutionResult(toolHandler.toolName(), response));
        }
        return Optional.empty();
    }
}
