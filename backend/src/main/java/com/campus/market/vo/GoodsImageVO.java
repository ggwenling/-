package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 商品图片视图（PRD GDS-05 详情图集）
 */
@Getter
@Setter
public class GoodsImageVO {

    /** 原图 URL */
    private String url;

    /** 缩略图 URL */
    private String thumbUrl;

    /** 顺序 0~8，0 为封面 */
    private Integer sort;
}
