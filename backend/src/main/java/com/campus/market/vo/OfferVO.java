package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 求购应约视图（PRD REQ-03：报价/留言/状态/操作）。
 * status 语义同 offer.status：0待处理 / 1已接受 / 2已拒绝 / 3已撤回。
 */
@Getter
@Setter
public class OfferVO {

    private Long id;

    private Long wantPostId;

    /** 求购帖标题（"我的应约"列表展示用） */
    private String wantPostTitle;

    /** 求购帖状态：OPEN / DEALT / CLOSED */
    private String wantPostStatus;

    /** 应约人 ID */
    private Long userId;

    private String nickname;

    private String avatar;

    /** 应约人信用分（前端 CreditTag 渲染） */
    private Integer creditScore;

    /** 应约人认证状态：1=已认证 */
    private Integer authStatus;

    /** 报价 */
    private java.math.BigDecimal price;

    /** 留言 */
    private String message;

    /** 0待处理 / 1已接受 / 2已拒绝 / 3已撤回 */
    private Integer status;

    /** 被接受后生成的订单 ID */
    private Long orderId;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime createdAt;
}
