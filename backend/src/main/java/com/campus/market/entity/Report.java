package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 举报工单实体，对应表 report（PRD RPT-01 提交举报 / RPT-02 进度查询、ADM-04 后台处置、
 * 数据库设计文档 §3.21 / T3）。
 * target_type/target_id 为多态逻辑外键（GOODS→goods / WANT→want_post / SWAP→swap_post / USER→user），
 * 不做物理外键，应用层校验 + T3 孤儿扫描兜底（M7 OrphanScanService 覆盖）。
 * reporter_id / handler_id 为物理外键（→user.id / →admin.id）。
 * images 为 JSON 列，T10 维持 JSON 类型不建关联表；实体侧以 String 承接，由 Jackson 序列化（PRD T10）。
 */
@Getter
@Setter
@TableName("report")
public class Report {

    /** 被举报对象类型白名单：商品 / 求购帖 / 交换帖 / 用户 */
    public static final String TARGET_GOODS = "GOODS";
    public static final String TARGET_WANT = "WANT";
    public static final String TARGET_SWAP = "SWAP";
    public static final String TARGET_USER = "USER";

    /** 举报类型白名单（PRD RPT-01） */
    public static final String TYPE_VIOLATION = "VIOLATION";
    public static final String TYPE_FRAUD = "FRAUD";
    public static final String TYPE_COUNTERFEIT = "COUNTERFEIT";
    public static final String TYPE_OTHER = "OTHER";

    /** 工单状态：0 待处理 / 1 已处置 / 2 已驳回（PRD ADM-04） */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_HANDLED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 举报人 ID（FK user.id） */
    private Long reporterId;

    /** 被举报对象类型：GOODS / WANT / SWAP / USER（多态逻辑外键，T3） */
    private String targetType;

    /** 被举报对象 ID（多态逻辑外键，T3） */
    private Long targetId;

    /** 举报类型：VIOLATION / FRAUD / COUNTERFEIT / OTHER */
    private String reportType;

    /** 补充描述，≤500 字 */
    private String description;

    /** 举报截图 URL 数组的 JSON 文本（最多 3 张，T10），读出时反序列化为 List&lt;String&gt; */
    private String images;

    /** 0 待处理 / 1 已处置 / 2 已驳回 */
    private Integer status;

    /** 处置说明（M6 后台处置写入，M7 只读） */
    private String result;

    /** 处置管理员 ID（FK admin.id，M6 写入） */
    private Long handlerId;

    /** 处置时间（M6 写入） */
    private LocalDateTime handledAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
