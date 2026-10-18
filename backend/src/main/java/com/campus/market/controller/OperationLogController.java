package com.campus.market.controller;

import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.entity.OperationLog;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.OperationLogService;
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
 * 操作日志查询（PRD ADM-09 / §4.2：三角色只读、不可改删；记录由 AOP 与显式审计完成）。
 * 验收 P1：原仅 super，auditor/operator 被拒，与 §4.2 矩阵不符。
 */
@Tag(name = "管理后台-操作日志（三角色只读）")
@Validated
@RestController
@RequestMapping("/api/admin/operation-logs")
@RequiredArgsConstructor
@RequireRole({"super", "auditor", "operator"})
public class OperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志分页（adminId/action 可选过滤）")
    @GetMapping
    public Result<PageResult<OperationLog>> page(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(operationLogService.page(adminId, action, pageNum, pageSize));
    }
}
