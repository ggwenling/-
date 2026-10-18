package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 求购帖详情视图（PRD REQ-02/03/04：描述 + 应约列表 + 求购者操作）。
 * 权限口径：应约列表（offers）仅求购者本人可见；其他登录用户仅可见自己的应约（myOffer）。
 */
@Getter
@Setter
public class WantPostDetailVO extends WantPostCardVO {

    /** 应约列表（仅求购者本人返回，按待处理优先、时间倒序） */
    private List<OfferVO> offers;

    /** 当前登录用户自己的应约（非帖主视角） */
    private OfferVO myOffer;

    /** 当前登录用户是否已应约（待处理） */
    private Boolean hasPendingOffer;

    /** 当前登录用户是否可应约（已认证、未受限、非本人、帖子 OPEN） */
    private Boolean canOffer;

    /** 当前登录用户是否可编辑/关闭（仅帖主且帖子 OPEN） */
    private Boolean canManage;

    /** 成交后生成的订单 ID（DEALT 时回填，供前端"查看订单"跳转） */
    private Long orderId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
