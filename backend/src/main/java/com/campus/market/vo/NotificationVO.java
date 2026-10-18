package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站内通知响应体（PRD NTF-01，GET /api/notifications）：
 * type 图标/文案映射与跳转由前端根据 refType/refId 处理。
 */
@Getter
@Setter
public class NotificationVO {

    private Long id;

    /** ORDER / AUDIT / REPORT / CREDIT / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 跳转类型（ORDER/GOODS/REPORT 等，可空） */
    private String refType;

    private Long refId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
