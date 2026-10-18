package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站内通知实体，对应表 notification（PRD NTF-01 / 数据库设计文档 §3.20）。
 * 本实体为 M3/M4 的共享契约：M4 提供查询/已读接口，M3 等模块经
 * {@link com.campus.market.service.NotificationService#push} 写入。
 * ref_type/ref_id 为跳转多态逻辑外键（T3），孤儿扫描归 M7。
 */
@Getter
@Setter
@TableName("notification")
public class Notification {

    /** 通知类型（PRD NTF-01） */
    public static final String TYPE_ORDER = "ORDER";
    public static final String TYPE_AUDIT = "AUDIT";
    public static final String TYPE_REPORT = "REPORT";
    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_SYSTEM = "SYSTEM";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 ID */
    private Long userId;

    /** ORDER / AUDIT / REPORT / CREDIT / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 跳转类型：ORDER / GOODS / REPORT 等 */
    private String refType;

    private Long refId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
