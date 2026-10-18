package com.campus.market.websocket;

import com.campus.market.security.JwtUtil;
import com.campus.market.security.LoginUser;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器（PRD CHT-02 / §9.2）：
 * token 经握手 URL query 传递（/ws?token=xxx），也可回退 Authorization 头；
 * 仅前台用户 access token 可建立连接，解析失败返回 401 拒绝握手。
 * 握手通过后 userId 写入 session attributes，供 {@link ChatWebSocketHandler} 识别推送主体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            token = servletRequest.getServletRequest().getParameter("token");
            if (token == null || token.isBlank()) {
                String authorization = servletRequest.getServletRequest().getHeader("Authorization");
                if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                    token = authorization.substring(BEARER_PREFIX.length());
                }
            }
        }
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            LoginUser user = jwtUtil.parse(token);
            if (user.getUserType() != LoginUser.UserType.USER
                    || !JwtUtil.TYPE_ACCESS.equals(user.getTokenType())) {
                log.debug("WebSocket 握手拒绝：非前台用户 access token, username={}", user.getUsername());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(WebSocketAttributes.USER_ID, user.getUserId());
            attributes.put(WebSocketAttributes.USERNAME, user.getUsername());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("WebSocket 握手 token 无效: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
