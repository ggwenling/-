package com.campus.market.common;

import com.campus.market.security.LoginUser;

/**
 * 测试工厂：构造各业务场景的 LoginUser 主体。
 * 口径与生产一致：authStatus 0/1、creditScore 由 AuthInterceptor 每请求刷新（service 层测试直接构造字段值）。
 */
public final class LoginUserTestFactory {

    private LoginUserTestFactory() {
    }

    /** 已认证、信用正常（100）的前台用户 */
    public static LoginUser user(long id) {
        return user(id, 1, 100);
    }

    /** 指定认证状态与信用分的前台用户 */
    public static LoginUser user(long id, int authStatus, int creditScore) {
        LoginUser u = new LoginUser();
        u.setUserId(id);
        u.setUsername("testuser" + id);
        u.setUserType(LoginUser.UserType.USER);
        u.setTokenType("access");
        u.setAuthStatus(authStatus);
        u.setCreditScore(creditScore);
        return u;
    }

    /** 未认证用户（触发 40304 校园认证拦截） */
    public static LoginUser uncertified(long id) {
        return user(id, 0, 100);
    }

    /** 信用受限用户（< restrictedThreshold=60，触发 40302） */
    public static LoginUser restricted(long id) {
        return user(id, 1, 50);
    }

    /** 后台管理员（角色小写口径：super/auditor/operator） */
    public static LoginUser admin(String role) {
        LoginUser u = new LoginUser();
        u.setUserId(900L + role.hashCode());
        u.setUsername("testadmin");
        u.setUserType(LoginUser.UserType.ADMIN);
        u.setAdminRole(role);
        u.setTokenType("access");
        return u;
    }
}
