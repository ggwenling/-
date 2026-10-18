package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 聊天消息实体，对应表 message（PRD CHT-02~05 / 数据库设计文档 §3.19）。
 * 消息一律先落库再经 WebSocket 在线推送（PRD §6.4 / T5）；read_at 为接收方阅读时间，
 * 是未读数的唯一事实依据（T5）：对端阅读时写入 read_at，并与 conversation 未读数-1 同事务。
 */
@Getter
@Setter
@TableName("message")
public class Message {

    /** 文本消息（≤500 字，发送前过敏感词 DFA，PRD CHT-02/06） */
    public static final String TYPE_TEXT = "TEXT";

    /** 商品卡片消息，content=商品 ID（PRD CHT-03） */
    public static final String TYPE_GOODS_CARD = "GOODS_CARD";

    /** 图片消息：PRD CHT-04 为 P2 本期跳过，仅预留枚举值 */
    public static final String TYPE_IMAGE = "IMAGE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID（FK conversation.id） */
    private Long conversationId;

    /** 发送者 ID（FK user.id） */
    private Long senderId;

    /** TEXT / GOODS_CARD / IMAGE（预留） */
    private String msgType;

    /** TEXT=文本 / GOODS_CARD=商品ID / IMAGE=图片URL（预留） */
    private String content;

    /** 接收方阅读时间——未读数唯一事实依据（T5），NULL=未读 */
    private LocalDateTime readAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
