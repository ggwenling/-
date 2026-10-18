package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.UserEditDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.CurrentUser;
import com.campus.market.security.annotation.RequireAuth;
import com.campus.market.service.UserService;
import com.campus.market.vo.UserDetailVO;
import com.campus.market.vo.UserOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（PRD USR-04/05/06）。主页公开；本人资料读写需登录。
 */
@Tag(name = "用户-资料与主页")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "我的资料（PRD USR-04/05）")
    @RequireAuth
    @GetMapping("/me")
    public Result<UserDetailVO> myProfile(@CurrentUser LoginUser user) {
        return Result.ok(userService.getProfile(user.getUserId()));
    }

    @Operation(summary = "我的聚合概览（PRD USR-06）")
    @RequireAuth
    @GetMapping("/me/overview")
    public Result<UserOverviewVO> myOverview(@CurrentUser LoginUser user) {
        return Result.ok(userService.overview(user.getUserId()));
    }

    @Operation(summary = "编辑我的资料（PRD USR-05）")
    @RequireAuth
    @PutMapping("/me")
    public Result<Void> updateMe(@Valid @RequestBody UserEditDTO dto, @CurrentUser LoginUser user) {
        requireFrontUser(user);
        userService.updateMe(user.getUserId(), dto);
        return Result.ok();
    }

    @Operation(summary = "用户主页（公开，PRD USR-04）")
    @GetMapping("/{id}")
    public Result<UserDetailVO> profile(@PathVariable Long id) {
        return Result.ok(userService.getProfile(id));
    }

    /** 前台用户主体校验：管理员 token 不走前台用户接口 */
    private LoginUser requireFrontUser(LoginUser user) {
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
