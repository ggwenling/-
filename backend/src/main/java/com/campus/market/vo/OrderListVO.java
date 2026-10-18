package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单行卡视图（PRD ORD-05 订单列表）：商品缩略 + 对方 + 金额 + 状态 + 随状态/角色显隐的操作标记。
 * 动作标记（can*）由服务端按"当前查看者 + 订单状态 + 类型"计算，前端据此显隐按钮。
 */
@Getter
@Setter
public class OrderListVO {

    private Long id;

    /** 业务单号 SH+yyyyMMdd+N 位（T11） */
    private String orderNo;

    /** SALE / PURCHASE / SWAP */
    private String type;

    /** WAIT_CONFIRM / SCHEDULED / COMPLETED / CANCELLED */
    private String status;

    private Long goodsId;

    /** 商品标题（SOLD/软删商品仍可展示，满足"历史可追溯"） */
    private String goodsTitle;

    /** 商品封面缩略图 URL */
    private String goodsCoverUrl;

    /** 成交金额快照 */
    private BigDecimal amount;

    /** 展示角色买方 ID（T7） */
    private Long buyerId;

    /** 展示角色卖方 ID（T7） */
    private Long sellerId;

    /** 当前查看者角色：buyer / seller */
    private String viewRole;

    /** 对方用户 ID */
    private Long counterpartId;

    private String counterpartNickname;

    private String counterpartAvatar;

    /** 对方信用分（CreditTag 展示用） */
    private Integer counterpartCreditScore;

    /** 当前查看者是否已评价该订单（"去评价"按钮显隐） */
    private Boolean hasReviewedByMe;

    // ---------- 服务端计算的动作标记（前端设计文档 §6.1 订单列表） ----------

    /** 卖家确认出售（WAIT_CONFIRM 且我是卖家） */
    private Boolean canConfirm;

    /** 卖家拒绝（WAIT_CONFIRM 且我是卖家） */
    private Boolean canReject;

    /** 买家取消（WAIT_CONFIRM/SCHEDULED 且我是买家） */
    private Boolean canCancel;

    /** 确认完成（SALE/PURCHASE：SCHEDULED 且我是卖家；SWAP：SCHEDULED 且我方尚未确认） */
    private Boolean canComplete;

    /** 去评价（COMPLETED 且 7 天窗口内且我方未评） */
    private Boolean canReview;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelledAt;
}
