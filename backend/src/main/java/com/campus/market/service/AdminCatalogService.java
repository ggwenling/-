package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.entity.Category;
import com.campus.market.entity.SensitiveWord;
import com.campus.market.entity.Tag;

import java.util.List;

/**
 * 管理后台分类/标签/敏感词库服务（PRD ADM-06/ADM-03，SUPER+OPERATOR）。
 */
public interface AdminCatalogService {

    // ==================== 分类（二级树，T1） ====================

    List<Category> categoryTree();

    Category createCategory(Long parentId, String name, Integer sort);

    void updateCategory(Long id, String name, String icon, Integer sort, Integer status);

    /** 删除分类：存在子分类或被商品引用时拒绝（40919） */
    void deleteCategory(Long id);

    // ==================== 标签（GDS-08） ====================

    List<Tag> tagList();

    Tag createTag(String name, Integer sort);

    void updateTag(Long id, String name, Integer sort, Integer status);

    /** 删除标签：被商品引用时拒绝（40919） */
    void deleteTag(Long id);

    // ==================== 敏感词库（ADM-03，DFA 刷新） ====================

    PageResult<SensitiveWord> wordPage(String keyword, long pageNum, long pageSize);

    /** 新增敏感词（uk_word 重复 40920），落库后刷新 DFA */
    Long addWord(String word);

    /** 删除敏感词（存在性校验 40411），落库后刷新 DFA */
    void deleteWord(Long id);

    /** 批量导入（去重），返回实际新增数，落库后刷新 DFA */
    int importWords(List<String> words);
}
