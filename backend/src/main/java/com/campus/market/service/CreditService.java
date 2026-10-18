package com.campus.market.service;

/**
 * 信用分规则引擎（PRD CRD-01 / §5.6）：credit_log 流水 + user.credit_score 同事务更新（clamp 0~150），
 * 并推送 CREDIT 站内通知；重复事件（同 reason+ref）幂等不重复加减分。
 * 分值增减常量取自 {@link com.campus.market.config.CreditProperties}。
 */
public interface CreditService {

    /**
     * 按 PRD 标准原因加减信用分（CRD-01）。分值由 CreditProperties 按原因映射：
     * ORDER_COMPLETE=+orderCompleteBonus、CANCEL_TIMEOUT=-orderTimeoutPenalty、
     * REPORT_VALID=-violationReportPenalty、MALICIOUS_REPORT=-maliciousReportPenalty。
     *
     * @param userId  被变动用户 ID
     * @param reason  CreditLog.REASON_*（标准原因）
     * @param refType 来源单据类型（CreditLog.REF_TYPE_*，可空）
     * @param refId   来源单据 ID（可空；非空时作为幂等事件键）
     */
    void addCredit(Long userId, String reason, String refType, Long refId);

    /**
     * 通用加减分（预留给 M6 超管手动调分 ADMIN_ADJUST）：同事务写 credit_log（before/after）+
     * 更新 user.credit_score（clamp 0~150）+ 推送 CREDIT 通知。
     *
     * @param userId      被变动用户 ID
     * @param reason      CreditLog.REASON_*
     * @param scoreChange 变动值，正加负减
     * @param refType     来源单据类型（可空）
     * @param refId       来源单据 ID（可空；非空时作为幂等事件键）
     * @param remark      备注（ADMIN_ADJUST 必填）
     * @param operatorId  操作管理员 ID（仅 ADMIN_ADJUST，其余传 null）
     */
    void addCredit(Long userId, String reason, int scoreChange, String refType, Long refId,
                   String remark, Long operatorId);
}
