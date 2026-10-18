package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Category;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsTag;
import com.campus.market.entity.SensitiveWord;
import com.campus.market.entity.Tag;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.SensitiveWordMapper;
import com.campus.market.mapper.TagMapper;
import com.campus.market.service.AdminCatalogService;
import com.campus.market.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台分类/标签/敏感词库实现（PRD ADM-06/ADM-03 / GDS-08 / §5.8）。
 * 一致性要点：
 * - 分类删除前置校验：子分类/商品引用任一存在即拒绝（40919），物理外键兜底；
 * - 标签删除前置校验：goods_tag 引用即拒绝；
 * - 敏感词变更后必须调用 sensitiveWordService.refresh()（volatile DFA 全量重建）；
 * - 批量导入按 uk_word 去重（INSERT IGNORE 语义等价的先查后插 + DuplicateKey 兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCatalogServiceImpl implements AdminCatalogService {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsTagMapper goodsTagMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordService sensitiveWordService;

    // ==================== 分类 ====================

    @Override
    public List<Category> categoryTree() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    @Override
    @Transactional
    public Category createCategory(Long parentId, String name, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "分类名称必填");
        }
        if (parentId != null && parentId > 0) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "父分类不存在");
            }
            if (parent.getParentId() != null && parent.getParentId() > 0) {
                throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "仅支持两级分类");
            }
        }
        Category category = new Category();
        category.setParentId(parentId == null ? 0L : parentId);
        category.setName(name.trim());
        category.setSort(sort == null ? 0 : sort);
        category.setStatus(Category.STATUS_ENABLED);
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @Transactional
    public void updateCategory(Long id, String name, String icon, Integer sort, Integer status) {
        requireCategory(id);
        Category patch = new Category();
        patch.setId(id);
        if (StringUtils.hasText(name)) {
            patch.setName(name.trim());
        }
        if (icon != null) {
            patch.setIcon(icon);
        }
        if (sort != null) {
            patch.setSort(sort);
        }
        if (status != null) {
            if (status != Category.STATUS_ENABLED && status != Category.STATUS_DISABLED) {
                throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "状态取值非法");
            }
            patch.setStatus(status);
        }
        categoryMapper.updateById(patch);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ErrorCode.REF_IN_USE, "该分类下存在子分类，无法删除");
        }
        Long goodsRefs = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getCategoryId, id));
        if (goodsRefs != null && goodsRefs > 0) {
            throw new BusinessException(ErrorCode.REF_IN_USE, "该分类下存在商品，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    // ==================== 标签 ====================

    @Override
    public List<Tag> tagList() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByAsc(Tag::getSort)
                .orderByAsc(Tag::getId));
    }

    @Override
    @Transactional
    public Tag createTag(String name, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "标签名称必填");
        }
        Tag tag = new Tag();
        tag.setName(name.trim());
        tag.setSort(sort == null ? 0 : sort);
        tag.setStatus(Tag.STATUS_ENABLED);
        tagMapper.insert(tag);
        return tag;
    }

    @Override
    @Transactional
    public void updateTag(Long id, String name, Integer sort, Integer status) {
        requireTag(id);
        Tag patch = new Tag();
        patch.setId(id);
        if (StringUtils.hasText(name)) {
            patch.setName(name.trim());
        }
        if (sort != null) {
            patch.setSort(sort);
        }
        if (status != null) {
            if (status != Tag.STATUS_ENABLED && status != Tag.STATUS_DISABLED) {
                throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "状态取值非法");
            }
            patch.setStatus(status);
        }
        tagMapper.updateById(patch);
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        requireTag(id);
        Long refs = goodsTagMapper.selectCount(new LambdaQueryWrapper<GoodsTag>()
                .eq(GoodsTag::getTagId, id));
        if (refs != null && refs > 0) {
            throw new BusinessException(ErrorCode.REF_IN_USE, "该标签仍被商品引用，无法删除");
        }
        tagMapper.deleteById(id);
    }

    // ==================== 敏感词库 ====================

    @Override
    public PageResult<SensitiveWord> wordPage(String keyword, long pageNum, long pageSize) {
        Page<SensitiveWord> result = sensitiveWordMapper.selectPage(
                new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)),
                new LambdaQueryWrapper<SensitiveWord>()
                        .like(StringUtils.hasText(keyword), SensitiveWord::getWord, keyword)
                        .orderByAsc(SensitiveWord::getWord));
        PageResult<SensitiveWord> page = new PageResult<>();
        page.setList(result.getRecords());
        page.setTotal(result.getTotal());
        page.setPageNum(result.getCurrent());
        page.setPageSize(result.getSize());
        return page;
    }

    @Override
    @Transactional
    public Long addWord(String word) {
        String normalized = normalizeWord(word);
        try {
            SensitiveWord entity = new SensitiveWord();
            entity.setWord(normalized);
            sensitiveWordMapper.insert(entity);
            sensitiveWordService.refresh();
            return entity.getId();
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.WORD_DUPLICATE);
        }
    }

    @Override
    @Transactional
    public void deleteWord(Long id) {
        SensitiveWord word = sensitiveWordMapper.selectById(id);
        if (word == null) {
            throw new BusinessException(ErrorCode.WORD_NOT_FOUND);
        }
        sensitiveWordMapper.deleteById(id);
        sensitiveWordService.refresh();
    }

    @Override
    @Transactional
    public int importWords(List<String> words) {
        if (words == null || words.isEmpty()) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "导入词列表为空");
        }
        Set<String> distinct = words.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeWord)
                .collect(Collectors.toCollection(HashSet::new));
        int added = 0;
        for (String word : distinct) {
            Long exists = sensitiveWordMapper.selectCount(new LambdaQueryWrapper<SensitiveWord>()
                    .eq(SensitiveWord::getWord, word));
            if (exists != null && exists > 0) {
                continue;
            }
            try {
                SensitiveWord entity = new SensitiveWord();
                entity.setWord(word);
                sensitiveWordMapper.insert(entity);
                added++;
            } catch (DuplicateKeyException ignored) {
                // 并发导入重复：幂等跳过
            }
        }
        sensitiveWordService.refresh();
        log.info("敏感词批量导入完成：提交 {} 条，去重后新增 {} 条", words.size(), added);
        return added;
    }

    // ==================== 私有 ====================

    private String normalizeWord(String word) {
        if (!StringUtils.hasText(word)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "敏感词不能为空");
        }
        return word.trim().toLowerCase(Locale.ROOT);
    }

    private void requireCategory(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
    }

    private void requireTag(Long id) {
        if (tagMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
    }
}
