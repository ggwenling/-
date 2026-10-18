package com.campus.market.websocket;

/**
 * WebSocket session attributes 键名常量（握手拦截器写入、处理器读取）。
 */
public final class WebSocketAttributes {

    /** 登录用户 ID（握手时由 JWT 解析，后续推送以此为准，不信任客户端报文） */
    public static final String USER_ID = "wsUserId";

    /** 登录用户名（日志用） */
    public static final String USERNAME = "wsUsername";

    private WebSocketAttributes() {
    }
}
