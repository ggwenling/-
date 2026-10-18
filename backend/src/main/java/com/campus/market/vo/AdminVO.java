package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理员信息 VO（PRD ADM-01）：角色小写口径，密码永不出参。
 */
@Getter
@Setter
public class AdminVO {

    private Long id;

    private String username;

    private String realName;

    /** super / auditor / operator */
    private String role;

    /** 0 启用 / 1 停用 */
    private Integer status;

    private Integer mustChangePassword;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
