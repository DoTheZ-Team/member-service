package com.justdo.plug.member.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdo.plug.member.global.exception.ApiException;
import com.justdo.plug.member.global.response.ApiResponse;
import com.justdo.plug.member.global.response.code.status.ErrorStatus;
import com.justdo.plug.member.global.utils.jwt.JwtTokenProvider;
import com.justdo.plug.member.global.utils.jwt.RedisUtils;
import io.lettuce.core.RedisConnectionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import static com.justdo.plug.member.global.utils.jwt.JwtTokenProvider.REFRESH_TOKEN_EXPIRATION_TIME;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisUtils redisUtils;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        DefaultOAuth2User defaultOAuth2User = (DefaultOAuth2User) authentication.getPrincipal();

        Long userId = (Long) defaultOAuth2User.getAttributes().get("id");

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);

        storeRefreshTokenInRedis(userId,refreshToken);

        ApiResponse<Map<String, String>> apiResponse = ApiResponse.onSuccess(tokenMap);

        // JSON 응답 생성
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = new ObjectMapper().writeValueAsString(apiResponse);

        try (PrintWriter writer = response.getWriter()) {
            writer.println(json);
        }

    }

    private void storeRefreshTokenInRedis(Long userId, String refreshToken) {
        try {
            redisUtils.setData(userId.toString(),refreshToken,REFRESH_TOKEN_EXPIRATION_TIME);
        } catch (RedisConnectionException | DataAccessException e) {
            throw new ApiException(ErrorStatus._REDIS_OPERATION_ERROR);
        }
    }
}
