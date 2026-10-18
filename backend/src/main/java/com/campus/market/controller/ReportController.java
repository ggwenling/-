package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.ReportCreateDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.OrphanScanService;
import com.campus.market.service.ReportService;
import com.campus.market.vo.OrphanScanVO;
import com.campus.market.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 举报接口（PRD RPT-01 提交举报 / RPT-02 我的举报进度）。
 * 均需登录且为前台用户主体（写法同 WantPostController.requireFrontUser），
 * 但<b>不要求校园认证、不检查信用受限</b>（PRD §3.1 未将举报列入受限禁止项；游客不可提交）。
 * 另附带 dev 手动触发孤儿扫描入口（数据库设计文档 T3 兜底）。
 */
@Tag(name = "举报")
@Validated
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final OrphanScanService orphanScanService;
    private final Environment environment;

    @Operation(summary = "提交举报（RPT-01，需登录；多态目标 GOODS/WANT/SWAP/USER）")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReportCreateDTO dto) {
        return Result.ok(reportService.create(dto, requireFrontUser()));
    }

    @Operation(summary = "我的举报进度分页（RPT-02，需登录；status 可选 0/1/2）")
    @GetMapping("/my")
    public Result<PageResult<ReportVO>> myReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        return Result.ok(reportService.pageMyReports(requireFrontUser().getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "【dev】手动触发孤儿数据扫描（T3 验证手段，仅 dev profile 可用）")
    @PostMapping("/orphan-scan/trigger")
    public Result<OrphanScanVO> triggerOrphanScan() {
        requireFrontUser();
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅开发环境可手动触发孤儿扫描");
        }
        return Result.ok(orphanScanService.scan());
    }

    /** 举报操作仅限前台用户主体（admin 与 user 主键空间独立，防止越权） */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
