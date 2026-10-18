package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交换帖卡片视图（PRD SWP-02 交换广场列表：我的物品 / 想要的物品 / 可补差价）。
 */
@Getter
@Setter
public class SwapPostCardVO {

    private Long id;

    private String title;

    private Long categoryId;

    private String categoryName;

    /** 一级分类（编辑表单回填 cascader 路径用） */
    private Long parentCategoryId;

    private String parentCategoryName;

    /** 帖主物品描述 */
    private String myItemDesc;

    /** 期望物品描述 */
    private String wantItemDesc;

    /** 是否接受补差价 */
    private Integer allowDiff;

    /** 期望差价金额（allowDiff=1 时非空） */
    private BigDecimal diffAmount;

    /** 帖主摘要（昵称/头像/信用分/认证状态） */
    private SellerVO publisher;

    /** OPEN / DEALT / CLOSED */
    private String status;

    /** 待处理请求数（广场卡片展示"已有 N 人想换"） */
    private Long pendingRequestCount;

    /** 当前登录用户在该帖的请求状态：null=未发起 / 0待处理 / 1已同意 / 2已拒绝 */
    private Integer myRequestStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
