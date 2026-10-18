package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 后台操作日志（PRD ADM-09 / 数据库设计文档 §3.24）：审计快照（admin_username 写入固化-T5），
 * 多态引用保持逻辑外键（T3）；只写不改。
 */
@Getter
@Setter
@TableName("operation_log")
public class OperationLog {

    /** 动作枚举（PRD ADM-09）：含改密/下架/恢复/删除/封禁/解封/信用调整/词库/举报处置/轮播公告等 */
    public static final String ACTION_RESET_PASSWORD = "ADMIN_RESET_PASSWORD";
    public static final String ACTION_ADMIN_CREATE = "ADMIN_CREATE";
    public static final String ACTION_ADMIN_UPDATE = "ADMIN_UPDATE";
    public static final String ACTION_ADMIN_DISABLE = "ADMIN_DISABLE";
    public static final String ACTION_ADMIN_ENABLE = "ADMIN_ENABLE";
    public static final String ACTION_GOODS_TAKE_DOWN = "GOODS_TAKE_DOWN";
    public static final String ACTION_GOODS_RESTORE = "GOODS_RESTORE";
    public static final String ACTION_GOODS_DELETE = "GOODS_DELETE";
    public static final String ACTION_USER_BAN = "USER_BAN";
    public static final String ACTION_USER_UNBAN = "USER_UNBAN";
    public static final String ACTION_CREDIT_ADJUST = "CREDIT_ADJUST";
    public static final String ACTION_WORD_ADD = "WORD_ADD";
    public static final String ACTION_WORD_DELETE = "WORD_DELETE";
    public static final String ACTION_WORD_IMPORT = "WORD_IMPORT";
    public static final String ACTION_REPORT_HANDLE = "REPORT_HANDLE";
    public static final String ACTION_CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String ACTION_CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String ACTION_CATEGORY_DELETE = "CATEGORY_DELETE";
    public static final String ACTION_TAG_CREATE = "TAG_CREATE";
    public static final String ACTION_TAG_UPDATE = "TAG_UPDATE";
    public static final String ACTION_TAG_DELETE = "TAG_DELETE";
    public static final String ACTION_BANNER_CREATE = "BANNER_CREATE";
    public static final String ACTION_BANNER_UPDATE = "BANNER_UPDATE";
    public static final String ACTION_BANNER_DELETE = "BANNER_DELETE";
    public static final String ACTION_NOTICE_CREATE = "NOTICE_CREATE";
    public static final String ACTION_NOTICE_UPDATE = "NOTICE_UPDATE";
    public static final String ACTION_NOTICE_PUBLISH = "NOTICE_PUBLISH";
    public static final String ACTION_NOTICE_OFFLINE = "NOTICE_OFFLINE";
    public static final String ACTION_NOTICE_DELETE = "NOTICE_DELETE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员 ID（物理外键 fk_oplog_admin → admin.id） */
    private Long adminId;

    /** 审计快照：写入时固化，admin 后续改名/删除不影响历史（T5） */
    private String adminUsername;

    /** 动作（ACTION_* 常量） */
    private String action;

    /** 操作对象类型（多态逻辑外键，如 GOODS/USER/OFFER/ADMIN/WORD/REPORT） */
    private String targetType;

    /** 操作对象 ID */
    private Long targetId;

    /** 参数摘要与理由（≤500） */
    private String detail;

    /** 来源 IP（兼容 IPv6） */
    private String ip;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
