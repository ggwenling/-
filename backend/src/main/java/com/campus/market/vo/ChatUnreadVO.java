package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 未读聚合响应体（PRD CHT-05，GET /api/chats/unread）：导航红点数据源，
 * stores/msg.js 轮询刷新后接入 MainLayout 徽标。
 */
@Getter
@Setter
public class ChatUnreadVO {

    /** 有未读消息的会话数 */
    private long conversationCount;

    /** 未读消息总数（红点徽标展示值） */
    private long messageCount;
}
