package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 商品图片项请求体（PRD GDS-01）：前端先经 POST /api/uploads 上传取得 url/thumbUrl 后随表单提交。
 * 数组顺序即图片顺序，服务端按下标写入 sort（0~8）。
 */
@Getter
@Setter
public class GoodsImageDTO {

    /** 原图 URL（/upload/{yyyyMM}/xxx） */
    @NotBlank(message = "图片 URL 不能为空")
    private String url;

    /** 缩略图 URL（webp 原图与原图同 URL） */
    @NotBlank(message = "缩略图 URL 不能为空")
    private String thumbUrl;
}
