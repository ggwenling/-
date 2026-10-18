package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 求购帖发布/编辑请求体（PRD REQ-01 发布、REQ-02 编辑；编辑复用同一结构，路径携带帖子 ID）。
 * 分类层级、预算≥0、敏感词等业务约束在 WantPostService 校验。
 */
@Getter
@Setter
public class WantPostPublishDTO {

    /** 标题，≤50 字 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不能超过 50 字")
    private String title;

    /** 求购描述，≤500 字 */
    @NotBlank(message = "求购描述不能为空")
    @Size(max = 500, message = "描述不能超过 500 字")
    private String description;

    /** 期望二级分类 ID */
    @NotNull(message = "请选择期望分类")
    private Long categoryId;

    /** 心理价，≥0；null=价格面议（PRD §4.1：未知金额用 NULL） */
    private BigDecimal budget;
}
