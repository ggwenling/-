package com.campus.market.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 交易评价请求体（PRD ORD-06）：1~5 星 + 可选内容 ≤200 字（敏感词校验在 service 层）。
 */
@Getter
@Setter
public class ReviewCreateDTO {

    /** 评分 1~5 星 */
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    @NotNull(message = "评分不能为空")
    private Integer score;

    /** 评价内容，≤200 字，可选 */
    @Size(max = 200, message = "评价内容不能超过 200 字")
    private String content;
}
