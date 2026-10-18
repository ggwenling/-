package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 私信消息响应体（PRD CHT-02/03/05，GET /api/chats/conversations/{id}/messages 与 WebSocket 推送共用）。
 */
@Getter
@Setter
public class MessageVO {

    private Long id;

    private Long conversationId;

    private Long senderId;

    /** TEXT / GOODS_CARD / IMAGE（预留） */
    private String msgType;

    /** TEXT=文本 / GOODS_CARD=商品 ID 字符串 */
    private String content;

    /** 接收方阅读时间（T5），null=未读——发送方据此展示"已读/未读" */
    private LocalDateTime readAt;

    private LocalDateTime createdAt;

    /** msgType=GOODS_CARD 时的商品快照，其余为 null */
    private GoodsBriefVO goods;
}
