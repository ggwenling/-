package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 交换请求（发起交换）请求体（PRD SWP-02：物品说明 + 可选关联自己在售商品）。
 */
@Getter
@Setter
public class SwapRequestCreateDTO {

    /** 发起方物品说明，≤500 字（敏感词校验） */
    @NotBlank(message = "请填写你的物品说明")
    @Size(max = 500, message = "物品说明不能超过 500 字")
    private String itemDesc;

    /** 关联的自己在售商品 ID（可选；须为本人 ON_SALE 商品） */
    private Long goodsId;
}
