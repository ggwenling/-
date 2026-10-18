package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 举报工单 Mapper（PRD RPT-01/RPT-02；ADM-04 处置端复用）。
 * 除基础 CRUD 外，集中承载 T3 孤儿数据扫描的计数 SQL（NOT EXISTS，一次一条 SQL 返回计数，
 * 避免把全表拉进内存）。孤儿扫描只统计 + 告警，不做任何删除（数据库设计文档 T3 多态逻辑外键兜底）。
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    // ==================== report.target_id 多态引用（按 target_type 分表校验） ====================
    // 内容类：把"目标不存在"与"目标已软删(DELETED)"分开计数（T3 兜底口径）。

    /** GOODS 目标不存在 */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'GOODS' "
            + "AND NOT EXISTS (SELECT 1 FROM goods g WHERE g.id = r.target_id)")
    Long countReportGoodsMissing();

    /** GOODS 目标存在但已软删 */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'GOODS' "
            + "AND EXISTS (SELECT 1 FROM goods g WHERE g.id = r.target_id AND g.status = 'DELETED')")
    Long countReportGoodsDeleted();

    /** WANT 目标不存在 */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'WANT' "
            + "AND NOT EXISTS (SELECT 1 FROM want_post w WHERE w.id = r.target_id)")
    Long countReportWantMissing();

    /** WANT 目标存在但已软删 */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'WANT' "
            + "AND EXISTS (SELECT 1 FROM want_post w WHERE w.id = r.target_id AND w.status = 'DELETED')")
    Long countReportWantDeleted();

    /** SWAP 目标不存在 */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'SWAP' "
            + "AND NOT EXISTS (SELECT 1 FROM swap_post s WHERE s.id = r.target_id)")
    Long countReportSwapMissing();

    /** SWAP 目标存在但已软删 */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'SWAP' "
            + "AND EXISTS (SELECT 1 FROM swap_post s WHERE s.id = r.target_id AND s.status = 'DELETED')")
    Long countReportSwapDeleted();

    /** USER 目标不存在（用户无软删，只判存在性） */
    @Select("SELECT COUNT(*) FROM report r WHERE r.target_type = 'USER' "
            + "AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.id = r.target_id)")
    Long countReportUserMissing();

    // ==================== credit_log.ref_type/ref_id 多态引用 ====================

    /** 信用流水引用的 ORDER 不存在 */
    @Select("SELECT COUNT(*) FROM credit_log c WHERE c.ref_type = 'ORDER' AND c.ref_id IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM order_info o WHERE o.id = c.ref_id)")
    Long countCreditOrderMissing();

    /** 信用流水引用的 REPORT 不存在 */
    @Select("SELECT COUNT(*) FROM credit_log c WHERE c.ref_type = 'REPORT' AND c.ref_id IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM report r WHERE r.id = c.ref_id)")
    Long countCreditReportMissing();

    // ==================== 内容类 deleted_by 审计引用（T4） ====================

    /** goods 用户自删但 deleted_by 对应用户不存在 */
    @Select("SELECT COUNT(*) FROM goods WHERE deleted_by_type = 'USER' AND deleted_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.id = goods.deleted_by)")
    Long countGoodsDeletedByUserOrphan();

    /** goods 管理员删除但 deleted_by 对应 admin 不存在 */
    @Select("SELECT COUNT(*) FROM goods WHERE deleted_by_type = 'ADMIN' AND deleted_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM admin a WHERE a.id = goods.deleted_by)")
    Long countGoodsDeletedByAdminOrphan();

    /** want_post 用户自删但 deleted_by 对应用户不存在 */
    @Select("SELECT COUNT(*) FROM want_post WHERE deleted_by_type = 'USER' AND deleted_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.id = want_post.deleted_by)")
    Long countWantDeletedByUserOrphan();

    /** want_post 管理员删除但 deleted_by 对应 admin 不存在 */
    @Select("SELECT COUNT(*) FROM want_post WHERE deleted_by_type = 'ADMIN' AND deleted_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM admin a WHERE a.id = want_post.deleted_by)")
    Long countWantDeletedByAdminOrphan();

    /** swap_post 用户自删但 deleted_by 对应用户不存在 */
    @Select("SELECT COUNT(*) FROM swap_post WHERE deleted_by_type = 'USER' AND deleted_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.id = swap_post.deleted_by)")
    Long countSwapDeletedByUserOrphan();

    /** swap_post 管理员删除但 deleted_by 对应 admin 不存在 */
    @Select("SELECT COUNT(*) FROM swap_post WHERE deleted_by_type = 'ADMIN' AND deleted_by IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM admin a WHERE a.id = swap_post.deleted_by)")
    Long countSwapDeletedByAdminOrphan();
}
