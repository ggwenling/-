package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 我的收藏列表项视图（PRD GDS-06：GET /api/favorites/my）。
 */
@Getter
@Setter
public class FavoriteVO {

    /** 收藏记录 ID */
    private Long id;

    /** 商品 ID */
    private Long goodsId;

    /** 商品卡片信息（含最新状态，便于灰态展示已下架/已删除商品） */
    private GoodsCardVO goods;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
