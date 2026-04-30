package com.tianji.chat.tools.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatToolExecutionResult {

    private String toolName;

    private String response;
}
