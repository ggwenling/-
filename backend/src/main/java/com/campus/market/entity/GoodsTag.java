package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品-标签关联实体，对应表 goods_tag（数据库设计文档 §3.8，T1 修订）。
 * 每商品 0~5 个标签，应用层校验；发布/编辑时事务内全量重建（先删后插）。
 */
@Getter
@Setter
@TableName("goods_tag")
public class GoodsTag {

    /** 每个商品最多打标数量（PRD GDS-01，数据库设计文档 §3.8） */
    public static final int MAX_TAG_COUNT = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID（FK goods.id） */
    private Long goodsId;

    /** 标签 ID（FK tag.id） */
    private Long tagId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
