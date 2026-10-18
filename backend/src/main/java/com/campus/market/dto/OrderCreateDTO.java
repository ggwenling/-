package com.campus.market.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 下单请求体（PRD ORD-01）：买家对在售商品创建 SALE 订单。
 */
@Getter
@Setter
public class OrderCreateDTO {

    /** 要下单的商品 ID */
    @NotNull(message = "商品 ID 不能为空")
    private Long goodsId;
}
