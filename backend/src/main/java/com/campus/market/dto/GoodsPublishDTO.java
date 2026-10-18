package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品发布/编辑请求体（PRD GDS-01 发布、GDS-02 编辑；编辑复用同一结构，路径携带商品 ID）。
 * 字段级长度与数值约束在此声明，业务约束（分类层级、图片 1~9、标签 0~5、敏感词）在 GoodsService 校验。
 */
@Getter
@Setter
public class GoodsPublishDTO {

    /** 标题，≤50 字 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不能超过 50 字")
    private String title;

    /** 描述，≤500 字 */
    @NotBlank(message = "描述不能为空")
    @Size(max = 500, message = "描述不能超过 500 字")
    private String description;

    /** 二级分类 ID（parent_id != 0） */
    @NotNull(message = "请选择二级分类")
    private Long categoryId;

    /** 成色：1全新 / 2几乎全新 / 3轻微使用痕迹 / 4明显使用痕迹 */
    @NotNull(message = "请选择成色")
    private Integer conditionLevel;

    /** 面交价，≥0；0=免费赠送 */
    @NotNull(message = "请填写价格")
    private BigDecimal price;

    /** 常约交易地点，≤100 字 */
    @Size(max = 100, message = "交易地点不能超过 100 字")
    private String tradeLocation;

    /** 教材课程名（分类=教材书籍时填写，≤100 字，PRD GDS-07） */
    @Size(max = 100, message = "课程名不能超过 100 字")
    private String courseName;

    /** ISBN（分类=教材书籍时填写，≤20 字符） */
    @Size(max = 20, message = "ISBN 不能超过 20 字符")
    private String isbn;

    /** 标签 ID 集合，0~5 个 */
    private List<Long> tagIds;

    /** 图片列表（按顺序作为 sort 0~n），1~9 张 */
    private List<GoodsImageDTO> images;
}
