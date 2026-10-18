package com.campus.market.controller;

import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminReportService;
import com.campus.market.vo.ReportVO;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 举报工单处置（PRD ADM-04，SUPER+AUDITOR）：工单分页/详情/组合处置/驳回。
 */
@Tag(name = "管理后台-举报工单处置（SUPER/AUDITOR）")
@Validated
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@RequireRole({"super", "auditor"})
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(summary = "工单分页（status=0 待处理/1 已处置/2 已驳回；targetType=GOODS/WANT/SWAP/USER 可选；待处理优先）")
    @GetMapping
    public Result<PageResult<ReportVO>> page(
            @RequestParam(required = false) @Min(0) @Max(2) Integer status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(adminReportService.page(status, targetType, pageNum, pageSize));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<ReportVO> detail(@PathVariable Long id) {
        return Result.ok(adminReportService.detail(id));
    }

    @Operation(summary = "组合处置（actions=TAKE_DOWN/WARN/DEDUCT/BAN 任选多；result 必填）")
    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleBody body) {
        adminReportService.handle(id, body.getActions(), body.getResult(), requireAdmin());
        return Result.ok();
    }

    @Operation(summary = "驳回举报（result 必填，通知举报人未成立）")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody HandleBody body) {
        adminReportService.reject(id, body.getResult(), requireAdmin());
        return Result.ok();
    }

    private LoginUser requireAdmin() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.ADMIN) {
            throw new com.campus.market.common.exception.BusinessException(
                    com.campus.market.common.api.ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 处置请求体 */
    @Data
    public static class HandleBody {
        private List<String> actions;
        private String result;
    }
}
