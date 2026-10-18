package com.campus.market.controller;

import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.entity.Category;
import com.campus.market.entity.SensitiveWord;
import com.campus.market.security.annotation.OperationLog;
import com.campus.market.security.annotation.RequireRole;
import com.campus.market.service.AdminCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类/标签/敏感词库管理（PRD ADM-06/ADM-03）。
 * 权限矩阵：分类标签 = SUPER+OPERATOR；敏感词库（内容安全）= SUPER+AUDITOR。
 */
@Tag(name = "管理后台-分类/标签/敏感词库")
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    // ==================== 分类（SUPER/OPERATOR） ====================

    @Operation(summary = "分类全量（按 sort 排序，前端组树）")
    @RequireRole({"super", "operator"})
    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(adminCatalogService.categoryTree());
    }

    @Operation(summary = "新建分类（parentId 空或 0 为一级；仅两级）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "CATEGORY_CREATE", targetType = "CATEGORY")
    @PostMapping("/categories")
    public Result<Long> createCategory(@RequestBody CategoryBody body) {
        return Result.ok(adminCatalogService.createCategory(body.getParentId(), body.getName(),
                body.getSort()).getId());
    }

    @Operation(summary = "编辑分类（名称/图标/排序/启停）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "CATEGORY_UPDATE", targetType = "CATEGORY")
    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryBody body) {
        adminCatalogService.updateCategory(id, body.getName(), body.getIcon(), body.getSort(), body.getStatus());
        return Result.ok();
    }

    @Operation(summary = "删除分类（有子分类或商品引用时拒绝）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "CATEGORY_DELETE", targetType = "CATEGORY")
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminCatalogService.deleteCategory(id);
        return Result.ok();
    }

    // ==================== 标签（SUPER/OPERATOR） ====================

    @Operation(summary = "标签全量")
    @RequireRole({"super", "operator"})
    @GetMapping("/tags")
    public Result<java.util.List<com.campus.market.entity.Tag>> tags() {
        return Result.ok(adminCatalogService.tagList());
    }

    @Operation(summary = "新建标签")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "TAG_CREATE", targetType = "TAG")
    @PostMapping("/tags")
    public Result<Long> createTag(@RequestBody TagBody body) {
        return Result.ok(adminCatalogService.createTag(body.getName(), body.getSort()).getId());
    }

    @Operation(summary = "编辑标签（名称/排序/启停）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "TAG_UPDATE", targetType = "TAG")
    @PutMapping("/tags/{id}")
    public Result<Void> updateTag(@PathVariable Long id, @RequestBody TagBody body) {
        adminCatalogService.updateTag(id, body.getName(), body.getSort(), body.getStatus());
        return Result.ok();
    }

    @Operation(summary = "删除标签（被商品引用时拒绝）")
    @RequireRole({"super", "operator"})
    @OperationLog(action = "TAG_DELETE", targetType = "TAG")
    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        adminCatalogService.deleteTag(id);
        return Result.ok();
    }

    // ==================== 敏感词库（SUPER/AUDITOR） ====================

    @Operation(summary = "敏感词列表（keyword 模糊可选，分页）")
    @RequireRole({"super", "auditor"})
    @GetMapping("/sensitive-words")
    public Result<PageResult<SensitiveWord>> words(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) long pageSize) {
        return Result.ok(adminCatalogService.wordPage(keyword, pageNum, pageSize));
    }

    @Operation(summary = "新增敏感词（重复 40920，落库即刷新 DFA）")
    @RequireRole({"super", "auditor"})
    @OperationLog(action = "WORD_ADD", targetType = "WORD")
    @PostMapping("/sensitive-words")
    public Result<Long> addWord(@RequestBody WordBody body) {
        return Result.ok(adminCatalogService.addWord(body.getWord()));
    }

    @Operation(summary = "删除敏感词（落库即刷新 DFA）")
    @RequireRole({"super", "auditor"})
    @OperationLog(action = "WORD_DELETE", targetType = "WORD")
    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteWord(@PathVariable Long id) {
        adminCatalogService.deleteWord(id);
        return Result.ok();
    }

    @Operation(summary = "批量导入敏感词（自动去重，返回新增数）")
    @RequireRole({"super", "auditor"})
    @OperationLog(action = "WORD_IMPORT", targetType = "WORD")
    @PostMapping("/sensitive-words/import")
    public Result<Integer> importWords(@RequestBody ImportBody body) {
        return Result.ok(adminCatalogService.importWords(body.getWords()));
    }

    // ==================== 请求体 ====================

    @Data
    public static class CategoryBody {
        private Long parentId;
        @NotBlank(message = "名称必填")
        private String name;
        private String icon;
        private Integer sort;
        private Integer status;
    }

    @Data
    public static class TagBody {
        @NotBlank(message = "名称必填")
        private String name;
        private Integer sort;
        private Integer status;
    }

    @Data
    public static class WordBody {
        @NotBlank(message = "敏感词必填")
        private String word;
    }

    @Data
    public static class ImportBody {
        private java.util.List<String> words;
    }
}
