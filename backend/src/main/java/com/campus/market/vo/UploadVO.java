package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 图片上传结果视图（PRD §9.1 / GDS-01）：原图与缩略图 URL，前端随商品表单回传。
 */
@Getter
@Setter
public class UploadVO {

    /** 原图 URL：/upload/{yyyyMM}/{uuid}.{ext} */
    private String url;

    /** 缩略图 URL（webp 不生成缩略图，返回原图 URL） */
    private String thumbUrl;
}
