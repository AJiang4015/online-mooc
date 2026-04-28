package com.tianji.auth.service.impl;

import com.alibaba.fastjson.JSON;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.api.dto.user.WxLoginRegisterDTO;
import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.auth.service.IAccountService;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.util.JwtTool;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.WebUtils;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountService {

    private static final String WX_QRCODE_KEY_PREFIX = "wx_qrcode:";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";

    private final JwtTool jwtTool;
    private final UserClient userClient;
    private final ILoginRecordService loginRecordService;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${tj.auth.weixin.appid}")
    private String appid;
    @Value("${tj.auth.weixin.secret}")
    private String secret;
    @Value("${tj.auth.weixin.expire}")
    private Long qrcodeExpire;

    @Override
    public String login(LoginFormDTO loginDTO, boolean isStaff) {
        LoginUserDTO detail = userClient.queryUserDetail(loginDTO, isStaff);
        if (detail == null) {
            throw new BadRequestException("登录信息有误");
        }
        detail.setRememberMe(loginDTO.getRememberMe());
        String token = generateToken(detail);
        loginRecordService.loginSuccess(loginDTO.getCellPhone(), detail.getUserId());
        return token;
    }

    @Override
    public void saveUuid(String uuid) {
        if (StringUtils.isBlank(uuid)) {
            throw new BadRequestException("UUID不能为空");
        }
        redisTemplate.opsForValue().set(wxQrcodeKey(uuid), STATUS_PENDING, qrcodeExpire, TimeUnit.SECONDS);
    }

    @Override
    public Map<String, Object> checkWxLoginStatus(String uuid) {
        String cacheValue = redisTemplate.opsForValue().get(wxQrcodeKey(uuid));
        if (cacheValue == null) {
            Map<String, Object> result = new HashMap<>(2);
            result.put("status", "expired");
            return result;
        }
        return parseWxLoginStatus(cacheValue);
    }

    @Override
    public String wxLogin(String code, String state) {
        if (StringUtils.isBlank(state)) {
            throw new BadRequestException("微信登录缺少state参数");
        }
        String cacheKey = wxQrcodeKey(state);
        String storedState = redisTemplate.opsForValue().get(cacheKey);
        if (storedState == null) {
            throw new BadRequestException("二维码已过期或UUID无效");
        }
        try {
            Map<String, String> tokenInfo = getAccessToken(code);
            assertWxApiSuccess(tokenInfo, "获取微信access_token失败");

            String accessToken = tokenInfo.get("access_token");
            String openid = tokenInfo.get("openid");
            Map<String, String> userInfo = getUserInfo(accessToken, openid);
            assertWxApiSuccess(userInfo, "获取微信用户信息失败");

            String unionid = userInfo.get("unionid");
            if (StringUtils.isBlank(unionid)) {
                throw new BadRequestException("该微信未绑定用户，请先绑定账号");
            }

            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPassword(unionid);
            loginForm.setType(3);

            LoginUserDTO loginUser = loadOrCreateWxUser(loginForm, unionid, userInfo);
            if (loginUser == null) {
                throw new BadRequestException("该微信未绑定用户，请先绑定账号");
            }

            String token = generateToken(loginUser);
            loginRecordService.loginSuccess(null, loginUser.getUserId());
            saveWxLoginResult(state, STATUS_SUCCESS, token, "登录成功");
            return token;
        } catch (RuntimeException e) {
            saveWxLoginResult(state, STATUS_ERROR, null, e.getMessage());
            throw e;
        }
    }

    private String generateToken(LoginUserDTO detail) {
        String token = jwtTool.createToken(detail);
        String refreshToken = jwtTool.createRefreshToken(detail);
        int maxAge = BooleanUtils.isTrue(detail.getRememberMe()) ?
                (int) JwtConstants.JWT_REMEMBER_ME_TTL.toSeconds() : -1;
        WebUtils.cookieBuilder()
                .name(detail.getRoleId() == 2 ? JwtConstants.REFRESH_HEADER : JwtConstants.ADMIN_REFRESH_HEADER)
                .value(refreshToken)
                .maxAge(maxAge)
                .httpOnly(true)
                .build();
        return token;
    }

    @Override
    public void logout() {
        jwtTool.cleanJtiCache();
        WebUtils.cookieBuilder()
                .name(JwtConstants.REFRESH_HEADER)
                .value("")
                .maxAge(0)
                .httpOnly(true)
                .build();
    }

    @Override
    public String refreshToken(String refreshToken) {
        LoginUserDTO userDTO = jwtTool.parseRefreshToken(refreshToken);
        return generateToken(userDTO);
    }

    private Map<String, String> getAccessToken(String code) {
        String urlTemplate = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
        String url = String.format(urlTemplate, appid, secret, code);
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, null, String.class);
        return JSON.parseObject(exchange.getBody(), Map.class);
    }

    private Map<String, String> getUserInfo(String accessToken, String openid) {
        String urlTemplate = "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s";
        String url = String.format(urlTemplate, accessToken, openid);
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        String body = exchange.getBody();
        String result = body == null ? "" : new String(body.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return JSON.parseObject(result, Map.class);
    }

    private void assertWxApiSuccess(Map<String, String> response, String message) {
        if (response == null || response.isEmpty()) {
            throw new BadRequestException(message);
        }
        String errCode = response.get("errcode");
        if (StringUtils.isNotBlank(errCode) && !"0".equals(errCode)) {
            String errMsg = response.getOrDefault("errmsg", message);
            throw new BadRequestException(errMsg);
        }
    }

    private LoginUserDTO loadOrCreateWxUser(LoginFormDTO loginForm, String unionid, Map<String, String> userInfo) {
        try {
            return userClient.queryUserDetail(loginForm, false);
        } catch (FeignException.BadRequest e) {
            String content = e.contentUTF8();
            if (!content.contains("微信unionid不存在")) {
                throw e;
            }
            WxLoginRegisterDTO registerDTO = new WxLoginRegisterDTO();
            registerDTO.setUnionid(unionid);
            registerDTO.setNickname(userInfo.get("nickname"));
            registerDTO.setIcon(userInfo.get("headimgurl"));
            return userClient.registerWxUser(registerDTO);
        }
    }

    private void saveWxLoginResult(String uuid, String status, String token, String message) {
        Map<String, Object> result = new HashMap<>(4);
        result.put("status", status);
        if (StringUtils.isNotBlank(token)) {
            result.put("token", token);
        }
        if (StringUtils.isNotBlank(message)) {
            result.put("msg", message);
        }
        redisTemplate.opsForValue().set(wxQrcodeKey(uuid), JSON.toJSONString(result), qrcodeExpire, TimeUnit.SECONDS);
    }

    private Map<String, Object> parseWxLoginStatus(String cacheValue) {
        if (!cacheValue.startsWith("{")) {
            Map<String, Object> result = new HashMap<>(2);
            result.put("status", cacheValue);
            return result;
        }
        return JSON.parseObject(cacheValue, Map.class);
    }

    private String wxQrcodeKey(String uuid) {
        return WX_QRCODE_KEY_PREFIX + uuid;
    }
}
