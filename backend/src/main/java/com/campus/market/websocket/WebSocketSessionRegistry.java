package com.campus.market.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 在线会话注册表（PRD CHT-02）：userId → 在线连接集合（支持同账号多端同时在线）。
 * 推送统一走 {@link #sendToUser}，对 session 加锁串行发送（Tomcat 不允许并发写同一 session）；
 * 推送失败（对端刚断开）仅降级为离线——消息已落库，由对端上线后拉取（PRD CHT-05）。
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> onlineSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public WebSocketSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long userId, WebSocketSession session) {
        onlineSessions.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("WebSocket 上线: userId={}, sessionId={}, 当前连接数={}", userId, session.getId(), onlineSessions.get(userId).size());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            onlineSessions.remove(userId, sessions);
        }
        log.debug("WebSocket 下线: userId={}, sessionId={}", userId, session.getId());
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        return sessions != null && sessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /**
     * 向指定用户的所有在线连接推送 JSON 消息；无在线连接时静默返回 false（离线拉取兜底）。
     *
     * @param payload 可序列化对象（统一 {type, ...} 结构，时间格式与 REST 一致）
     */
    public boolean sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.warn("WebSocket 推送序列化失败: userId={}", userId, e);
            return false;
        }
        boolean delivered = false;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
                delivered = true;
            } catch (IOException e) {
                log.debug("WebSocket 推送失败（对端断开）: userId={}, sessionId={}", userId, session.getId());
            }
        }
        return delivered;
    }
}
