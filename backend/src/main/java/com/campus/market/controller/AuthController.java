package com.campus.market.controller;

import com.campus.market.common.api.Result;
import com.campus.market.common.util.IpUtils;
import com.campus.market.dto.LoginDTO;
import com.campus.market.dto.RefreshDTO;
import com.campus.market.dto.RegisterDTO;
import com.campus.market.dto.SendVerifyCodeDTO;
import com.campus.market.dto.VerifyCheckDTO;
import com.campus.market.security.annotation.RequireAuth;
import com.campus.market.security.annotation.CurrentUser;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AuthService;
import com.campus.market.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（PRD USR-01/02/03、§5.1）。注册/登录/刷新公开；验证码与认证需登录。
 */
@Tag(name = "认证-注册登录认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册并自动登录（PRD USR-01）")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @Operation(summary = "登录（失败 5 次锁 10 分钟 + 同 IP 限流 5 次/分钟，PRD USR-02/§7）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.login(dto, IpUtils.clientIp(request)));
    }

    @Operation(summary = "刷新令牌（refreshToken 换新双 token）")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.ok(authService.refresh(dto));
    }

    @Operation(summary = "登出（客户端丢弃 token）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(null);
        return Result.ok();
    }

    @Operation(summary = "发送校园认证验证码（演示模式打印后端控制台，PRD §5.1）")
    @RequireAuth
    @PostMapping("/verify-code/send")
    public Result<Void> sendVerifyCode(@Valid @RequestBody SendVerifyCodeDTO dto,
                                       @CurrentUser LoginUser user) {
        authService.sendVerifyCode(dto, user.getUserId());
        return Result.ok();
    }

    @Operation(summary = "提交验证码完成校园认证（PRD USR-03）")
    @RequireAuth
    @PostMapping("/verify-code/check")
    public Result<Void> certify(@Valid @RequestBody VerifyCheckDTO dto,
                                @CurrentUser LoginUser user) {
        authService.certify(dto, user.getUserId());
        return Result.ok();
    }
}
