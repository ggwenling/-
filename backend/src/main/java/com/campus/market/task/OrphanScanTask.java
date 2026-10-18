package com.campus.market.task;

import com.campus.market.service.OrphanScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿数据扫描定时任务（数据库设计文档 T3 多态逻辑外键兜底 + T4 审计引用）。
 * 每日 03:30 执行一次，扫描 report/credit_log/notification/operation_log/deleted_by 等逻辑外键失效引用，
 * 只统计 + WARN 告警，绝不删除数据。异常 catch 后记 error，不得中断后续调度
 * （参考 OrderTimeoutTask / RecommendTask 写法）。
 * dev 手动触发：POST /api/reports/orphan-scan/trigger（仅 dev profile）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanScanTask {

    private final OrphanScanService orphanScanService;

    /** 每日 03:30 全量扫描一次（低峰期执行，避免与业务争用） */
    @Scheduled(cron = "0 30 3 * * ?")
    public void scanOrphans() {
        try {
            orphanScanService.scan();
        } catch (Exception e) {
            // 定时任务不得因单次失败中断后续调度
            log.error("孤儿数据扫描失败：{}", e.getMessage(), e);
        }
    }
}
