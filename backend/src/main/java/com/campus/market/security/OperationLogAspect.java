package com.campus.market.security;

import com.campus.market.common.util.IpUtils;
import com.campus.market.security.annotation.OperationLog;
import com.campus.market.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志切面（PRD ADM-09）：拦截标注 @OperationLog 的后台方法，
 * 方法成功返回后记录操作者（UserContext ADMIN 主体）、动作、目标与参数摘要。
 * 业务异常时不落成功日志（处置失败不产生审计噪音）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            record(joinPoint, operationLog);
        } catch (Exception e) {
            log.warn("操作日志切面记录失败：action={}, err={}", operationLog.action(), e.getMessage());
        }
        return result;
    }

    private void record(ProceedingJoinPoint joinPoint, OperationLog annotation) {
        LoginUser admin = UserContext.get();
        if (admin == null || admin.getUserType() != LoginUser.UserType.ADMIN) {
            return;   // 仅记录后台主体操作
        }
        String detail = buildDetail(joinPoint);
        operationLogService.record(admin.getUserId(), admin.getUsername(), annotation.action(),
                annotation.targetType(), resolveTargetId(joinPoint), detail, currentIp());
    }

    /** 参数摘要：文本参数拼接（截断），作为 detail 兜底 */
    private String buildDetail(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        return Arrays.stream(args)
                .filter(arg -> arg instanceof String || arg instanceof Number || arg instanceof Enum<?>)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    /** 目标 ID：方法签名中名为 id/targetId 的 Long 参数（约定优于配置） */
    private Long resolveTargetId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (names == null) {
            return null;
        }
        for (int i = 0; i < names.length; i++) {
            if (("id".equals(names[i]) || "targetId".equals(names[i]))
                    && args[i] instanceof Long value) {
                return value;
            }
        }
        return null;
    }

    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        return IpUtils.clientIp(request);
    }
}
