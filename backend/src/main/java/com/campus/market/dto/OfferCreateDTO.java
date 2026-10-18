package com.campus.market.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 求购应约请求体（PRD REQ-03：报价 + 留言）。
 */
@Getter
@Setter
public class OfferCreateDTO {

    /** 报价，≥0（不得为负，PRD §4.1） */
    @NotNull(message = "请填写报价")
    private BigDecimal price;

    /** 留言，≤200 字（敏感词校验） */
    @Size(max = 200, message = "留言不能超过 200 字")
    private String message;
}
