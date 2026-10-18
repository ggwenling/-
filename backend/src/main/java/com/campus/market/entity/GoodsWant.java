package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * "想要"事实实体，对应表 goods_want（数据库设计文档 §3.9，T5 修订）。
 * goods.want_count 的唯一事实来源；由 M3 下单流程写入，M2 仅展示 want_count，不写本表。
 */
@Getter
@Setter
@TableName("goods_want")
public class GoodsWant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 想要的用户 ID（FK user.id） */
    private Long userId;

    /** 商品 ID（FK goods.id） */
    private Long goodsId;

    /** 首次产生的订单 ID（可空，FK order_info.id，M3 回填） */
    private Long orderId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
