package com.campus.market.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记仅限指定管理角色访问的后台接口（RBAC，PRD §4.2 权限矩阵），可标注在方法或类上。
 * 角色：super（超级管理员）/ auditor（内容审核员）/ operator（运营员）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的管理角色集合 */
    String[] value();
}
