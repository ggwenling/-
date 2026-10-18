package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.GoodsService;
import com.campus.market.service.RecommendService;
import com.campus.market.vo.GoodsCardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 推荐接口（PRD REC-01~03 / §6.6）。
 * 公开接口：游客与登录用户均可读取（游客走热榜兜底，登录用户走个性化缓存）；
 * 推荐结果由定时任务离线写入 Redis，本接口直读缓存（miss 时按需计算回填）。
 */
@Tag(name = "推荐-猜你喜欢与相似推荐")
@Validated
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final GoodsService goodsService;
    private final Environment environment;

    @Operation(summary = "首页猜你喜欢（REC-01：登录用户个性化；游客/新用户热门兜底）")
    @GetMapping("/home")
    public Result<List<GoodsCardVO>> home(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        LoginUser viewer = currentFrontUserOrNull();
        List<Long> ids = recommendService.recommendForUser(viewer == null ? null : viewer.getUserId(), limit);
        return Result.ok(goodsService.cardsByIds(ids));
    }

    @Operation(summary = "热度榜（首页右侧 Top N，REC-02 冷启动共用）")
    @GetMapping("/hot")
    public Result<List<GoodsCardVO>> hot(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return Result.ok(goodsService.cardsByIds(recommendService.hotGoodsIds(limit)));
    }

    @Operation(summary = "商品相似推荐（REC-03：同分类 + 共享标签，排除自身）")
    @GetMapping("/similar/{goodsId}")
    public Result<List<GoodsCardVO>> similar(
            @PathVariable Long goodsId,
            @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
        return Result.ok(goodsService.cardsByIds(recommendService.similarGoodsIds(goodsId, limit)));
    }

    @Operation(summary = "【dev】手动触发推荐离线重算（REC-03 验证手段，仅 dev profile 可用）")
    @PostMapping("/rebuild/trigger")
    public Result<Integer> rebuild() {
        UserContext.requireLogin();
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅开发环境可手动触发推荐重算");
        }
        return Result.ok(recommendService.rebuildOfflineCache());
    }

    /** 推荐位对游客开放：非前台用户主体一律按游客处理（走热榜兜底，PRD §3.1） */
    private LoginUser currentFrontUserOrNull() {
        LoginUser user = UserContext.get();
        return user != null && user.getUserType() == LoginUser.UserType.USER ? user : null;
    }
}
