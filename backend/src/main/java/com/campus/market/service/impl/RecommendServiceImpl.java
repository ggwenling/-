package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.util.RedisService;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsTag;
import com.campus.market.entity.UserBehavior;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.campus.market.service.RecommendService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐服务实现（PRD REC-01~03、§6.6；数据库设计文档 §3.0 派生字段/§3.26 行为表）。
 *
 * 行为权重映射：ORDER_DONE=5 / FAVORITE=3 / VIEW=1（PRD §6.6，不落库，计算时映射）。
 * 打分口径：候选商品得分 = Σ(命中标签的行为加权分) + 分类行为加权分 + 热度微调(×0.05)，
 * 同分按 heat_score 降序；候选池为 ON_SALE 且排除自身发布与已互动过的商品。
 * 缓存：Redis 存商品 ID 数组（JSON），接口直读；miss 时按需计算回填，定时任务负责批量预热。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    /** 行为权重（PRD §6.6） */
    private static final int WEIGHT_ORDER_DONE = 5;
    private static final int WEIGHT_FAVORITE = 3;
    private static final int WEIGHT_VIEW = 1;

    /** 缓存容量与候选池上限（离线计算有界化） */
    private static final int HOT_CACHE_SIZE = 50;
    private static final int CANDIDATE_POOL_SIZE = 300;
    private static final int BEHAVIOR_SCAN_LIMIT = 300;
    private static final int ACTIVE_USER_LIMIT = 200;
    private static final int ACTIVE_USER_DAYS = 30;

    /** 缓存过期（热榜 2h；个性化 2h；相似推荐 6h） */
    private static final Duration HOT_TTL = Duration.ofHours(2);
    private static final Duration USER_TTL = Duration.ofHours(2);
    private static final Duration SIMILAR_TTL = Duration.ofHours(6);

    private static final String KEY_HOT = "campus:market:rec:hot:goods";
    private static final String KEY_USER_PREFIX = "campus:market:rec:user:";
    private static final String KEY_SIMILAR_PREFIX = "campus:market:rec:similar:";

    private final GoodsMapper goodsMapper;
    private final GoodsTagMapper goodsTagMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    // ==================== REC-01/02 首页"猜你喜欢" ====================

    @Override
    public List<Long> recommendForUser(Long userId, int limit) {
        int size = normalizeLimit(limit);
        if (userId == null) {
            return hotGoodsIds(size);
        }
        String key = KEY_USER_PREFIX + userId;
        List<Long> cached = readIds(key);
        if (cached != null) {
            return cached.stream().limit(size).toList();
        }
        List<Long> computed = computeForUser(userId, Math.max(size, HOT_CACHE_SIZE));
        writeIds(key, computed, USER_TTL);
        return computed.stream().limit(size).toList();
    }

    @Override
    public List<Long> hotGoodsIds(int limit) {
        int size = normalizeLimit(limit);
        List<Long> cached = readIds(KEY_HOT);
        if (cached != null) {
            return cached.stream().limit(size).toList();
        }
        List<Long> hot = queryHotIds(HOT_CACHE_SIZE);
        writeIds(KEY_HOT, hot, HOT_TTL);
        return hot.stream().limit(size).toList();
    }

    // ==================== REC-03 详情页相似推荐 ====================

    @Override
    public List<Long> similarGoodsIds(Long goodsId, int limit) {
        int size = normalizeLimit(limit);
        if (goodsId == null) {
            return hotGoodsIds(size);
        }
        String key = KEY_SIMILAR_PREFIX + goodsId;
        List<Long> cached = readIds(key);
        if (cached != null) {
            return cached.stream().limit(size).toList();
        }
        List<Long> computed = computeSimilar(goodsId, Math.max(size, 12));
        writeIds(key, computed, SIMILAR_TTL);
        return computed.stream().limit(size).toList();
    }

    // ==================== 离线重算（定时任务入口） ====================

    @Override
    public int rebuildOfflineCache() {
        int recomputed = recomputeHeatScores();
        // 热榜强制重建（先删缓存再兜底读取回填）
        redisService.delete(KEY_HOT);
        List<Long> hot = hotGoodsIds(HOT_CACHE_SIZE);
        // 活跃用户个性化预热（近 30 天有行为的用户）
        List<Long> activeUsers = activeUserIds();
        int warmed = 0;
        for (Long userId : activeUsers) {
            try {
                List<Long> ids = computeForUser(userId, HOT_CACHE_SIZE);
                writeIds(KEY_USER_PREFIX + userId, ids, USER_TTL);
                warmed++;
            } catch (Exception e) {
                log.warn("用户推荐预热失败：userId={}, err={}", userId, e.getMessage());
            }
        }
        // 热门商品相似推荐预热（top 20，控制离线耗时）
        hot.stream().limit(20).forEach(goodsId ->
                writeIds(KEY_SIMILAR_PREFIX + goodsId, computeSimilar(goodsId, 12), SIMILAR_TTL));
        log.info("推荐离线重算完成：heat_score 覆盖 {} 个商品，热榜 {} 条，活跃用户预热 {} 人",
                recomputed, hot.size(), warmed);
        return recomputed;
    }

    /** 热度分重算（数据库设计文档 §3.0）：浏览×1 + 收藏×3 + 想要×5 */
    private int recomputeHeatScores() {
        int rows = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .ne(Goods::getStatus, Goods.STATUS_DELETED)
                .setSql("heat_score = view_count * 1 + favorite_count * 3 + want_count * 5"));
        return rows;
    }

    // ==================== 计算核心 ====================

    /** 个性化推荐：行为加权 → 标签/分类画像 → 候选打分（无行为回退热门） */
    private List<Long> computeForUser(Long userId, int size) {
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .eq(UserBehavior::getUserId, userId)
                .orderByDesc(UserBehavior::getId)
                .last("LIMIT " + BEHAVIOR_SCAN_LIMIT));
        if (behaviors.isEmpty()) {
            // 新用户冷启动：无行为 → 热门商品（PRD §6.6），排除自己发布的（验收 P3）
            return queryHotIds(size, userId);
        }

        // 行为权重聚合（同一商品多次浏览叠加，但封顶避免刷单主导）
        Map<Long, Integer> goodsWeight = new HashMap<>();
        Set<Long> interacted = new LinkedHashSet<>();
        for (UserBehavior behavior : behaviors) {
            int weight = switch (behavior.getBehavior() == null ? "" : behavior.getBehavior()) {
                case UserBehavior.BEHAVIOR_ORDER_DONE -> WEIGHT_ORDER_DONE;
                case UserBehavior.BEHAVIOR_FAVORITE -> WEIGHT_FAVORITE;
                default -> WEIGHT_VIEW;
            };
            interacted.add(behavior.getGoodsId());
            goodsWeight.merge(behavior.getGoodsId(), weight, (a, b) -> Math.min(a + b, 20));
        }

        Map<Long, Goods> interactedGoods = loadGoods(interacted);
        Map<Long, Integer> categoryScore = new HashMap<>();
        Map<Long, Integer> tagScore = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : goodsWeight.entrySet()) {
            Goods goods = interactedGoods.get(entry.getKey());
            if (goods == null) {
                continue;
            }
            int weight = Math.min(entry.getValue(), 20);
            if (goods.getCategoryId() != null) {
                categoryScore.merge(goods.getCategoryId(), weight, Integer::sum);
            }
        }
        Map<Long, List<Long>> interactedTags = loadTagMap(interacted);
        for (Map.Entry<Long, List<Long>> entry : interactedTags.entrySet()) {
            int weight = Math.min(goodsWeight.getOrDefault(entry.getKey(), 0), 20);
            if (weight <= 0) {
                continue;
            }
            for (Long tagId : entry.getValue()) {
                tagScore.merge(tagId, weight, Integer::sum);
            }
        }

        // 候选池：热度前 N 的在售商品，排除自身发布与已互动
        List<Goods> pool = goodsMapper.selectPage(
                        new Page<>(1, CANDIDATE_POOL_SIZE, false),
                        new LambdaQueryWrapper<Goods>()
                                .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                                .ne(Goods::getUserId, userId)
                                .orderByDesc(Goods::getHeatScore)
                                .orderByDesc(Goods::getId))
                .getRecords();
        if (pool.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> poolTags = loadTagMap(pool.stream().map(Goods::getId).collect(Collectors.toSet()));

        Map<Long, Long> scored = new HashMap<>();
        for (Goods goods : pool) {
            if (interacted.contains(goods.getId())) {
                continue;
            }
            long score = 0L;
            for (Long tagId : poolTags.getOrDefault(goods.getId(), List.of())) {
                score += tagScore.getOrDefault(tagId, 0) * 2L;
            }
            if (goods.getCategoryId() != null) {
                score += categoryScore.getOrDefault(goods.getCategoryId(), 0);
            }
            // 热度微调：避免行为画像完全缺失时推荐集中度过高
            score += Math.round((goods.getHeatScore() == null ? 0 : goods.getHeatScore()) * 0.05);
            scored.put(goods.getId(), score);
        }

        List<Long> ranked = scored.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .limit(size)
                .toList();
        if (ranked.size() >= size) {
            return ranked;
        }
        // 不足则用热榜补齐（排除已互动与自身发布，验收 P3）
        List<Long> filled = new ArrayList<>(ranked);
        for (Long hotId : queryHotIds(HOT_CACHE_SIZE, userId)) {
            if (filled.size() >= size) {
                break;
            }
            if (!filled.contains(hotId) && !interacted.contains(hotId)) {
                filled.add(hotId);
            }
        }
        return filled;
    }

    /** 相似推荐：同分类或共享标签（共标签数×2 + 同分类 1 + 热度微调），不足用热榜补齐 */
    private List<Long> computeSimilar(Long goodsId, int size) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
            return List.of();
        }
        List<Long> myTags = loadTagMap(Set.of(goodsId)).getOrDefault(goodsId, List.of());

        // 候选：同分类在售商品（有界）+ 共享标签商品
        Set<Long> candidateIds = new LinkedHashSet<>();
        goodsMapper.selectPage(new Page<>(1, 100, false), new LambdaQueryWrapper<Goods>()
                        .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                        .eq(Goods::getCategoryId, goods.getCategoryId())
                        .ne(Goods::getId, goodsId)
                        .orderByDesc(Goods::getHeatScore)
                        .orderByDesc(Goods::getId))
                .getRecords().forEach(item -> candidateIds.add(item.getId()));
        if (!myTags.isEmpty()) {
            goodsTagMapper.selectList(new LambdaQueryWrapper<GoodsTag>()
                            .in(GoodsTag::getTagId, myTags)
                            .orderByDesc(GoodsTag::getGoodsId))
                    .stream()
                    .map(GoodsTag::getGoodsId)
                    .filter(id -> !Objects.equals(id, goodsId))
                    .limit(200)
                    .forEach(candidateIds::add);
        }
        candidateIds.remove(goodsId);
        if (candidateIds.isEmpty()) {
            return excludeSelf(queryHotIds(size + 1), goodsId).stream().limit(size).toList();
        }

        Map<Long, Goods> candidateGoods = loadGoods(candidateIds);
        Map<Long, List<Long>> candidateTags = loadTagMap(candidateIds);
        Map<Long, Long> scored = new LinkedHashMap<>();
        for (Long candidateId : candidateIds) {
            Goods candidate = candidateGoods.get(candidateId);
            if (candidate == null || !Goods.STATUS_ON_SALE.equals(candidate.getStatus())) {
                continue;
            }
            long shared = candidateTags.getOrDefault(candidateId, List.of()).stream()
                    .filter(myTags::contains).count();
            long score = shared * 2L
                    + (Objects.equals(candidate.getCategoryId(), goods.getCategoryId()) ? 1L : 0L);
            score += Math.round((candidate.getHeatScore() == null ? 0 : candidate.getHeatScore()) * 0.05);
            scored.put(candidateId, score);
        }
        List<Long> ranked = scored.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .limit(size)
                .collect(Collectors.toCollection(ArrayList::new));
        if (ranked.size() < size) {
            for (Long hotId : queryHotIds(size + 1)) {
                if (ranked.size() >= size) {
                    break;
                }
                if (!Objects.equals(hotId, goodsId) && !ranked.contains(hotId)) {
                    ranked.add(hotId);
                }
            }
        }
        return ranked;
    }

    // ==================== 数据访问与缓存辅助 ====================

    /** 热度榜查询：heat_score → view_count → want_count → id 降序（PRD §6.6 热度由浏览+想要计算） */
    private List<Long> queryHotIds(int limit) {
        return queryHotIds(limit, null);
    }

    /** 带排除的热榜：excludeUserId 非空时排除该用户自己发布的商品（推荐语境不给用户推自己的闲置） */
    private List<Long> queryHotIds(int limit, Long excludeUserId) {
        return goodsMapper.selectPage(new Page<>(1, normalizeLimit(limit), false),
                        new LambdaQueryWrapper<Goods>()
                                .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                                .ne(excludeUserId != null, Goods::getUserId, excludeUserId)
                                .orderByDesc(Goods::getHeatScore)
                                .orderByDesc(Goods::getViewCount)
                                .orderByDesc(Goods::getWantCount)
                                .orderByDesc(Goods::getId))
                .getRecords().stream().map(Goods::getId).toList();
    }

    private List<Long> activeUserIds() {
        LocalDate since = LocalDate.now().minusDays(ACTIVE_USER_DAYS);
        return userBehaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                        .ge(UserBehavior::getBehaviorDate, since)
                        .orderByDesc(UserBehavior::getId)
                        .last("LIMIT " + ACTIVE_USER_LIMIT))
                .stream().map(UserBehavior::getUserId).distinct().toList();
    }

    private Map<Long, Goods> loadGoods(Set<Long> goodsIds) {
        List<Long> ids = goodsIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return goodsMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Goods::getId, goods -> goods, (a, b) -> a));
    }

    /** 商品 → 标签 ID 列表（一次批量查询，避免 N+1） */
    private Map<Long, List<Long>> loadTagMap(Set<Long> goodsIds) {
        List<Long> ids = goodsIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return goodsTagMapper.selectList(new LambdaQueryWrapper<GoodsTag>()
                        .in(GoodsTag::getGoodsId, ids))
                .stream()
                .collect(Collectors.groupingBy(GoodsTag::getGoodsId,
                        Collectors.mapping(GoodsTag::getTagId, Collectors.toList())));
    }

    private List<Long> excludeSelf(List<Long> ids, Long selfId) {
        return ids.stream().filter(id -> !Objects.equals(id, selfId)).toList();
    }

    private int normalizeLimit(int limit) {
        return Math.min(Math.max(limit, 1), HOT_CACHE_SIZE);
    }

    /** 读缓存：未命中返回 null（区别于空列表，"计算得出空结果"也要缓存）；Redis 异常降级为实时计算（验收 P1：读路径原无降级，Redis 宕机时推荐接口 500） */
    private List<Long> readIds(String key) {
        String json;
        try {
            json = redisService.get(key);
        } catch (Exception e) {
            log.warn("推荐缓存读取失败，降级为实时计算：key={}, err={}", key, e.getMessage());
            return null;
        }
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("推荐缓存反序列化失败，按未命中处理：key={}, err={}", key, e.getMessage());
            redisService.delete(key);
            return null;
        }
    }

    private void writeIds(String key, List<Long> ids, Duration ttl) {
        try {
            redisService.set(key, objectMapper.writeValueAsString(ids), ttl);
        } catch (Exception e) {
            log.warn("推荐缓存写入失败：key={}, err={}", key, e.getMessage());
        }
    }
}
