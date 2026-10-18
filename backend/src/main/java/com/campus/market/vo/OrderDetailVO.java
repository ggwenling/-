package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订单详情视图（PRD ORD-05 详情）：在 {@link OrderListVO} 基础上补充完整时间线字段、
 * SWAP 双确认状态（T7）、取消原因与双方互评内容（前端 el-steps + 评价区）。
 */
@Getter
@Setter
public class OrderDetailVO extends OrderListVO {

    /** 卖家确认时间（进入待面交；SWAP 创建即 SCHEDULED，=创建时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedAt;

    /** 买方完成确认时间（T7，SWAP 双确认展示用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime buyerConfirmedAt;

    /** 卖方完成确认时间（T7） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sellerConfirmedAt;

    /** 取消方：BUYER / SELLER / TIMEOUT（可空） */
    private String cancelledBy;

    /** 取消理由 */
    private String cancelReason;

    /** 卖方摘要（买家卡展示） */
    private SellerVO seller;

    /** 买方摘要（SWAP 对等参与者展示，T7） */
    private SellerVO buyer;

    /** 本订单全部评价（通常 0~2 条；前端按 reviewerId 区分自己/对方，已评只读展示） */
    private java.util.List<ReviewVO> reviews;
}
