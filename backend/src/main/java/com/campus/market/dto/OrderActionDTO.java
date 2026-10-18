package com.campus.market.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单取消/拒绝请求体（PRD ORD-02/03）：理由可选，≤200 字。
 */
@Getter
@Setter
public class OrderActionDTO {

    /** 取消/拒绝理由（可选） */
    @Size(max = 200, message = "理由不能超过 200 字")
    private String reason;
}
