package com.tianji.chat.tools.runtime;

public interface ChatToolHandler {

    String toolName();

    boolean supports(String message);

    String execute(String message);
}
