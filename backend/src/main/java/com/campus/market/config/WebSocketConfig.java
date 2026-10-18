package com.campus.market.config;

import com.campus.market.websocket.ChatWebSocketHandler;
import com.campus.market.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置（PRD CHT-02 / §6.6）：原生 WebSocket 端点 /ws，
 * 握手 URL 携带 token（/ws?token=xxx）由 {@link JwtHandshakeInterceptor} 校验（PRD §9.2）。
 * 演示环境放开跨域来源，与 WebMvcConfig 的 CORS 口径一致。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
