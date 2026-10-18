package com.campus.market.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要登录（前台用户或后台管理员）的接口，可标注在方法或类上。
 * 与 AuthInterceptor 构成接口层校验，service 层业务校验为第二层（PRD §9.2）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {
}
