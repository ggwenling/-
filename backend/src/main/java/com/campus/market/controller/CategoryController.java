package com.campus.market.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.api.Result;
import com.campus.market.entity.Category;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.vo.CategoryTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类接口（PRD GDS-08：二级树形分类，公共接口无需登录）。
 * 首页分类宫格与发布表单 el-cascader 共用；纯读树形装配，未单独建 Service（模块文件所有权约束）。
 */
@Tag(name = "商品-分类")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

    @Operation(summary = "分类树（仅启用中）")
    @GetMapping
    public Result<List<CategoryTreeVO>> tree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, Category.STATUS_ENABLED)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, List<Category>> byParent = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != Category.ROOT_PARENT_ID)
                .collect(Collectors.groupingBy(Category::getParentId));

        List<CategoryTreeVO> tree = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == Category.ROOT_PARENT_ID)
                .map(root -> {
                    CategoryTreeVO vo = toVO(root);
                    byParent.getOrDefault(root.getId(), List.of()).forEach(child -> vo.getChildren().add(toVO(child)));
                    return vo;
                }).toList();
        return Result.ok(tree);
    }

    private CategoryTreeVO toVO(Category category) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setSort(category.getSort());
        return vo;
    }
}
