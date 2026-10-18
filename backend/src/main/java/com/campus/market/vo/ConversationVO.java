package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会话列表项响应体（PRD CHT-01，GET /api/chats/conversations）：
 * 含对方昵称/头像/最后消息/我的未读数，按 last_msg_at 倒序分页。
 */
@Getter
@Setter
public class ConversationVO {

    private Long id;

    /** 对方用户 ID（聊天窗顶栏与发消息目标） */
    private Long peerUserId;

    private String peerNickname;

    private String peerAvatar;

    /** 最近消息摘要（派生字段，T5） */
    private String lastMsg;

    private LocalDateTime lastMsgAt;

    /** 当前用户在该会话的未读数（派生字段，T5） */
    private Integer unreadCount;

    private LocalDateTime updatedAt;
}
