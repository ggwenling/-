package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 求购帖实体，对应表 want_post（PRD REQ-01~04 / 数据库设计文档 §3.10）。
 * 软删除走状态值 DELETED + deleted_* 审计四元组（T4），与 goods 同一套规则（§3.6 删除规则表）。
 */
@Getter
@Setter
@TableName("want_post")
public class WantPost {

    /** 状态机（PRD §5.4）：OPEN 求购中 → DEALT 已成交 / CLOSED 已关闭；DELETED 软删 */
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_DEALT = "DEALT";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_DELETED = "DELETED";

    /** 删除操作者类型（T4） */
    public static final String DELETED_BY_USER = "USER";
    public static final String DELETED_BY_ADMIN = "ADMIN";

    /** 求购者软删默认原因（T4） */
    public static final String USER_DELETE_REASON = "求购者自行删除";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 求购者 ID（FK user.id） */
    private Long userId;

    /** 期望分类 ID（FK category.id） */
    private Long categoryId;

    /** 标题，≤50 字 */
    private String title;

    /** 求购描述，≤500 字 */
    private String description;

    /** 心理价；NULL=价格面议（PRD §4.1：未知金额用 NULL，不用 0 伪装免费） */
    private BigDecimal budget;

    /** OPEN / DEALT / CLOSED / DELETED */
    private String status;

    /** 软删时间（T4） */
    private LocalDateTime deletedAt;

    private String deletedByType;

    private Long deletedBy;

    private String deleteReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
