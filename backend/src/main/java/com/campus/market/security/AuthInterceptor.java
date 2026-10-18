package com.campus.market.security;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.User;
import com.campus.market.security.annotation.RequireAuth;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminService;
import com.campus.market.service.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * 鉴权拦截器（PRD §9.2 接口层校验）：
 * 1. 请求头携带合法 token 时解析出 LoginUser 放入 UserContext，供"登录可选"接口（如商品详情）读取；
 * 2. 命中 @RequireAuth 强制要求登录，且 refresh token 不能访问业务接口；
 * 3. 命中 @RequireRole 校验后台管理员角色（RBAC，M6 权限矩阵）；
 * 4. USER 主体每请求刷新封禁/认证/信用状态，并按白名单执行封禁拦截（PRD §3.1：封禁用户仍可
 *    登录查看封禁原因与期限，故个人资料、登出与导航未读数轮询接口放行，其余业务接口 40301）；
 *    ADMIN 主体刷新停用与强制改密标记（T12：mustChangePassword=1
 *    仅放行 /api/admin/auth/**，其余接口 40310）。
 * 未标注注解的接口不做强制校验，因此无需维护放行路径清单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    /** T12：强制改密期间 ADMIN 仅可访问的后台认证路径前缀 */
    private static final String ADMIN_AUTH_PATH_PREFIX = "/api/admin/auth/";
    /**
     * 封禁用户仍可访问的"自助"路径白名单（PRD §3.1：封禁用户可登录查看封禁原因和期限）：
     * 个人资料、登出/刷新令牌，以及导航红点轮询所需的两个未读数接口（否则前端每 30s 弹出错误提示）。
     * 白名单仅放行只读/自助语义，发布、互动、下单、发消息等一律仍被 40301 拦截。
     */
    private static final List<String> BANNED_USER_ALLOWED_PATHS = List.of(
            "/api/users/me",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/notifications/unread-count",
            "/api/chats/unread");

    private final JwtUtil jwtUtil;

    private final UserService userService;

    private final AdminService adminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        LoginUser user = resolveUser(request);
        // 前台用户主体：刷新封禁/认证/信用状态（封禁拦截 + 到期自动解封，PRD §4.1/§5.7）
        if (user != null && user.getUserType() == LoginUser.UserType.USER) {
            userService.applyFreshState(user);
            // 封禁拦截：白名单外的业务接口一律 40301（PRD §3.1）
            if (user.getStatus() != null && user.getStatus() == User.STATUS_BANNED
                    && !isBannedUserAllowed(request.getRequestURI())) {
                throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
            }
        }
        // 后台管理员主体：刷新停用状态与强制改密标记（PRD ADM-01/T12）
        if (user != null && user.getUserType() == LoginUser.UserType.ADMIN) {
            adminService.applyFreshState(user);
            if (user.getMustChangePassword() != null && user.getMustChangePassword() == 1
                    && !request.getRequestURI().startsWith(ADMIN_AUTH_PATH_PREFIX)) {
                throw new BusinessException(ErrorCode.ADM_MUST_CHANGE_PASSWORD);
            }
        }

        RequireAuth requireAuth = findAnnotation(handlerMethod, RequireAuth.class);
        if (requireAuth != null) {
            if (user == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            if (JwtUtil.TYPE_REFRESH.equals(user.getTokenType())) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
        }

        RequireRole requireRole = findAnnotation(handlerMethod, RequireRole.class);
        if (requireRole != null) {
            if (user == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            if (user.getUserType() != LoginUser.UserType.ADMIN
                    || !Arrays.asList(requireRole.value()).contains(user.getAdminRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        if (user != null) {
            UserContext.set(user);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 解析 Authorization: Bearer xxx；无 token 或解析失败均返回 null（是否拦截由注解决定） */
    private LoginUser resolveUser(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            return jwtUtil.parse(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 先查方法上的注解，再回退到类上 */
    private <A extends Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> type) {
        A annotation = handlerMethod.getMethodAnnotation(type);
        return annotation != null
                ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), type);
    }

    /** 封禁用户自助路径判定：前缀匹配，兼容带查询参数的 URI */
    private boolean isBannedUserAllowed(String requestUri) {
        return BANNED_USER_ALLOWED_PATHS.stream().anyMatch(requestUri::startsWith);
    }
}
