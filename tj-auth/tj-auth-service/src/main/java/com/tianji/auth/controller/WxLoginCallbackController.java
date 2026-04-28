package com.tianji.auth.controller;

import com.tianji.auth.service.IAccountService;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WxLoginCallbackController {

    private final IAccountService accountService;

    @Value("${tj.auth.weixin.login-page}")
    private String loginPage;

    @NoWrapper
    @GetMapping(value = {"/auth/wxLogin", "/wxLogin"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> wxLogin(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        try {
            accountService.wxLogin(code, state);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildCallbackPage("微信登录成功", "扫码登录成功，正在返回登录页...", state, null, true));
        } catch (Exception e) {
            log.error("微信扫码登录回调失败，code={}, state={}", code, state, e);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildCallbackPage("微信登录失败", e.getMessage(), state, e.getMessage(), false));
        }
    }

    private String buildCallbackPage(String title, String message, String state, String errorMessage, boolean success) {
        String redirectUrl = buildRedirectUrl(state, errorMessage);
        String safeTitle = escapeHtml(title);
        String safeMessage = escapeHtml(message);
        String color = success ? "#07c160" : "#f56c6c";
        return "<!DOCTYPE html>"
                + "<html><head><meta charset=\"UTF-8\"><title>" + safeTitle + "</title>"
                + "<meta http-equiv=\"refresh\" content=\"1;url=" + escapeHtml(redirectUrl) + "\">"
                + "<style>body{font-family:Arial,sans-serif;background:#f5f7fa;margin:0;padding:32px;}"
                + ".card{max-width:480px;margin:40px auto;padding:32px;background:#fff;border-radius:16px;"
                + "box-shadow:0 12px 40px rgba(0,0,0,.08);text-align:center;}"
                + ".title{font-size:24px;font-weight:600;color:#1f2329;margin-bottom:12px;}"
                + ".message{font-size:14px;line-height:1.8;color:#606266;}"
                + ".badge{display:inline-block;margin-bottom:20px;padding:8px 16px;border-radius:999px;"
                + "background:" + color + "1A;color:" + color + ";font-weight:600;}"
                + ".link{display:inline-block;margin-top:20px;color:" + color + ";text-decoration:none;font-weight:600;}"
                + "</style><script>setTimeout(function(){window.location.replace('" + escapeJs(redirectUrl) + "');},300);</script></head>"
                + "<body><div class=\"card\"><div class=\"badge\">" + safeTitle + "</div>"
                + "<div class=\"title\">" + safeTitle + "</div>"
                + "<div class=\"message\">" + safeMessage + "</div>"
                + "<a class=\"link\" href=\"" + escapeHtml(redirectUrl) + "\">没有自动跳转？点这里继续</a>"
                + "</div></body></html>";
    }

    private String buildRedirectUrl(String state, String errorMessage) {
        StringBuilder builder = new StringBuilder(loginPage).append("?md=wechat");
        if (state != null && !state.isBlank()) {
            builder.append("&wxState=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            builder.append("&wxError=").append(URLEncoder.encode(errorMessage, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
