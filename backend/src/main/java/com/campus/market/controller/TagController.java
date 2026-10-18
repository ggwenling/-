package com.campus.market.controller;

import com.campus.market.common.api.Result;
import com.campus.market.service.TagService;
import com.campus.market.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口（PRD GDS-08：启用中标签列表，供发布表单多选 0~5；公共接口无需登录）
 */
@Tag(name = "商品-标签")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "启用中的标签列表")
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.ok(tagService.listEnabled());
    }
}
