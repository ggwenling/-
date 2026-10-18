package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 收藏事实实体，对应表 favorite（PRD GDS-06 / 数据库设计文档 §3.17）。
 * uk_user_goods 保证一人一商品一条；收藏/取消与 goods.favorite_count ±1 同事务（T5）。
 */
@Getter
@Setter
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（FK user.id） */
    private Long userId;

    /** 商品 ID（FK goods.id） */
    private Long goodsId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
