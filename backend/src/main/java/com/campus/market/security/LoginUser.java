package com.campus.market.security;

import lombok.Getter;
import lombok.Setter;

/**
 * 当前登录主体（前台用户或后台管理员），由 JWT 解析而来
 */
@Getter
@Setter
public class LoginUser {

    public enum UserType { USER, ADMIN }

    private Long userId;
    private String username;
    private UserType userType;
    /** 管理员角色：super / auditor / operator，仅后台主体有值（PRD §4.2） */
    private String adminRole;
    /** token 类型：access / refresh，refresh 不能用于访问业务接口 */
    private String tokenType;
    /** 最新认证状态（0/1）：由 AuthInterceptor 每次请求从 DB 刷新，token 不携带（PRD §4.1） */
    private Integer authStatus;
    /** 最新信用分：同上，供受限策略判断（PRD §5.7） */
    private Integer creditScore;
    /**
     * 最新账号状态（0 正常 / 1 封禁）：由 AuthInterceptor 每次请求从 DB 刷新。
     * 封禁的强制拦截在拦截器按 URI 白名单执行（PRD §3.1：封禁用户仍可登录查看封禁原因与期限）。
     */
    private Integer status;
    /** 最新强制改密标记（ADMIN 主体，M6）：由 AuthInterceptor 每请求刷新，token 不携带 */
    private Integer mustChangePassword;
}
