package com.campus.market.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数注解：把当前登录主体（{@link com.campus.market.security.LoginUser}）注入 controller 方法参数，
 * 配合 UserArgumentResolver 使用。示例：public Result&lt;Void&gt; me(@CurrentUser LoginUser user)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
