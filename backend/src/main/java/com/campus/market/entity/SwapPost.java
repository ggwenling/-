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
 * 交换帖实体，对应表 swap_post（PRD SWP-01~03 / 数据库设计文档 §3.13）。
 * 软删除同 §3.6 规则（T4）。
 */
@Getter
@Setter
@TableName("swap_post")
public class SwapPost {

    /** 状态机（PRD §5.5）：OPEN 交换中 → DEALT 已成交 / CLOSED 已关闭；DELETED 软删 */
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_DEALT = "DEALT";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_DELETED = "DELETED";

    public static final String DELETED_BY_USER = "USER";
    public static final String DELETED_BY_ADMIN = "ADMIN";

    public static final String USER_DELETE_REASON = "帖主自行删除";

    /** 是否接受补差价 */
    public static final int ALLOW_DIFF_NO = 0;
    public static final int ALLOW_DIFF_YES = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 帖主 ID（FK user.id） */
    private Long userId;

    /** 物品分类 ID（FK category.id） */
    private Long categoryId;

    /** 标题，≤50 字 */
    private String title;

    /** 我的物品描述，≤500 字 */
    private String myItemDesc;

    /** 想要的物品描述，≤500 字 */
    private String wantItemDesc;

    /** 是否接受补差价：0 否 / 1 是 */
    private Integer allowDiff;

    /** 期望差价金额（allow_diff=1 时必填且 ≥0，否则为 NULL） */
    private BigDecimal diffAmount;

    /** OPEN / DEALT / CLOSED / DELETED */
    private String status;

    private LocalDateTime deletedAt;

    private String deletedByType;

    private Long deletedBy;

    private String deleteReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
