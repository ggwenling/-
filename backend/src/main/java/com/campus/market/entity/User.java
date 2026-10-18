package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 前台用户实体，对应表 user（PRD §4.1 角色梯度 / 数据库设计文档 §3.1）。
 * credit_score 为当前值冗余，唯一事实来源是 credit_log（PRD §5.7 / T5）。
 * 受限用户：credit_score &lt; 60（app.credit.restricted-threshold）；封禁：status=1。
 */
@Getter
@Setter
@TableName("user")
public class User {

    /** 认证状态（PRD §4.1：未认证只能浏览） */
    public static final int AUTH_STATUS_UNVERIFIED = 0;
    public static final int AUTH_STATUS_VERIFIED = 1;

    /** 账号状态 */
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_BANNED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文（PRD §9.2） */
    private String password;

    private String nickname;

    private String avatar;

    private String college;

    /** 个人简介，入库前 XSS 转义（PRD §9.2） */
    private String bio;

    /** 信用分 0~150，&lt;60 受限 */
    private Integer creditScore;

    private Integer authStatus;

    private Integer status;

    private String banReason;

    /** 封禁截止；非空且到期后由 AuthInterceptor 自动解封 */
    private LocalDateTime bannedUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
