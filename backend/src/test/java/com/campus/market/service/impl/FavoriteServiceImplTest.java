package com.campus.market.service.impl;

import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Favorite;
import com.campus.market.entity.Goods;
import com.campus.market.entity.UserBehavior;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.TagMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FavoriteServiceImpl 浅测（T5：收藏事实 + favorite_count 原子 +1 + FAVORITE 埋点；重复幂等）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long GOODS_ID = 10L;

    @Mock FavoriteMapper favoriteMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock GoodsImageMapper goodsImageMapper;
    @Mock GoodsTagMapper goodsTagMapper;
    @Mock TagMapper tagMapper;
    @Mock UserBehaviorMapper userBehaviorMapper;

    @InjectMocks FavoriteServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Favorite.class, Goods.class, UserBehavior.class);
    }

    private void stubExistingGoods() {
        lenient().when(goodsMapper.selectById(GOODS_ID)).thenReturn(onSaleGoods());
    }

    private Goods onSaleGoods() {
        Goods goods = new Goods();
        goods.setId(GOODS_ID);
        goods.setStatus(Goods.STATUS_ON_SALE);
        return goods;
    }

    @Test
    void add_goodsMissing_rejected() {
        when(goodsMapper.selectById(GOODS_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.add(USER_ID, GOODS_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    @Test
    void add_success_incrementsCounterAndRecordsBehavior() {
        stubExistingGoods();
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(Favorite.class))).thenReturn(1);

        service.add(USER_ID, GOODS_ID);

        verify(goodsMapper).update(isNull(), any());                      // favorite_count+1（原子 setSql）
        verify(userBehaviorMapper).insert(any(UserBehavior.class));       // FAVORITE 埋点（REC 输入）
    }

    @Test
    void add_alreadyFavorited_idempotent() {
        stubExistingGoods();
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        assertThatCode(() -> service.add(USER_ID, GOODS_ID)).doesNotThrowAnyException();

        verify(favoriteMapper, never()).insert(any(Favorite.class));
        verify(goodsMapper, never()).update(isNull(), any());
    }

    @Test
    void add_concurrentDuplicateKey_silentlyIgnored() {
        stubExistingGoods();
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(Favorite.class))).thenThrow(new DuplicateKeyException("uk"));

        assertThatCode(() -> service.add(USER_ID, GOODS_ID)).doesNotThrowAnyException();

        verify(goodsMapper, never()).update(isNull(), any());
    }

    @Test
    void remove_rowsDeleted_decrementsCounter() {
        when(favoriteMapper.delete(any())).thenReturn(1);

        service.remove(USER_ID, GOODS_ID);

        verify(goodsMapper).update(isNull(), any());   // GREATEST(count-1, 0)
    }

    @Test
    void remove_nothingDeleted_noCounterChange() {
        when(favoriteMapper.delete(any())).thenReturn(0);

        service.remove(USER_ID, GOODS_ID);

        verify(goodsMapper, never()).update(isNull(), any());
    }
}
