package com.campus.market.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.api.Result;
import com.campus.market.entity.Banner;
import com.campus.market.entity.Notice;
import com.campus.market.mapper.BannerMapper;
import com.campus.market.mapper.NoticeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台轮播/公告读取（PRD ADM-07 C 端出口，公开接口）：
 * 仅返回上线轮播与已发布公告，排序按管理端配置。
 */
@Tag(name = "前台-轮播与公告")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OpsContentController {

    private final BannerMapper bannerMapper;
    private final NoticeMapper noticeMapper;

    @Operation(summary = "首页轮播（仅上线，sort 升序）")
    @GetMapping("/banners")
    public Result<List<Banner>> banners() {
        return Result.ok(bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, Banner.STATUS_ONLINE)
                .orderByAsc(Banner::getSort)
                .orderByAsc(Banner::getId)));
    }

    @Operation(summary = "已发布公告（published_at 倒序，前 20 条）")
    @GetMapping("/notices")
    public Result<List<Notice>> notices() {
        return Result.ok(noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, Notice.STATUS_PUBLISHED)
                .orderByDesc(Notice::getPublishedAt)
                .last("LIMIT 20")));
    }
}
