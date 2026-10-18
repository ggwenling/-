package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 交换请求实体，对应表 swap_request（PRD SWP-02/03 / 数据库设计文档 §3.14，T8 修订）。
 * uk_swap_req_pending(swap_post_id, user_id, pending_key) 保证一人一帖最多一条待处理请求；
 * pending_key 为 STORED 生成列，insert 时不可指定。
 */
@Getter
@Setter
@TableName("swap_request")
public class SwapRequest {

    /** 待处理（计入唯一约束） */
    public static final int STATUS_PENDING = 0;
    /** 已同意（生成 SWAP 订单） */
    public static final int STATUS_ACCEPTED = 1;
    /** 已拒绝（含被同意时自动关闭的其余待处理请求） */
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交换帖 ID（FK swap_post.id） */
    private Long swapPostId;

    /** 发起方 ID（FK user.id） */
    private Long userId;

    /** 发起方物品说明，≤500 字 */
    private String itemDesc;

    /** 关联的发起方在售商品 ID（FK goods.id，可空） */
    private Long goodsId;

    /** 0待处理 / 1已同意 / 2已拒绝 */
    private Integer status;

    /** 被同意后生成的订单 ID（FK order_info.id，可空） */
    private Long orderId;

    /** 生成列 pending_key（STORED，T8） */
    @TableField(exist = false)
    private Long pendingKey;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
