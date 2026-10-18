package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户行为实体，对应表 user_behavior（PRD GDS-05 浏览埋点 / 数据库设计文档 §3.26，T9 修订）。
 * uk_behavior_view_day 保证 VIEW 行同人同商品同日仅一条（数据库唯一约束保证并发安全），
 * 冲突时静默忽略（不重复计浏览）；行为权重（5/3/1）在推荐计算（M5）时映射，不落库。
 */
@Getter
@Setter
@TableName("user_behavior")
public class UserBehavior {

    /** 行为类型：浏览 */
    public static final String BEHAVIOR_VIEW = "VIEW";
    /** 行为类型：收藏 */
    public static final String BEHAVIOR_FAVORITE = "FAVORITE";
    /** 行为类型：成交 */
    public static final String BEHAVIOR_ORDER_DONE = "ORDER_DONE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（FK user.id） */
    private Long userId;

    /** 商品 ID（FK goods.id） */
    private Long goodsId;

    /** 行为：VIEW / FAVORITE / ORDER_DONE */
    private String behavior;

    /** 业务日期（写入时取当天，东八区口径，T9） */
    private LocalDate behaviorDate;

    /** 生成列 view_day_key：VIEW 行=behavior_date，非 VIEW 行 NULL（STORED，T9），插入时不指定 */
    @TableField(exist = false)
    private LocalDate viewDayKey;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
