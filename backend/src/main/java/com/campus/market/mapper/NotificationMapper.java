package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 站内通知 Mapper（PRD NTF-01/02）。查询/已读接口由 M4 实现，此处仅共享基础投影。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 未读数（导航红点，前端设计文档 §4.1） */
    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND is_read = 0")
    long countUnread(@Param("userId") Long userId);
}
