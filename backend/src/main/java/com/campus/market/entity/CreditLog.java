package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 信用分流水实体，对应表 credit_log（PRD CRD-01 / 数据库设计文档 §3.16）。
 * credit_log 是信用分的唯一事实来源，user.credit_score 为当前值冗余，同事务更新（T5）；
 * ref_type/ref_id 为多态逻辑外键（T3），孤儿扫描归 M7。
 */
@Getter
@Setter
@TableName("credit_log")
public class CreditLog {

    /** 信用变动原因（reason，数据库设计文档 §3.16 枚举口径） */
    public static final String REASON_ORDER_COMPLETE = "ORDER_COMPLETE";
    public static final String REASON_REPORT_VALID = "REPORT_VALID";
    public static final String REASON_CANCEL_TIMEOUT = "CANCEL_TIMEOUT";
    public static final String REASON_MALICIOUS_REPORT = "MALICIOUS_REPORT";
    public static final String REASON_ADMIN_ADJUST = "ADMIN_ADJUST";

    /** 多态关联类型（ref_type） */
    public static final String REF_TYPE_ORDER = "ORDER";
    public static final String REF_TYPE_REPORT = "REPORT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被变动用户 ID（FK user.id） */
    private Long userId;

    /** 变动值，正加负减（T2：避开保留字 change） */
    private Integer scoreChange;

    /** 变动前分值 */
    private Integer beforeScore;

    /** 变动后分值 */
    private Integer afterScore;

    /** ORDER_COMPLETE / REPORT_VALID / CANCEL_TIMEOUT / MALICIOUS_REPORT / ADMIN_ADJUST */
    private String reason;

    /** 多态关联类型：ORDER / REPORT（逻辑外键，T3） */
    private String refType;

    /** 关联单据 ID */
    private Long refId;

    /** 备注（ADMIN_ADJUST 必填） */
    private String remark;

    /** 操作管理员 ID（仅 ADMIN_ADJUST，超管专属，FK admin.id） */
    private Long operatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
