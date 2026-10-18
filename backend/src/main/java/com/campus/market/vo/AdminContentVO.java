package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内容巡查列表 VO（PRD ADM-02）：三类内容（GOODS/WANT/SWAP）统一投影。
 */
@Getter
@Setter
public class AdminContentVO {

    public static final String TYPE_GOODS = "GOODS";
    public static final String TYPE_WANT = "WANT";
    public static final String TYPE_SWAP = "SWAP";
    /** 举报目标类型"用户"（PRD §3.21 report.target_type 第四类，仅用于举报处置链路） */
    public static final String TYPE_USER = "USER";

    /** GOODS / WANT / SWAP */
    private String targetType;

    private Long id;

    private String title;

    private Long ownerId;

    /** ON_SALE / OFF_SALE / IN_TRANSACTION / SOLD / DELETED / OPEN / DEALT / CLOSED */
    private String status;

    /** 商品价 / 求购预算 / 交换差价（可为空） */
    private BigDecimal amount;

    /** 平台下架理由（goods 专用） */
    private String offSaleReason;

    private LocalDateTime deletedAt;

    private String deleteReason;

    private LocalDateTime createdAt;
}
