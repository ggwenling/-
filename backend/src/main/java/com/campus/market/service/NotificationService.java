package com.campus.market.service;

/**
 * 站内通知写入服务（PRD NTF-01）。M3/M4 的共享契约：
 * 订单状态变更（M3）、处置结果/信用变动（M3/M6/M7）等场景统一经此写入通知。
 * 查询、已读、红点接口由 M4 的 NotificationController 提供。
 */
public interface NotificationService {

    /**
     * 写入一条站内通知。
     *
     * @param userId  接收人 ID
     * @param type    Notification.TYPE_*（ORDER/AUDIT/REPORT/CREDIT/SYSTEM）
     * @param title   标题
     * @param content 内容
     * @param refType 跳转类型（ORDER/GOODS/REPORT 等，可空）
     * @param refId   跳转对象 ID（可空）
     */
    void push(Long userId, String type, String title, String content, String refType, Long refId);
}
