package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交换帖详情视图（PRD SWP-02/03：双方物品对照 + 请求列表 + 帖主操作）。
 * 权限口径：请求列表（requests）仅帖主可见；其他登录用户仅可见自己的请求（myRequest）。
 */
@Getter
@Setter
public class SwapPostDetailVO extends SwapPostCardVO {

    /** 请求列表（仅帖主本人返回） */
    private List<SwapRequestVO> requests;

    /** 当前登录用户自己的请求（非帖主视角） */
    private SwapRequestVO myRequest;

    /** 当前登录用户是否可发起交换（已认证、未受限、非本人、帖子 OPEN） */
    private Boolean canRequest;

    /** 当前登录用户是否可编辑/关闭（仅帖主且帖子 OPEN） */
    private Boolean canManage;

    /** 成交后生成的 SWAP 订单 ID（DEALT 时回填） */
    private Long orderId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
