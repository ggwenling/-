package com.campus.market.service;

import com.campus.market.vo.OrphanScanVO;

/**
 * 孤儿数据扫描服务（数据库设计文档 T3 多态逻辑外键兜底 + T4 审计引用）。
 * 扫描各类逻辑外键失效引用，<b>只统计 + WARN 日志，绝不删除/修改任何数据</b>。
 * 由 OrphanScanTask 每日 03:30 调度，dev 可经 POST /api/reports/orphan-scan/trigger 手动触发。
 */
public interface OrphanScanService {

    /** 执行一次全量孤儿扫描，返回各类明细计数与影响行数合计 */
    OrphanScanVO scan();
}
