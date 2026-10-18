package com.campus.market.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.entity.CreditLog;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.RequireAuth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信用流水接口（PRD CRD-01 读取侧 / §5.7）：个人中心"信用记录"Tab。
 * 写入侧由 CreditService（M3）在订单完成/超时/处置等场景维护。
 */
@Tag(name = "用户-信用流水")
@Validated
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditLogMapper creditLogMapper;

    @Operation(summary = "我的信用分流水（时间倒序）")
    @RequireAuth
    @GetMapping("/my")
    public Result<PageResult<CreditLog>> my(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 50, message = "每页条数最大为 50") long pageSize) {
        LoginUser user = UserContext.requireLogin();
        Page<CreditLog> page = creditLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CreditLog>()
                        .eq(CreditLog::getUserId, user.getUserId())
                        .orderByDesc(CreditLog::getCreatedAt));
        return Result.ok(PageResult.of(page));
    }
}
