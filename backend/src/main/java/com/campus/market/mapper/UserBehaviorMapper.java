package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 用户行为 Mapper（PRD GDS-05 浏览埋点 / 数据库设计文档 §3.26）。
 * VIEW 同人同商品同日的去重由 uk_behavior_view_day 数据库唯一约束保证（T9）。
 */
@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    /** 近 N 天每日活跃用户数（活跃口径：当日有任意行为记录的 distinct user_id，M6 收尾 ADM-08） */
    @Select("SELECT behavior_date AS `date`, COUNT(DISTINCT user_id) AS activeUsers "
            + "FROM user_behavior WHERE behavior_date >= #{since} "
            + "GROUP BY behavior_date ORDER BY behavior_date")
    List<Map<String, Object>> selectDauSince(@Param("since") LocalDate since);
}
