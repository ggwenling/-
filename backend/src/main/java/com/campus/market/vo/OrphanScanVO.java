package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 孤儿数据扫描结果（数据库设计文档 T3 多态逻辑外键兜底 + T4 审计引用）。
 * 只统计与告警，不含任何删除动作；total 为各类明细计数之和（影响行数合计）。
 * 内容类举报目标（GOODS/WANT/SWAP）区分"目标不存在"与"目标已软删(DELETED)"。
 */
@Getter
@Setter
public class OrphanScanVO {

    /** 影响行数合计（下列所有明细计数之和） */
    private long total;

    // ---------- report.target_id 多态引用 ----------
    private long reportGoodsMissing;
    private long reportGoodsDeleted;
    private long reportWantMissing;
    private long reportWantDeleted;
    private long reportSwapMissing;
    private long reportSwapDeleted;
    private long reportUserMissing;

    // ---------- credit_log.ref_type/ref_id ----------
    private long creditOrderMissing;
    private long creditReportMissing;

    // ---------- notification.ref_type/ref_id（内存判定） ----------
    private long notificationOrphan;

    // ---------- operation_log.target_type/target_id（内存判定） ----------
    private long operationLogOrphan;

    // ---------- 内容类 deleted_by 审计引用 ----------
    private long goodsDeletedByUserOrphan;
    private long goodsDeletedByAdminOrphan;
    private long wantDeletedByUserOrphan;
    private long wantDeletedByAdminOrphan;
    private long swapDeletedByUserOrphan;
    private long swapDeletedByAdminOrphan;
}
