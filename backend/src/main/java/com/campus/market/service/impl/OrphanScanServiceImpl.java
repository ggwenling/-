package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OperationLog;
import com.campus.market.mapper.AdminMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.NotificationMapper;
import com.campus.market.mapper.OfferMapper;
import com.campus.market.mapper.OperationLogMapper;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.SensitiveWordMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.OrphanScanService;
import com.campus.market.vo.OrphanScanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 孤儿数据扫描实现（数据库设计文档 T3 多态逻辑外键兜底 + T4 审计引用；PRD §7 多态关联）。
 * <p>
 * 口径：只统计 + WARN 告警，绝不删除/修改数据。
 * 实现取舍——<b>大表</b>（report / credit_log / goods / want_post / swap_post）与目标存在性校验
 * 走 ReportMapper 上的 @Select NOT EXISTS 计数 SQL，一次一条 SQL 返回计数，不把全表拉进内存；
 * <b>小表</b> notification / operation_log 数据量小，直接 selectList 后按 refType/targetType 分组
 * 批量 selectBatchIds 判定，分组后同类只查一次，无 N+1。
 * <p>
 * notification.ref_type 白名单据现有代码实写值：现有 push 调用仅写入 ORDER（CreditLog.REF_TYPE_ORDER），
 * 另 NotificationService 契约声明可跳转 GOODS/REPORT，故按 {ORDER,GOODS,REPORT} 扫描；
 * operation_log.target_type 按 OperationLog 契约 {GOODS,USER,OFFER,ADMIN,WORD,REPORT} 扫描；
 * 白名单外的取值不臆测，跳过（debug 记录）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanScanServiceImpl implements OrphanScanService {

    /** notification.ref_type 白名单 → 目标表（见类注释的据实口径） */
    private static final Set<String> NOTIFICATION_REF_TYPES = Set.of("ORDER", "GOODS", "REPORT");
    /** operation_log.target_type 白名单 → 目标表 */
    private static final Set<String> OPERATION_LOG_TARGET_TYPES =
            Set.of("GOODS", "USER", "OFFER", "ADMIN", "WORD", "REPORT");

    private final ReportMapper reportMapper;
    private final NotificationMapper notificationMapper;
    private final OperationLogMapper operationLogMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final OfferMapper offerMapper;
    private final AdminMapper adminMapper;
    private final SensitiveWordMapper sensitiveWordMapper;

    @Override
    public OrphanScanVO scan() {
        OrphanScanVO vo = new OrphanScanVO();

        // 1. report.target_id 多态引用（内容类区分"不存在"与"已软删"）
        vo.setReportGoodsMissing(nz(reportMapper.countReportGoodsMissing()));
        vo.setReportGoodsDeleted(nz(reportMapper.countReportGoodsDeleted()));
        vo.setReportWantMissing(nz(reportMapper.countReportWantMissing()));
        vo.setReportWantDeleted(nz(reportMapper.countReportWantDeleted()));
        vo.setReportSwapMissing(nz(reportMapper.countReportSwapMissing()));
        vo.setReportSwapDeleted(nz(reportMapper.countReportSwapDeleted()));
        vo.setReportUserMissing(nz(reportMapper.countReportUserMissing()));

        // 2. credit_log.ref_type/ref_id（ORDER / REPORT）
        vo.setCreditOrderMissing(nz(reportMapper.countCreditOrderMissing()));
        vo.setCreditReportMissing(nz(reportMapper.countCreditReportMissing()));

        // 3. notification.ref_type/ref_id（小表，内存判定）
        vo.setNotificationOrphan(scanNotificationOrphans());

        // 4. operation_log.target_type/target_id（小表，内存判定）
        vo.setOperationLogOrphan(scanOperationLogOrphans());

        // 5. 内容类 deleted_by 审计引用（T4）
        vo.setGoodsDeletedByUserOrphan(nz(reportMapper.countGoodsDeletedByUserOrphan()));
        vo.setGoodsDeletedByAdminOrphan(nz(reportMapper.countGoodsDeletedByAdminOrphan()));
        vo.setWantDeletedByUserOrphan(nz(reportMapper.countWantDeletedByUserOrphan()));
        vo.setWantDeletedByAdminOrphan(nz(reportMapper.countWantDeletedByAdminOrphan()));
        vo.setSwapDeletedByUserOrphan(nz(reportMapper.countSwapDeletedByUserOrphan()));
        vo.setSwapDeletedByAdminOrphan(nz(reportMapper.countSwapDeletedByAdminOrphan()));

        long total = vo.getReportGoodsMissing() + vo.getReportGoodsDeleted()
                + vo.getReportWantMissing() + vo.getReportWantDeleted()
                + vo.getReportSwapMissing() + vo.getReportSwapDeleted() + vo.getReportUserMissing()
                + vo.getCreditOrderMissing() + vo.getCreditReportMissing()
                + vo.getNotificationOrphan() + vo.getOperationLogOrphan()
                + vo.getGoodsDeletedByUserOrphan() + vo.getGoodsDeletedByAdminOrphan()
                + vo.getWantDeletedByUserOrphan() + vo.getWantDeletedByAdminOrphan()
                + vo.getSwapDeletedByUserOrphan() + vo.getSwapDeletedByAdminOrphan();
        vo.setTotal(total);

        if (total > 0) {
            log.warn("孤儿数据扫描发现失效引用，合计 {} 行（只统计不删除）：{}", total, vo);
        } else {
            log.info("孤儿数据扫描完成：未发现失效引用");
        }
        return vo;
    }

    /** notification 多态跳转引用失效计数（按 refType 分组批量判定） */
    private long scanNotificationOrphans() {
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .isNotNull(Notification::getRefType)
                .isNotNull(Notification::getRefId));
        if (list.isEmpty()) {
            return 0;
        }
        Map<String, Set<Long>> grouped = new HashMap<>();
        for (Notification n : list) {
            if (!NOTIFICATION_REF_TYPES.contains(n.getRefType())) {
                log.debug("孤儿扫描：忽略未知 notification.ref_type={}", n.getRefType());
                continue;
            }
            grouped.computeIfAbsent(n.getRefType(), k -> new HashSet<>()).add(n.getRefId());
        }
        long orphan = 0;
        for (Map.Entry<String, Set<Long>> entry : grouped.entrySet()) {
            BaseMapper<?> target = switch (entry.getKey()) {
                case "ORDER" -> orderInfoMapper;
                case "GOODS" -> goodsMapper;
                default -> reportMapper;
            };
            orphan += missingCount(target, entry.getValue());
        }
        return orphan;
    }

    /** operation_log 多态引用失效计数（按 targetType 分组批量判定） */
    private long scanOperationLogOrphans() {
        List<OperationLog> list = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .isNotNull(OperationLog::getTargetType)
                .isNotNull(OperationLog::getTargetId));
        if (list.isEmpty()) {
            return 0;
        }
        Map<String, Set<Long>> grouped = new HashMap<>();
        for (OperationLog oplog : list) {
            if (!OPERATION_LOG_TARGET_TYPES.contains(oplog.getTargetType())) {
                log.debug("孤儿扫描：忽略未知 operation_log.target_type={}", oplog.getTargetType());
                continue;
            }
            grouped.computeIfAbsent(oplog.getTargetType(), k -> new HashSet<>()).add(oplog.getTargetId());
        }
        long orphan = 0;
        for (Map.Entry<String, Set<Long>> entry : grouped.entrySet()) {
            BaseMapper<?> target = switch (entry.getKey()) {
                case "GOODS" -> goodsMapper;
                case "USER" -> userMapper;
                case "OFFER" -> offerMapper;
                case "ADMIN" -> adminMapper;
                case "WORD" -> sensitiveWordMapper;
                default -> reportMapper;
            };
            orphan += missingCount(target, entry.getValue());
        }
        return orphan;
    }

    /** 目标 ID 集合中在当前表中不存在的数量（selectBatchIds 只返回存在的行，差集即孤儿） */
    private long missingCount(BaseMapper<?> mapper, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        int found = mapper.selectBatchIds(ids).size();
        return (long) ids.size() - found;
    }

    private long nz(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
