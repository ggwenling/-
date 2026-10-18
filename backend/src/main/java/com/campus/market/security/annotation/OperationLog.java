package com.campus.market.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（PRD ADM-09）：标注在后台 mutating 方法上，由 {@link com.campus.market.security.OperationLogAspect}
 * 自动记录操作者（UserContext 的 ADMIN 主体）、动作、目标对象与理由摘要，替代每处手工调用。
 * 目标对象参数按名匹配：方法含 <code>Long id</code>/<code>targetId</code> 参数则记 target_id；
 * 含 <code>reason</code>/<code>dto.reason</code> 则记入 detail。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 动作常量（OperationLog.ACTION_*） */
    String action();

    /** 操作对象类型（多态逻辑外键：GOODS/USER/ADMIN/WORD/REPORT/BANNER/NOTICE…） */
    String targetType() default "";
}
