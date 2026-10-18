package com.campus.market.service;

import java.util.List;

/**
 * 推荐服务（PRD REC-01~03 / §6.6）。
 * 算法口径（PRD §6.6）：行为权重「成交 5 / 收藏 3 / 浏览 1」，匹配维度为商品标签 + 分类；
 * 新用户无行为时回退热门商品（热度由浏览量 + 想要数计算，PRD §6.6）。
 * 计算与缓存口径：离线由 {@code RecommendTask} 定时任务重算（热度分每小时、热榜与活跃用户推荐每小时），
 * 结果写入 Redis 由接口直接读取；接口 miss 时按需计算并回填缓存（保证冷启动/新用户可用），
 * 单次结果分页上限受 limit 约束。
 */
public interface RecommendService {

    /** 首页"猜你喜欢"：当前用户个性化推荐商品 ID（按推荐分降序）；无行为时回退热门 */
    List<Long> recommendForUser(Long userId, int limit);

    /** 热度榜：热门商品 ID（heat_score 降序，无行为新用户兜底共用） */
    List<Long> hotGoodsIds(int limit);

    /** 商品详情"相似推荐"：同分类 + 共享标签的商品 ID（排除自身），不足时用热门补齐 */
    List<Long> similarGoodsIds(Long goodsId, int limit);

    /**
     * 【定时任务入口】离线重算：①重算 goods.heat_score（浏览×1 + 收藏×3 + 想要×5）；
     * ②重建热榜缓存；③为近 30 天有行为的活跃用户预计算个性化推荐缓存。
     *
     * @return 参与重算的商品数
     */
    int rebuildOfflineCache();
}
