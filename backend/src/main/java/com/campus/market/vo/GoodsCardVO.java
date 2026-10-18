package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品卡片视图（全站复用 GoodsCard 的数据结构，PRD GDS-03 列表 / GDS-05 详情相似推荐位）。
 */
@Getter
@Setter
public class GoodsCardVO {

    private Long id;

    private String title;

    /** 面交价，0=免费 */
    private BigDecimal price;

    /** 成色 1~4 */
    private Integer conditionLevel;

    /** 封面缩略图 URL（sort=0 的图片；webp 无缩略图时为原图） */
    private String coverUrl;

    /** 标签名列表 */
    private List<String> tags;

    /** 商品状态（列表恒为 ON_SALE；收藏列表可能为 OFF_SALE 等） */
    private String status;

    private Long viewCount;

    private Long wantCount;

    private Long favoriteCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
