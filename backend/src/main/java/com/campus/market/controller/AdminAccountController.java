package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminService;
import com.campus.market.vo.AdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员账号管理（PRD ADM-01，SUPER 专属）：CRUD、重置密码、启停。
 */
@Tag(name = "管理后台-管理员账号（SUPER）")
@Validated
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@RequireRole("super")
public class AdminAccountController {

    private final AdminService adminService;

    @Operation(summary = "管理员分页")
    @GetMapping
    public Result<PageResult<AdminVO>> page(
            @RequestParam(required = false) Long adminId,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(adminService.page(adminId, pageNum, pageSize));
    }

    @Operation(summary = "新建管理员（初始密码 ≥8 位，mustChangePassword=1）")
    @PostMapping
    public Result<Long> create(@RequestBody AccountBody body) {
        return Result.ok(adminService.create(body.getUsername(), body.getPassword(),
                body.getRealName(), body.getRole(), requireAdmin()));
    }

    @Operation(summary = "编辑管理员（真实姓名/角色/启停）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AccountBody body) {
        adminService.update(id, body.getRealName(), body.getRole(), body.getStatus(), requireAdmin());
        return Result.ok();
    }

    @Operation(summary = "重置密码（重置后对方须重新登录并改密）")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody AccountBody body) {
        adminService.resetPassword(id, body.getPassword(), requireAdmin());
        return Result.ok();
    }

    private LoginUser requireAdmin() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 账号管理请求体（按接口取用字段） */
    @Data
    public static class AccountBody {
        private String username;
        private String password;
        private String realName;
        private String role;
        private Integer status;
    }
}
