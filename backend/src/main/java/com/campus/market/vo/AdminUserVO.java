package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 后台用户管理 VO（PRD ADM-05）。
 * 与实体 User 字段一致但**不含 password**（验收 P1：BCrypt 哈希不得随 JSON 泄露）。
 */
@Getter
@Setter
public class AdminUserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String college;

    private String bio;

    private Integer creditScore;

    private Integer authStatus;

    private Integer status;

    private String banReason;

    private LocalDateTime bannedUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
