package com.campus.market.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.util.RedisService;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsTag;
import com.campus.market.entity.UserBehavior;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecommendServiceImpl 深测（REC-01~03：行为权重 5/3/1、候选排除、冷启动回退、缓存 TTL）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendServiceImplTest {

    private static final String KEY_HOT = "campus:market:rec:hot:goods";
    private static final String KEY_USER_PREFIX = "campus:market:rec:user:";
    private static final String KEY_SIMILAR_PREFIX = "campus:market:rec:similar:";

    @Mock GoodsMapper goodsMapper;
    @Mock GoodsTagMapper goodsTagMapper;
    @Mock UserBehaviorMapper userBehaviorMapper;
    @Mock RedisService redisService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private RecommendServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Goods.class, GoodsTag.class, UserBehavior.class);
    }

    @BeforeEach
    void setUp() {
        service = new RecommendServiceImpl(goodsMapper, goodsTagMapper, userBehaviorMapper, redisService, objectMapper);
        lenient().when(redisService.get(anyString())).thenReturn(null);
    }

    // ==================== REC-01/02 猜你喜欢 ====================

    @Test
    void guestUser_fallsBackToHotList() {
        stubHotPool(30L, 20L, 10L);

        List<Long> ids = service.recommendForUser(null, 5);

        assertThat(ids).containsExactly(30L, 20L, 10L);
        verify(redisService).set(eq(KEY_HOT), anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    void userCacheHit_returnsTruncated_withoutDb() {
        when(redisService.get(KEY_USER_PREFIX + 1L)).thenReturn("[7,8,9]");

        List<Long> ids = service.recommendForUser(1L, 2);

        assertThat(ids).containsExactly(7L, 8L);
        verify(goodsMapper, never()).selectPage(any(), any());
    }

    @Test
    void userWithBehavior_scoresByTagAndCategory_excludesInteracted() {
        // 行为：goods1 成交（权重 5），goods1 属分类 A(100)、标签 7，发布者 99
        UserBehavior done = behavior(1L, 1L, UserBehavior.BEHAVIOR_ORDER_DONE);
        when(userBehaviorMapper.selectList(any())).thenReturn(List.of(done));
        when(goodsMapper.selectBatchIds(any())).thenReturn(List.of(goods(1L, 99L, 100L)));
        when(goodsTagMapper.selectList(any())).thenReturn(List.of(tag(1L, 7L), tag(4L, 7L)));

        // 候选池：goods4 共享标签 7 / goods5 同分类 A / goods1 已互动（Java 层排除，自身发布的 ne(userId)
        // 为 SQL 语义，mock 不执行过滤，由集成测试兜底验证）
        Page<Goods> pool = new Page<>(1, 300, false);
        pool.setRecords(List.of(
                goods(4L, 98L, 200L),   // catB 标签 7
                goods(5L, 97L, 100L),   // catA 无标签
                goods(1L, 99L, 100L))); // 已互动：打分循环 continue
        when(goodsMapper.selectPage(any(), any())).thenReturn(pool);

        List<Long> ids = service.recommendForUser(1L, 10);

        // goods4：共享标签 5×2 + 热度微调 > goods5：分类 5 + 热度微调；goods1 被排除
        assertThat(ids).startsWith(4L);
        assertThat(ids).contains(5L);
        assertThat(ids).doesNotContain(1L);
        // 回填缓存 TTL 2h
        verify(redisService).set(startsWith(KEY_USER_PREFIX), anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    void userWithoutBehavior_coldStartFallsBackToHot() {
        when(userBehaviorMapper.selectList(any())).thenReturn(List.of());
        stubHotPool(50L, 40L);

        List<Long> ids = service.recommendForUser(2L, 5);

        assertThat(ids).containsExactly(50L, 40L);   // PRD §6.6 冷启动
    }

    // ==================== REC-02 热度榜 ====================

    @Test
    void hotList_cacheHit_noDb() {
        when(redisService.get(KEY_HOT)).thenReturn("[11,22]");

        assertThat(service.hotGoodsIds(5)).containsExactly(11L, 22L);
        verify(goodsMapper, never()).selectPage(any(), any());
    }

    // ==================== REC-03 相似推荐 ====================

    @Test
    void similar_scoresSharedTagsHigherThanSameCategory() {
        when(goodsMapper.selectById(1L)).thenReturn(goods(1L, 99L, 100L));
        when(goodsTagMapper.selectList(any())).thenReturn(List.of(tag(1L, 7L), tag(3L, 7L)));
        Page<Goods> sameCategory = new Page<>(1, 100, false);
        sameCategory.setRecords(List.of(goods(2L, 97L, 100L)));
        when(goodsMapper.selectPage(any(), any())).thenReturn(sameCategory);
        when(goodsMapper.selectBatchIds(any()))
                .thenReturn(List.of(goods(2L, 97L, 100L), goods(3L, 96L, 200L)));

        List<Long> ids = service.similarGoodsIds(1L, 6);

        // goods3 共享标签（2×1 + 热度微调）> goods2 仅同分类（1 + 热度微调）；排除自身
        assertThat(ids).startsWith(3L);
        assertThat(ids).contains(2L);
        assertThat(ids).doesNotContain(1L);
        verify(redisService).set(startsWith(KEY_SIMILAR_PREFIX), anyString(), eq(Duration.ofHours(6)));
    }

    @Test
    void similar_goodsDeleted_returnsEmpty() {
        when(goodsMapper.selectById(1L)).thenReturn(null);
        assertThat(service.similarGoodsIds(1L, 6)).isEmpty();
    }

    // ==================== limit 归一化 ====================

    @Test
    void limitNormalized_boundaries() {
        stubHotPool(LongStream.rangeClosed(1, 60).map(i -> 61 - i).toArray());
        assertThat(service.hotGoodsIds(0)).hasSize(1);      // 下限 1
        assertThat(service.hotGoodsIds(999)).hasSize(50);   // 上限 HOT_CACHE_SIZE=50
    }

    // ==================== 辅助 ====================

    private void stubHotPool(long... ids) {
        Page<Goods> pool = new Page<>(1, 50, false);
        pool.setRecords(java.util.Arrays.stream(ids).mapToObj(id -> goods(id, 999L, 100L)).toList());
        lenient().when(goodsMapper.selectPage(any(), any())).thenReturn(pool);
        lenient().when(goodsTagMapper.selectList(any())).thenReturn(List.of());
    }

    private Goods goods(Long id, Long userId, Long categoryId) {
        Goods goods = new Goods();
        goods.setId(id);
        goods.setUserId(userId);
        goods.setCategoryId(categoryId);
        goods.setStatus(Goods.STATUS_ON_SALE);
        goods.setHeatScore(0);
        return goods;
    }

    private UserBehavior behavior(Long goodsId, Long userId, String behavior) {
        UserBehavior b = new UserBehavior();
        b.setUserId(userId);
        b.setGoodsId(goodsId);
        b.setBehavior(behavior);
        b.setBehaviorDate(LocalDate.now());
        return b;
    }

    private GoodsTag tag(Long goodsId, Long tagId) {
        GoodsTag t = new GoodsTag();
        t.setGoodsId(goodsId);
        t.setTagId(tagId);
        return t;
    }
}
