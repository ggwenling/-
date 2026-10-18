package com.campus.market.security;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;

/**
 * 请求级登录上下文（ThreadLocal），由 AuthInterceptor 写入、请求结束清理。
 * 切勿在异步线程/定时任务中读取（那不是用户请求线程）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 要求已登录并返回当前主体，未登录抛 40100 */
    public static LoginUser requireLogin() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
