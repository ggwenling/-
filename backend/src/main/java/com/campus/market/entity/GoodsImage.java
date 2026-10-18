package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品图片实体，对应表 goods_image（PRD GDS-01 / 数据库设计文档 §3.7）。
 * 每商品 1~9 张，应用层校验（GoodsService）。
 */
@Getter
@Setter
@TableName("goods_image")
public class GoodsImage {

    /** 图片顺序上限（0~8），与图片数量 1~9 张对应 */
    public static final int MAX_IMAGE_COUNT = 9;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID（FK goods.id） */
    private Long goodsId;

    /** 原图 URL */
    private String url;

    /** 缩略图 URL */
    private String thumbUrl;

    /** 顺序 0~8，sort=0 为封面 */
    private Integer sort;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
