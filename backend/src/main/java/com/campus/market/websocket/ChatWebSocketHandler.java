package com.campus.market.websocket;

import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.MessageSendDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 私信 WebSocket 处理器（PRD CHT-02/05）：
 * - 连接建立后按 userId 注册到 {@link WebSocketSessionRegistry}，作为在线推送目标；
 * - 接收端协议（JSON 文本帧）：
 *   {@code {"type":"CHAT_SEND","peerUserId":2,"msgType":"TEXT","content":"hi"}} 走与 REST 相同的
 *   {@link ChatService#sendMessage} 落库链路（敏感词校验/惰性建会话/事务维护派生字段），
 *   事务提交后由服务层向双方在线连接推送 CHAT_NEW——先落库再推送（T5）；
 *   {@code {"type":"PING"}} 心跳，回复 PONG。
 * - 推送端协议：CHAT_NEW（新消息，双方）、CHAT_READ（对端已读回执）、ERROR（业务错误码）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionRegistry.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = userIdOf(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "消息格式错误");
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "CHAT_SEND" -> handleChatSend(session, userId, node);
            case "PING" -> sessionRegistry.sendToUser(userId, Map.of("type", "PONG"));
            default -> sendError(session, "未知的消息类型");
        }
    }

    /** WS 端发送私信：与 REST POST /api/chats/messages 完全同一条落库+推送链路 */
    private void handleChatSend(WebSocketSession session, Long userId, JsonNode node) {
        MessageSendDTO dto = new MessageSendDTO();
        dto.setPeerUserId(node.path("peerUserId").asLong(0));
        dto.setMsgType(node.path("msgType").asText(""));
        dto.setContent(node.path("content").asText(""));
        try {
            chatService.sendMessage(userId, dto);
        } catch (BusinessException e) {
            // 敏感词命中（GOODS_SENSITIVE 40001）等业务错误原样回传给发送端
            sendError(session, e.getMessage(), e.getErrorCode().getCode());
        }
    }

    private void sendError(WebSocketSession session, String message) {
        sendError(session, message, null);
    }

    private void sendError(WebSocketSession session, String message, Integer code) {
        Long userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        sessionRegistry.sendToUser(userId, Map.of("type", "ERROR", "code", code == null ? 0 : code, "message", message));
    }

    private Long userIdOf(WebSocketSession session) {
        Object userId = session.getAttributes().get(WebSocketAttributes.USER_ID);
        return userId instanceof Long id ? id : null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // 连接已断开，忽略
        }
    }
}
