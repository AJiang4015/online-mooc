package com.tianji.chat.tools.runtime;

import com.tianji.chat.domain.vo.CourseVO;
import com.tianji.chat.tools.ToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Order(30)
@RequiredArgsConstructor
public class CourseChatToolHandler implements ChatToolHandler {

    private final ToolsService toolsService;

    @Override
    public String toolName() {
        return "queryCourse";
    }

    @Override
    public boolean supports(String message) {
        return message.contains("课程");
    }

    @Override
    public String execute(String message) {
        String keyword = extractCourseKeyword(message);
        if (!StringUtils.hasText(keyword)) {
            return "请告诉我你想查询什么课程，例如“帮我找 Java 课程”。";
        }
        return formatCourseResult(keyword, toolsService.queryCourse(keyword));
    }

    private String extractCourseKeyword(String message) {
        String keyword = message
                .replaceAll("[，。！？；;：:]", "")
                .replaceAll("请问|请帮我|帮我|麻烦|查一个|查下|查询一个|查询|找一个|找找|推荐一个|推荐|看看|有没有|给我找|帮我找", "")
                .replaceAll("相关", "")
                .replaceAll("的课程*$", "")
                .replaceAll("课程.*$", "")
                .trim();
        return keyword.isEmpty() ? message.trim() : keyword;
    }

    private String formatCourseResult(String keyword, List<CourseVO> courses) {
        if (courses == null || courses.isEmpty()) {
            return "暂时没有找到和“" + keyword + "”相关的课程。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("我帮你找到了这些和“")
                .append(keyword)
                .append("”相关的课程：\n\n");
        for (int i = 0; i < courses.size(); i++) {
            CourseVO course = courses.get(i);
            builder.append("课程")
                    .append(i + 1)
                    .append("：")
                    .append(course.getName())
                    .append("\n课程ID：")
                    .append(course.getId())
                    .append("\n价格：")
                    .append(course.getPrice())
                    .append("元");
            if (i < courses.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }
}
