package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 交易评价视图（PRD ORD-06 / CRD-03）：评价人摘要 + 评分内容，个人主页评价 Tab 与订单详情评价区共用。
 */
@Getter
@Setter
public class ReviewVO {

    private Long id;

    private Long orderId;

    /** 评价人 ID */
    private Long reviewerId;

    private String reviewerNickname;

    private String reviewerAvatar;

    /** 被评价人 ID（CRD-03 按 revieweeId 聚合） */
    private Long revieweeId;

    /** 1~5 星 */
    private Integer score;

    /** 评价内容 */
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
