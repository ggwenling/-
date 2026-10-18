package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 交换请求视图（PRD SWP-03：发起方物品说明 + 关联在售商品 + 状态/操作）。
 * status 语义同 swap_request.status：0待处理 / 1已同意 / 2已拒绝。
 */
@Getter
@Setter
public class SwapRequestVO {

    private Long id;

    private Long swapPostId;

    /** 发起方 ID */
    private Long userId;

    private String nickname;

    private String avatar;

    private Integer creditScore;

    private Integer authStatus;

    /** 发起方物品说明 */
    private String itemDesc;

    /** 关联的发起方在售商品（可空） */
    private Long goodsId;

    private String goodsTitle;

    private String goodsCoverUrl;

    private BigDecimal goodsPrice;

    /** 0待处理 / 1已同意 / 2已拒绝 */
    private Integer status;

    /** 被同意后生成的订单 ID */
    private Long orderId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
