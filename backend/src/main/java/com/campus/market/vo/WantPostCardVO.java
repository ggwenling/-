package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 求购帖卡片视图（PRD REQ-02 求购广场列表）。
 * budget 为 null 表示价格面议（PRD §4.1，不用 0 伪装免费）。
 */
@Getter
@Setter
public class WantPostCardVO {

    private Long id;

    private String title;

    private String description;

    /** 心理价；NULL=面议 */
    private BigDecimal budget;

    private Long categoryId;

    private String categoryName;

    /** 一级分类（编辑表单回填 cascader 路径用） */
    private Long parentCategoryId;

    private String parentCategoryName;

    /** 发布者摘要（复用商品卖家卡片结构：昵称/头像/信用分/认证状态） */
    private SellerVO publisher;

    /** OPEN / DEALT / CLOSED */
    private String status;

    /** 待处理应约数（卡片展示口径，与交换侧 pendingRequestCount 统一，验收 P3） */
    private Long offerCount;

    /** 当前登录用户在该帖的应约状态：null=未应约 / 0待处理 / 1已接受 / 2已拒绝 / 3已撤回 */
    private Integer myOfferStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
