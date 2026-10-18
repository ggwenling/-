package com.campus.market.task;

import com.campus.market.service.RecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推荐离线计算定时任务（PRD REC-03 / §6.6：推荐结果由 Java 定时任务计算并写入 Redis，接口直读缓存）。
 * 每小时执行：重算 goods.heat_score（浏览×1 + 收藏×3 + 想要×5，数据库设计文档 §3.0）+
 * 重建热榜缓存 + 为近 30 天活跃用户预热个性化推荐 + 热门商品相似推荐预热。
 * 首轮延迟 60s（等应用启动与库连接就绪）。dev 验证：POST /api/recommend/rebuild/trigger。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendTask {

    private final RecommendService recommendService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void rebuildRecommendCache() {
        try {
            int goodsCount = recommendService.rebuildOfflineCache();
            log.info("推荐缓存重算完成：heat_score 覆盖 {} 个在售/下架商品", goodsCount);
        } catch (Exception e) {
            // 定时任务不得因单次失败中断后续调度
            log.error("推荐缓存重算失败：{}", e.getMessage(), e);
        }
    }
}
