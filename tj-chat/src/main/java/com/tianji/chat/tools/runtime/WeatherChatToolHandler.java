package com.tianji.chat.tools.runtime;

import com.tianji.chat.tools.ToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class WeatherChatToolHandler implements ChatToolHandler {

    private final ToolsService toolsService;

    @Override
    public String toolName() {
        return "queryTodayWeather";
    }

    @Override
    public boolean supports(String message) {
        return message.contains("天气")
                || message.contains("气温")
                || message.contains("下雨")
                || message.contains("降雨")
                || message.contains("风速")
                || message.contains("湿度")
                || message.contains("冷不冷")
                || message.contains("热不热");
    }

    @Override
    public String execute(String message) {
        return toolsService.queryTodayWeather(extractCityName(message));
    }

    private String extractCityName(String message) {
        String city = message
                .replaceAll("[？?！!，,。；;：:]", "")
                .replaceAll("请问|请帮我|帮我|麻烦|查一下|查下|查询一下|查询|告诉我|看一下|看看|帮我查|帮我看", "")
                .replaceAll("今天天气怎么样|今天天气如何|今天会不会下雨|天气怎么样|天气如何|天气|气温多少|气温|下雨吗|会不会下雨|风速多少|风大不大|湿度多少|冷不冷|热不热", "")
                .replaceAll("今天|现在|当前", "")
                .trim();
        if (city.endsWith("的")) {
            city = city.substring(0, city.length() - 1);
        }
        return city;
    }
}
