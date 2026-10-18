package com.campus.market.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 收藏请求体（PRD GDS-06）：重复收藏幂等，不重复计数。
 */
@Getter
@Setter
public class FavoriteAddDTO {

    /** 要收藏的商品 ID */
    @NotNull(message = "商品 ID 不能为空")
    private Long goodsId;
}
