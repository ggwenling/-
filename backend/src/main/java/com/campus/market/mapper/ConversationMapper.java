package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 聊天会话 Mapper（PRD CHT-01 / 数据库设计文档 §3.18）。
 * 派生字段（last_msg/unread1/unread2）一律通过原子 UPDATE 维护（T5：并发安全），
 * 未读扣减以"实际置为已读的行数"为步长，并发已读天然不重复扣减。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 发消息时同事务更新会话摘要与双方未读数（T5 原子更新）。
     *
     * @param inc1 接收方为 user1 时传 1，否则 0
     * @param inc2 接收方为 user2 时传 1，否则 0
     */
    @Update("UPDATE conversation SET last_msg = #{lastMsg}, last_msg_at = #{lastMsgAt}, "
            + "unread1 = unread1 + #{inc1}, unread2 = unread2 + #{inc2} WHERE id = #{id}")
    int updateSummaryAndUnread(@Param("id") Long id, @Param("lastMsg") String lastMsg,
                               @Param("lastMsgAt") LocalDateTime lastMsgAt,
                               @Param("inc1") int inc1, @Param("inc2") int inc2);

    /** user1 阅读后按实际已读行数扣减 unread1，GREATEST 防事实/计数短暂不一致时变负（T5） */
    @Update("UPDATE conversation SET unread1 = GREATEST(unread1 - #{count}, 0) WHERE id = #{id}")
    int decreaseUnread1(@Param("id") Long id, @Param("count") int count);

    /** user2 阅读后按实际已读行数扣减 unread2（T5） */
    @Update("UPDATE conversation SET unread2 = GREATEST(unread2 - #{count}, 0) WHERE id = #{id}")
    int decreaseUnread2(@Param("id") Long id, @Param("count") int count);

    /** 有未读消息的会话数（导航红点，CHT-05） */
    @Select("SELECT COUNT(*) FROM conversation WHERE (user1_id = #{userId} AND unread1 > 0) "
            + "OR (user2_id = #{userId} AND unread2 > 0)")
    long countConversationsWithUnread(@Param("userId") Long userId);

    /** 未读消息总数（导航红点徽标，CHT-05） */
    @Select("SELECT COALESCE(SUM(t.unread), 0) FROM ("
            + "SELECT unread1 AS unread FROM conversation WHERE user1_id = #{userId} "
            + "UNION ALL "
            + "SELECT unread2 AS unread FROM conversation WHERE user2_id = #{userId}) t")
    long sumUnreadMessages(@Param("userId") Long userId);

    /**
     * T5 定时兜底重建：由 message.read_at 事实全量重算派生字段
     * （unread1/2=对方发出且未读的消息数；last_msg/last_msg_at=最新一条消息）。
     */
    @Update("UPDATE conversation c SET "
            + "c.unread1 = (SELECT COUNT(*) FROM message m WHERE m.conversation_id = c.id AND m.sender_id = c.user2_id AND m.read_at IS NULL), "
            + "c.unread2 = (SELECT COUNT(*) FROM message m WHERE m.conversation_id = c.id AND m.sender_id = c.user1_id AND m.read_at IS NULL), "
            + "c.last_msg = (SELECT m.content FROM message m WHERE m.conversation_id = c.id ORDER BY m.created_at DESC, m.id DESC LIMIT 1), "
            + "c.last_msg_at = (SELECT m.created_at FROM message m WHERE m.conversation_id = c.id ORDER BY m.created_at DESC, m.id DESC LIMIT 1)")
    int rebuildDerivedFields();
}
