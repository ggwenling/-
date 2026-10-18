package com.campus.market.task;

import com.campus.market.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时扫描定时任务（PRD ORD-03）：
 * WAIT_CONFIRM 超 created_at+48h、SCHEDULED 超 confirmed_at+15d →
 * CANCELLED(cancelled_by=TIMEOUT) + 商品解锁 + 卖方信用 -2（CANCEL_TIMEOUT）+ 双方通知。
 * 每分钟执行一次（数据库设计文档 §3.11 超时扫描约定）。
 * dev 验证手段：把库中订单 created_at/confirmed_at 改到窗口之外等一分钟，
 * 或调用 POST /api/orders/timeout-scan/trigger 手动触发（仅 dev profile）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderService orderService;

    /** 每分钟扫描一次超时订单；首轮延迟 30s 避免与启动竞争 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scanTimeoutOrders() {
        try {
            int cancelled = orderService.autoCancelTimeoutOrders();
            if (cancelled > 0) {
                log.info("订单超时扫描：本轮自动取消 {} 笔", cancelled);
            }
        } catch (Exception e) {
            // 定时任务不得因单次失败中断后续调度（与 RecommendTask / OrphanScanTask 口径一致）
            log.error("订单超时扫描失败：{}", e.getMessage(), e);
        }
    }
}
