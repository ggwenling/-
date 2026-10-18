package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.MessageSendDTO;
import com.campus.market.vo.ChatUnreadVO;
import com.campus.market.vo.ConversationVO;
import com.campus.market.vo.MessageVO;

/**
 * 私信服务（PRD CHT-01~06 / 数据库设计文档 §3.18、§3.19）：
 * 会话惰性创建（uk_pair，user1_id&lt;user2_id）、消息先落库再 WebSocket 推送（T5）、
 * read_at 已读打点与派生字段（last_msg/unread1/unread2）同事务原子维护。
 */
public interface ChatService {

    /** CHT-01：当前用户会话分页（含对方昵称/头像/最后消息/未读数），按 last_msg_at 倒序 */
    PageResult<ConversationVO> pageConversations(Long userId, long pageNum, long pageSize);

    /** CHT-02/03/06：发送私信（TEXT 过敏感词 DFA；GOODS_CARD 校验商品存在），落库+维护派生字段+在线推送 */
    MessageVO sendMessage(Long senderId, MessageSendDTO dto);

    /**
     * CHT-02/05：拉取会话消息（分页倒序）。
     * 拉取即已读：read_at IS NULL 且非本人发送的消息置为 now，并与未读数-1 同事务（CHT-05）。
     */
    PageResult<MessageVO> pageMessages(Long userId, Long conversationId, long pageNum, long pageSize);

    /** CHT-05：显式已读打点（打开聊天窗/收到新消息时调用），幂等 */
    void markRead(Long userId, Long conversationId);

    /** CHT-05：未读聚合（会话数+消息总数），导航红点数据源 */
    ChatUnreadVO countUnread(Long userId);

    /** T5：定时兜底重建会话派生字段（事实=message.read_at / message 表），每日执行 */
    int rebuildDerivedFields();
}
