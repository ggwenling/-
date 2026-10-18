package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 后台登录返回（PRD ADM-01/T12）：mustChangePassword=true 时前端强制进入改密页。
 */
@Getter
@Setter
public class AdminLoginVO {

    private String token;

    private String refreshToken;

    private Long adminId;

    private String username;

    private String realName;

    /** 小写口径：super/auditor/operator */
    private String role;

    /** T12：首次登录/被重置密码后为 true */
    private Boolean mustChangePassword;
}
