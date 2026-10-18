package com.campus.market.controller;

import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.service.ReviewService;
import com.campus.market.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价展示接口（PRD CRD-03）：按被评价人分页查询其收到的评价，公开访问（游客可见），
 * 时间倒序；个人主页"收到的评价"Tab 数据源（接线由主代理汇合）。
 */
@Tag(name = "交易-评价")
@Validated
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "某人收到的评价分页（CRD-03，公开）")
    @GetMapping
    public Result<PageResult<ReviewVO>> pageByReviewee(
            @RequestParam @NotNull(message = "被评价人不能为空") Long revieweeId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        // 公开接口：仅读取脱敏后的评价展示字段（昵称/头像），不含学号邮箱（PRD §6.1）
        return Result.ok(reviewService.pageByReviewee(revieweeId, pageNum, pageSize));
    }
}
