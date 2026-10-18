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
 * 求购应约实体，对应表 offer（PRD REQ-03 / 数据库设计文档 §3.12，T8 修订）。
 * uk_offer_pending(want_post_id, user_id, pending_key) 保证同一用户对同一求购帖最多一条待处理应约，
 * pending_key 为 STORED 生成列（status=0 时为 want_post_id，否则 NULL），插入时不指定。
 */
@Getter
@Setter
@TableName("offer")
public class Offer {

    /** 待处理（计入唯一约束） */
    public static final int STATUS_PENDING = 0;
    /** 已接受（生成 PURCHASE 订单） */
    public static final int STATUS_ACCEPTED = 1;
    /** 已拒绝（含被接受时应约自动关闭的其余待处理应约） */
    public static final int STATUS_REJECTED = 2;
    /** 卖方主动撤回 */
    public static final int STATUS_WITHDRAWN = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 求购帖 ID（FK want_post.id） */
    private Long wantPostId;

    /** 应约卖家 ID（FK user.id） */
    private Long userId;

    /** 报价，不得为负 */
    private BigDecimal price;

    /** 留言，≤200 字 */
    private String message;

    /** 0待处理 / 1已接受 / 2已拒绝 / 3已撤回 */
    private Integer status;

    /** 被接受后生成的订单 ID（FK order_info.id，可空） */
    private Long orderId;

    /** 生成列 pending_key（STORED，T8）：insert 时不可指定 */
    @TableField(exist = false)
    private Long pendingKey;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
