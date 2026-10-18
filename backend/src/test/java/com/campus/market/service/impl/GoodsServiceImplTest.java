package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.dto.GoodsImageDTO;
import com.campus.market.dto.GoodsPublishDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.GoodsTag;
import com.campus.market.entity.Tag;
import com.campus.market.entity.UserBehavior;
import com.campus.market.entity.Favorite;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.TagMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserAccessGuard;
import com.campus.market.service.RecommendService;
import com.campus.market.service.SensitiveWordService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GoodsServiceImpl 中测（GDS-01/02：发布校验链、上下架状态机、管理员下架禁自恢复、30 天恢复规则）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long GOODS_ID = 10L;

    @Mock GoodsMapper goodsMapper;
    @Mock GoodsImageMapper goodsImageMapper;
    @Mock GoodsTagMapper goodsTagMapper;
    @Mock TagMapper tagMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock UserBehaviorMapper userBehaviorMapper;
    @Mock FavoriteMapper favoriteMapper;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock RecommendService recommendService;
    /** 真实守卫（阈值取自 CreditProperties 默认 60），与生产口径一致 */
    @Spy UserAccessGuard userAccessGuard = new UserAccessGuard(new CreditProperties());

    @InjectMocks GoodsServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Goods.class, GoodsImage.class, GoodsTag.class, Tag.class,
                Category.class, UserBehavior.class, Favorite.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(sensitiveWordService.findHits(any())).thenReturn(List.of());
        lenient().when(categoryMapper.selectById(100L)).thenReturn(category());
    }

    // ==================== GDS-01 发布 ====================

    @Test
    void publish_uncertified_rejected() {
        assertThatThrownBy(() -> service.publish(dto(), LoginUserTestFactory.uncertified(USER_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_NOT_CERTIFIED));
    }

    @Test
    void publish_creditRestricted_rejected() {
        // CRD-02：受限用户（creditScore < 60）禁止发布商品
        assertThatThrownBy(() -> service.publish(dto(), LoginUserTestFactory.restricted(USER_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_RESTRICTED));
    }

    @Test
    void update_creditRestricted_rejected() {
        // CRD-02：受限校验先于存在性/归属校验，受限用户编辑同样被拦截（编辑与发布口径一致）
        assertThatThrownBy(() -> service.update(GOODS_ID, dto(), LoginUserTestFactory.restricted(USER_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_RESTRICTED));
    }

    @Test
    void publish_negativePrice_rejected() {
        GoodsPublishDTO bad = dto();
        bad.setPrice(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.publish(bad, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_PARAM_INVALID));
    }

    @Test
    void publish_badConditionLevel_rejected() {
        GoodsPublishDTO bad = dto();
        bad.setConditionLevel(5);
        assertThatThrownBy(() -> service.publish(bad, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_PARAM_INVALID));
    }

    @Test
    void publish_sensitiveWord_rejected() {
        when(sensitiveWordService.findHits(any())).thenReturn(List.of("代考"));
        assertThatThrownBy(() -> service.publish(dto(), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_SENSITIVE));
        verify(goodsMapper, never()).insert(any(Goods.class));
    }

    @Test
    void publish_success_initializesCounters() {
        when(goodsMapper.insert(any(Goods.class))).thenAnswer(inv -> {
            inv.getArgument(0, Goods.class).setId(GOODS_ID);
            return 1;
        });

        Long id = service.publish(dto(), user());

        assertThat(id).isEqualTo(GOODS_ID);
        verify(goodsMapper).insert(any(Goods.class));
        verify(goodsImageMapper).insert(any(GoodsImage.class));   // 1 张图重建
    }

    // ==================== GDS-02 上下架状态机 ====================

    @Test
    void offSale_notOnSale_rejected() {
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(goods(Goods.STATUS_IN_TRANSACTION, null));
        assertThatThrownBy(() -> service.offSale(GOODS_ID, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_ON_SALE));
    }

    @Test
    void onSale_adminTakedown_forbidden() {
        // 管理员下架（off_sale_reason 非空）不允许卖家自行上架 → 40303
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(goods(Goods.STATUS_OFF_SALE, "违规内容"));
        assertThatThrownBy(() -> service.onSale(GOODS_ID, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_FORBIDDEN));
    }

    @Test
    void onSale_sellerOffSale_success() {
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(goods(Goods.STATUS_OFF_SALE, null));
        service.onSale(GOODS_ID, user());
        verify(goodsMapper).update(isNull(), any());
    }

    @Test
    void restore_adminDeleted_forbidden() {
        Goods deleted = goods(Goods.STATUS_DELETED, null);
        deleted.setDeletedByType(Goods.DELETED_BY_ADMIN);
        deleted.setDeletedBy(900L);
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(deleted);

        assertThatThrownBy(() -> service.restore(GOODS_ID, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_FORBIDDEN));
    }

    @Test
    void restore_over30Days_forbidden() {
        Goods deleted = goods(Goods.STATUS_DELETED, null);
        deleted.setDeletedByType(Goods.DELETED_BY_USER);
        deleted.setDeletedBy(USER_ID);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(31));
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(deleted);

        assertThatThrownBy(() -> service.restore(GOODS_ID, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_FORBIDDEN));
    }

    @Test
    void restore_userDeletedWithin30Days_success() {
        Goods deleted = goods(Goods.STATUS_DELETED, null);
        deleted.setDeletedByType(Goods.DELETED_BY_USER);
        deleted.setDeletedBy(USER_ID);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(3));
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(deleted);

        service.restore(GOODS_ID, user());

        verify(goodsMapper).update(isNull(), any());   // → OFF_SALE + 清审计四元组
    }

    // ==================== 辅助 ====================

    private LoginUser user() {
        return LoginUserTestFactory.user(USER_ID);
    }

    private Category category() {
        Category category = new Category();
        category.setId(100L);
        category.setParentId(1L);
        category.setName("教材课本");
        category.setStatus(Category.STATUS_ENABLED);
        return category;
    }

    private Goods goods(String status, String offSaleReason) {
        Goods goods = new Goods();
        goods.setId(GOODS_ID);
        goods.setUserId(USER_ID);
        goods.setStatus(status);
        goods.setOffSaleReason(offSaleReason);
        return goods;
    }

    private GoodsPublishDTO dto() {
        GoodsPublishDTO dto = new GoodsPublishDTO();
        dto.setTitle("九成新台灯");
        dto.setDescription("宿舍自用，几乎全新");
        dto.setCategoryId(100L);
        dto.setPrice(new BigDecimal("45.00"));
        dto.setConditionLevel(2);
        GoodsImageDTO image = new GoodsImageDTO();
        image.setUrl("/upload/202610/test.png");
        image.setThumbUrl("/upload/202610/test_thumb.png");
        dto.setImages(List.of(image));
        return dto;
    }
}
