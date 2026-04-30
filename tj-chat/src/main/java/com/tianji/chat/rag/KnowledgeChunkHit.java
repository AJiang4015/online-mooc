package com.tianji.chat.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkHit {

    private double score;
    private String title;
    private String content;
    private String rawText;

    public String displayTitle() {
        return StringUtils.hasText(title) ? title : "未命名知识片段";
    }

    public String displayContent() {
        if (StringUtils.hasText(content)) {
            return content;
        }
        return rawText == null ? "" : rawText;
    }
}
