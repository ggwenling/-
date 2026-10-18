package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper（PRD CHT-02~05 / 数据库设计文档 §3.19）。
 * 已读打点（read_at）与未读扣减同事务，走 MyBatis-Plus 通用更新即可，无需自定义 SQL。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
