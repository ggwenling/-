package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录/注册/刷新令牌响应（PRD USR-01/02）：双 token（access 2h + refresh 7d）。
 * 封禁用户允许登录拿到 token（仅能查看封禁通知，PRD §4.1），banned 相关字段供前端展示。
 */
@Getter
@Setter
public class LoginVO {

    private String token;

    private String refreshToken;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** 0=未认证 1=已认证（PRD §4.1） */
    private Integer authStatus;

    /** 是否处于封禁状态 */
    private Boolean banned;

    private String banReason;

    private String bannedUntil;
}
