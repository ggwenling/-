package com.campus.market.controller;

import com.campus.market.common.api.Result;
import com.campus.market.entity.Banner;
import com.campus.market.entity.Notice;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.security.annotation.OperationLog;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminOpsService;
import com.campus.market.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 轮播/公告管理（PRD ADM-07，SUPER+OPERATOR）与数据大屏（ADM-08，查看三角色）。
 */
@Tag(name = "管理后台-轮播/公告/数据大屏")
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AdminOpsService adminOpsService;

    // ==================== 轮播（SUPER/OPERATOR） ====================

    @Operation(summary = "轮播全量（含下线，按 sort 排序）")
    @RequireRole({"super", "operator"})
    @GetMapping("/banners")
    public Result<List<Banner>> banners() {
        return Result.ok(adminOpsService.bannerList());
    }

    @Operation(summary = "新建轮播（默认下线态）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "BANNER_CREATE", targetType = "BANNER")
    @PostMapping("/banners")
    public Result<Long> createBanner(@RequestBody BannerBody body) {
        return Result.ok(adminOpsService.createBanner(body.getTitle(), body.getImageUrl(),
                body.getLinkUrl(), body.getSort()).getId());
    }

    @Operation(summary = "编辑轮播（含上下线切换）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "BANNER_UPDATE", targetType = "BANNER")
    @PutMapping("/banners/{id}")
    public Result<Void> updateBanner(@PathVariable Long id, @RequestBody BannerBody body) {
        adminOpsService.updateBanner(id, body.getTitle(), body.getImageUrl(),
                body.getLinkUrl(), body.getSort(), body.getStatus());
        return Result.ok();
    }

    @Operation(summary = "删除轮播")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "BANNER_DELETE", targetType = "BANNER")
    @DeleteMapping("/banners/{id}")
    public Result<Void> deleteBanner(@PathVariable Long id) {
        adminOpsService.deleteBanner(id);
        return Result.ok();
    }

    // ==================== 公告（SUPER/OPERATOR） ====================

    @Operation(summary = "公告全量（草稿优先，新在前）")
    @RequireRole({"super", "operator"})
    @GetMapping("/notices")
    public Result<List<Notice>> notices() {
        return Result.ok(adminOpsService.noticeList());
    }

    @Operation(summary = "新建公告（草稿态）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "NOTICE_CREATE", targetType = "NOTICE")
    @PostMapping("/notices")
    public Result<Long> createNotice(@RequestBody NoticeBody body) {
        return Result.ok(adminOpsService.createNotice(body.getTitle(), body.getContent(),
                requireAdmin()).getId());
    }

    @Operation(summary = "编辑公告（标题/内容）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "NOTICE_UPDATE", targetType = "NOTICE")
    @PutMapping("/notices/{id}")
    public Result<Void> updateNotice(@PathVariable Long id, @RequestBody NoticeBody body) {
        adminOpsService.updateNotice(id, body.getTitle(), body.getContent());
        return Result.ok();
    }

    @Operation(summary = "发布（草稿→发布）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "NOTICE_PUBLISH", targetType = "NOTICE")
    @PostMapping("/notices/{id}/publish")
    public Result<Void> publishNotice(@PathVariable Long id) {
        adminOpsService.publishNotice(id, requireAdmin());
        return Result.ok();
    }

    @Operation(summary = "下线（发布→下线）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "NOTICE_OFFLINE", targetType = "NOTICE")
    @PostMapping("/notices/{id}/offline")
    public Result<Void> offlineNotice(@PathVariable Long id) {
        adminOpsService.offlineNotice(id, requireAdmin());
        return Result.ok();
    }

    @Operation(summary = "删除公告")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "NOTICE_DELETE", targetType = "NOTICE")
    @DeleteMapping("/notices/{id}")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        adminOpsService.deleteNotice(id);
        return Result.ok();
    }

    // ==================== 数据大屏（查看三角色） ====================

    @Operation(summary = "数据大屏聚合（用户/商品/订单/GMV/待办/信用分布/周趋势）")
    @RequireRole({"super", "auditor", "operator"})
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.ok(adminOpsService.dashboard());
    }

    private LoginUser requireAdmin() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.ADMIN) {
            throw new com.campus.market.common.exception.BusinessException(
                    com.campus.market.common.api.ErrorCode.FORBIDDEN);
        }
        return user;
    }

    @Data
    public static class BannerBody {
        private String title;
        private String imageUrl;
        private String linkUrl;
        private Integer sort;
        private Integer status;
    }

    @Data
    public static class NoticeBody {
        private String title;
        private String content;
    }
}
