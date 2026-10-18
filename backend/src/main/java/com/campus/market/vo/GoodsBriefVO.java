package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 消息内嵌商品卡片快照（PRD CHT-03）：GOODS_CARD 消息渲染缩略图+标题+价格小卡所需的最小字段，
 * 由 GoodsMapper 只读投影而来，不依赖 M2 服务。
 */
@Getter
@Setter
public class GoodsBriefVO {

    private Long goodsId;

    private String title;

    private BigDecimal price;

    /** 封面缩略图 URL（sort 最小一张），商品无图时为 null */
    private String coverUrl;
}
