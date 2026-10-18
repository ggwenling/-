package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 我的聚合概览（PRD USR-06）：订单维度计数归 M3 后补充。
 */
@Getter
@Setter
public class UserOverviewVO {

    private Long onSaleCount;

    private Long soldCount;

    private Long favoriteCount;
}
