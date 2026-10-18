package com.campus.market.controller;

import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminContentService;
import com.campus.market.vo.AdminContentVO;
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

/**
 * 内容巡查处置（PRD ADM-02，SUPER+AUDITOR）：三类内容统一分页 + 下架/恢复/软删。
 */
@Tag(name = "管理后台-内容巡查处置（SUPER/AUDITOR）")
@Validated
@RestController
@RequestMapping("/api/admin/contents")
@RequiredArgsConstructor
@RequireRole({"super", "auditor"})
public class AdminContentController {

    private final AdminContentService adminContentService;

    @Operation(summary = "巡查分页（targetType=GOODS|WANT|SWAP）")
    @GetMapping
    public Result<PageResult<AdminContentVO>> page(
            @RequestParam String targetType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(adminContentService.page(targetType, status, keyword, pageNum, pageSize));
    }

    @Operation(summary = "平台下架（reason 必填；goods 卖家不可自行恢复）")
    @PostMapping("/{targetType}/{id}/take-down")
    public Result<Void> takeDown(@PathVariable String targetType, @PathVariable Long id,
                                 @RequestBody ActionBody body) {
        adminContentService.takeDown(targetType, id, body.getReason(), requireAdmin());
        return Result.ok();
    }

    @Operation(summary = "恢复展示（下架/关闭内容回到在售/开放）")
    @PostMapping("/{targetType}/{id}/restore")
    public Result<Void> restore(@PathVariable String targetType, @PathVariable Long id) {
        adminContentService.restore(targetType, id, requireAdmin());
        return Result.ok();
    }

    @Operation(summary = "软删（审计四元组，reason 必填）")
    @PostMapping("/{targetType}/{id}/delete")
    public Result<Void> delete(@PathVariable String targetType, @PathVariable Long id,
                               @RequestBody ActionBody body) {
        adminContentService.deleteContent(targetType, id, body.getReason(), requireAdmin());
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

    /** 处置动作请求体 */
    @Data
    public static class ActionBody {
        private String reason;
    }
}
