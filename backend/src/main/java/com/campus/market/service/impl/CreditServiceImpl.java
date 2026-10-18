package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Notification;
import com.campus.market.entity.User;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.CreditService;
import com.campus.market.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信用分规则引擎实现（PRD CRD-01 / §5.6 / 数据库设计文档 §3.16、T5）。
 * 一致性要点：
 * 1. SELECT ... FOR UPDATE 锁 user 行后再算 before/after，防并发同用户加减分读脏；
 * 2. credit_log 插入与 user.credit_score 更新同事务（credit_log 是唯一事实来源）；
 * 3. 同一 reason+refType+refId 的重复事件幂等跳过（重复任务不得重复加减分，PRD §5.6）；
 * 4. clamp 0~150，阈值常量来自 CreditProperties。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditLogMapper creditLogMapper;
    private final UserMapper userMapper;
    private final CreditProperties creditProperties;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void addCredit(Long userId, String reason, String refType, Long refId) {
        addCredit(userId, reason, resolveStandardChange(reason), refType, refId, null, null);
    }

    @Override
    @Transactional
    public void addCredit(Long userId, String reason, int scoreChange, String refType, Long refId,
                          String remark, Long operatorId) {
        // 幂等事件键（PRD §5.6）：同用户同原因同来源单据只记一次（ref 为空的手动调分不受限）
        if (refType != null && refId != null) {
            Long exists = creditLogMapper.selectCount(new LambdaQueryWrapper<CreditLog>()
                    .eq(CreditLog::getUserId, userId)
                    .eq(CreditLog::getReason, reason)
                    .eq(CreditLog::getRefType, refType)
                    .eq(CreditLog::getRefId, refId));
            if (exists != null && exists > 0) {
                log.info("信用事件重复，幂等跳过：userId={}, reason={}, ref={}/{}", userId, reason, refType, refId);
                return;
            }
        }

        // 行锁读当前分值（防并发同用户变动读脏 before_score）
        User user = creditLogMapper.selectUserForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在，无法变更信用分");
        }
        int before = user.getCreditScore() == null ? 0 : user.getCreditScore();
        int after = Math.max(0, Math.min(creditProperties.getMaxScore(), before + scoreChange));

        CreditLog creditLog = new CreditLog();
        creditLog.setUserId(userId);
        creditLog.setScoreChange(scoreChange);
        creditLog.setBeforeScore(before);
        creditLog.setAfterScore(after);
        creditLog.setReason(reason);
        creditLog.setRefType(refType);
        creditLog.setRefId(refId);
        creditLog.setRemark(remark);
        creditLog.setOperatorId(operatorId);
        creditLogMapper.insert(creditLog);

        User patch = new User();
        patch.setId(userId);
        patch.setCreditScore(after);
        userMapper.updateById(patch);

        // CREDIT 通知（PRD §6.4：信用变动生成站内通知）
        notificationService.push(userId, Notification.TYPE_CREDIT, "信用分变动",
                String.format("您的信用分 %d → %d（%s）", before, after, reasonLabel(reason)),
                refType, refId);
        log.info("信用分变更：userId={}, {} {} → {}（{}）", userId, reason, before, after, scoreChange);
    }

    /** PRD 标准原因 → 分值映射（CRD-01，常量来自 CreditProperties） */
    private int resolveStandardChange(String reason) {
        return switch (reason) {
            case CreditLog.REASON_ORDER_COMPLETE -> creditProperties.getOrderCompleteBonus();
            case CreditLog.REASON_CANCEL_TIMEOUT -> -creditProperties.getOrderTimeoutPenalty();
            case CreditLog.REASON_REPORT_VALID -> -creditProperties.getViolationReportPenalty();
            case CreditLog.REASON_MALICIOUS_REPORT -> -creditProperties.getMaliciousReportPenalty();
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "未知信用变动原因：" + reason);
        };
    }

    /** 通知文案用原因中文名 */
    private String reasonLabel(String reason) {
        return switch (reason) {
            case CreditLog.REASON_ORDER_COMPLETE -> "订单完成奖励";
            case CreditLog.REASON_CANCEL_TIMEOUT -> "订单超时未确认";
            case CreditLog.REASON_REPORT_VALID -> "举报属实";
            case CreditLog.REASON_MALICIOUS_REPORT -> "恶意举报";
            case CreditLog.REASON_ADMIN_ADJUST -> "管理员调整";
            default -> reason;
        };
    }
}
