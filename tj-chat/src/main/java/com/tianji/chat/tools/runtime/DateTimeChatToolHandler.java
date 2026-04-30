package com.tianji.chat.tools.runtime;

import com.tianji.chat.tools.ToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
public class DateTimeChatToolHandler implements ChatToolHandler {

    private final ToolsService toolsService;

    @Override
    public String toolName() {
        return "currentDateTime";
    }

    @Override
    public boolean supports(String message) {
        return message.contains("今天几号")
                || message.contains("今天多少号")
                || message.contains("今天日期")
                || message.contains("当前日期")
                || message.contains("现在几点")
                || message.contains("几点了")
                || message.contains("当前时间")
                || message.contains("今天星期几")
                || message.contains("现在几号");
    }

    @Override
    public String execute(String message) {
        return toolsService.currentDateTime();
    }
}
