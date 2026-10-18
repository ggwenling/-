package com.campus.market.service.impl;

import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
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
import com.campus.market.service.SensitiveWordService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminCatalogServiceImpl 深测（ADM-06/03：分类树/删除引用校验、标签 CRUD、敏感词增删与 DFA 刷新）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCatalogServiceImplTest {

    private static final Long CAT_ID = 100L;
    private static final Long TAG_ID = 200L;
    private static final Long WORD_ID = 300L;

    @Mock CategoryMapper categoryMapper;
    @Mock TagMapper tagMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock GoodsTagMapper goodsTagMapper;
    @Mock SensitiveWordMapper sensitiveWordMapper;
    @Mock SensitiveWordService sensitiveWordService;

    @InjectMocks AdminCatalogServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Category.class, Tag.class, Goods.class, GoodsTag.class, SensitiveWord.class);
    }

    @BeforeEach
    void setUp() {
        when(categoryMapper.selectById(CAT_ID)).thenReturn(category(0L));
        when(tagMapper.selectById(TAG_ID)).thenReturn(tag());
        when(sensitiveWordMapper.selectCount(any())).thenReturn(0L);
    }

    private Category category(Long parentId) {
        Category category = new Category();
        category.setId(CAT_ID);
        category.setParentId(parentId);
        category.setName("教材课本");
        category.setStatus(Category.STATUS_ENABLED);
        return category;
    }

    private Tag tag() {
        Tag tag = new Tag();
        tag.setId(TAG_ID);
        tag.setName("九成新");
        tag.setStatus(Tag.STATUS_ENABLED);
        return tag;
    }

    // ==================== 分类 ====================

    @Test
    void createCategory_threeLevel_rejected() {
        when(categoryMapper.selectById(5L)).thenReturn(category(2L));   // 父分类本身是二级
        assertThatThrownBy(() -> service.createCategory(5L, "非法三级", 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void createCategory_emptyName_rejected() {
        assertThatThrownBy(() -> service.createCategory(0L, " ", 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void deleteCategory_hasChildren_rejected() {
        when(categoryMapper.selectCount(any())).thenReturn(2L);   // 子分类存在
        assertThatThrownBy(() -> service.deleteCategory(CAT_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REF_IN_USE));
    }

    @Test
    void deleteCategory_goodsReferenced_rejected() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);           // 无子分类
        when(goodsMapper.selectCount(any())).thenReturn(3L);              // 有商品引用
        assertThatThrownBy(() -> service.deleteCategory(CAT_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REF_IN_USE));
        verify(categoryMapper, never()).deleteById(CAT_ID);
    }

    @Test
    void deleteCategory_free_success() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(goodsMapper.selectCount(any())).thenReturn(0L);

        service.deleteCategory(CAT_ID);

        verify(categoryMapper).deleteById(CAT_ID);
    }

    @Test
    void updateCategory_invalidStatus_rejected() {
        assertThatThrownBy(() -> service.updateCategory(CAT_ID, null, null, null, 9))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    // ==================== 标签 ====================

    @Test
    void deleteTag_referencedByGoods_rejected() {
        when(goodsTagMapper.selectCount(any())).thenReturn(4L);
        assertThatThrownBy(() -> service.deleteTag(TAG_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REF_IN_USE));
        verify(tagMapper, never()).deleteById(TAG_ID);
    }

    @Test
    void createTag_success_enabled() {
        java.util.List<Tag> inserted = new java.util.ArrayList<>();
        when(tagMapper.insert(any(Tag.class))).thenAnswer(inv -> {
            Tag t = inv.getArgument(0, Tag.class);
            inserted.add(t);
            t.setId(TAG_ID);
            return 1;
        });

        Tag created = service.createTag("全新未拆", 3);

        assertThat(created.getId()).isEqualTo(TAG_ID);
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getStatus()).isEqualTo(Tag.STATUS_ENABLED);
    }

    // ==================== 敏感词库 ====================

    private SensitiveWord word(String w) {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(WORD_ID);
        sw.setWord(w);
        return sw;
    }

    @Test
    void addWord_normalizesToLowerCase_andRefreshesDfa() {
        java.util.List<SensitiveWord> inserted = new java.util.ArrayList<>();
        when(sensitiveWordMapper.insert(any(SensitiveWord.class))).thenAnswer(inv -> {
            SensitiveWord s = inv.getArgument(0, SensitiveWord.class);
            inserted.add(s);
            s.setId(WORD_ID);
            return 1;
        });

        service.addWord("  外挂  ");

        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getWord()).isEqualTo("外挂");   // trim 规范化
        verify(sensitiveWordService).refresh();   // DFA 即时刷新
    }

    @Test
    void addWord_duplicate_rejected() {
        when(sensitiveWordMapper.insert(any(SensitiveWord.class)))
                .thenThrow(new DuplicateKeyException("uk_word"));
        assertThatThrownBy(() -> service.addWord("外挂"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WORD_DUPLICATE));
    }

    @Test
    void deleteWord_missing_rejected() {
        when(sensitiveWordMapper.selectById(WORD_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteWord(WORD_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WORD_NOT_FOUND));
    }

    @Test
    void deleteWord_success_refreshesDfa() {
        when(sensitiveWordMapper.selectById(WORD_ID)).thenReturn(word("外挂"));

        service.deleteWord(WORD_ID);

        verify(sensitiveWordMapper).deleteById(WORD_ID);
        verify(sensitiveWordService).refresh();
    }

    @Test
    void importWords_dedups_andRefreshesOnce() {
        when(sensitiveWordMapper.selectCount(any())).thenReturn(0L);
        when(sensitiveWordMapper.insert(any(SensitiveWord.class))).thenReturn(1);

        int added = service.importWords(java.util.Arrays.asList("外挂", "外挂", "  ", null, "作弊器"));

        assertThat(added).isEqualTo(2);   // 去重 + 空白过滤
        verify(sensitiveWordMapper, org.mockito.Mockito.times(2)).insert(any(SensitiveWord.class));
        verify(sensitiveWordService, org.mockito.Mockito.times(1)).refresh();
    }

    @Test
    void importWords_empty_rejected() {
        assertThatThrownBy(() -> service.importWords(List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }
}
