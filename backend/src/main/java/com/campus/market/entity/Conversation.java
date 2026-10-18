package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 聊天会话实体，对应表 conversation（PRD CHT-01 / 数据库设计文档 §3.18）。
 * 一对一会话（PRD §6.4 不支持群聊）：uk_pair(user1_id,user2_id) 唯一，约定 user1_id &lt; user2_id。
 * last_msg / last_msg_at / unread1 / unread2 为派生字段（T5）：唯一事实依据是 message.read_at 与
 * message 表本身，写操作与消息落库同事务原子维护，每日定时重算兜底（{@code ChatServiceImpl#rebuildDerivedFields}）。
 */
@Getter
@Setter
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话一方，约定小于 user2Id（FK user.id） */
    private Long user1Id;

    /** 会话另一方（FK user.id） */
    private Long user2Id;

    /** 最近消息摘要（派生-T5）：TEXT=文本内容，GOODS_CARD="[商品] 标题" */
    private String lastMsg;

    /** 最近消息时间（派生-T5），会话列表按其倒序 */
    private LocalDateTime lastMsgAt;

    /** user1 未读数（派生-T5）= user2 发出且 read_at IS NULL 的消息数 */
    private Integer unread1;

    /** user2 未读数（派生-T5）= user1 发出且 read_at IS NULL 的消息数 */
    private Integer unread2;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
