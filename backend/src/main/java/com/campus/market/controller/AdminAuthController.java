package com.campus.market.controller;

import com.campus.market.common.api.Result;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminService;
import com.campus.market.vo.AdminLoginVO;
import com.campus.market.vo.AdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认证接口（PRD ADM-01 / T12）：登录、当前管理员、强制改密、登出。
 * mustChangePassword=1 的 ADMIN 由拦截器限制仅可访问本控制器（/api/admin/auth/**）。
 */
@Tag(name = "管理后台-认证")
@Validated
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminService adminService;

    @Operation(summary = "后台登录（用户名密码，停用账号拦截）")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody LoginBody body, HttpServletRequest request) {
        return Result.ok(adminService.login(body.getUsername(), body.getPassword(),
                com.campus.market.common.util.IpUtils.clientIp(request)));
    }

    @Operation(summary = "当前登录管理员")
    @RequireRole({"super", "auditor", "operator"})
    @GetMapping("/me")
    public Result<AdminVO> me() {
        return Result.ok(adminService.me(requireAdminId()));
    }

    @Operation(summary = "修改自己的密码（T12：强制改密标记清零）")
    @RequireRole({"super", "auditor", "operator"})
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordBody body) {
        adminService.changePassword(requireAdminId(), body.getOldPassword(), body.getNewPassword());
        return Result.ok();
    }

    @Operation(summary = "登出（无状态 JWT：客户端丢弃 token）")
    @RequireRole({"super", "auditor", "operator"})
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    private Long requireAdminId() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.ADMIN) {
            throw new com.campus.market.common.exception.BusinessException(
                    com.campus.market.common.api.ErrorCode.FORBIDDEN);
        }
        return user.getUserId();
    }

    /** 登录请求体 */
    @Data
    public static class LoginBody {
        @NotBlank(message = "用户名必填")
        private String username;
        @NotBlank(message = "密码必填")
        private String password;
    }

    /** 改密请求体 */
    @Data
    public static class ChangePasswordBody {
        @NotBlank(message = "原密码必填")
        private String oldPassword;
        @NotBlank(message = "新密码必填")
        private String newPassword;
    }
}
