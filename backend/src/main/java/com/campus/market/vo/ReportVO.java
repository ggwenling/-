package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 举报工单响应体（PRD RPT-01/RPT-02；后台处置端 ADM-04 亦可复用）。
 * targetTitle 为举报时目标标题/昵称的"快照回溯"：用 selectById 直查目标表（不走 DELETED 过滤），
 * 因此已软删的目标仍可展示标题（PRD §7 多态关联）。
 * result / handledAt 由 M6 后台处置写入，前台只读。
 */
@Getter
@Setter
public class ReportVO {

    private Long id;

    /** 举报人 ID */
    private Long reporterId;

    /** GOODS / WANT / SWAP / USER */
    private String targetType;

    private Long targetId;

    /** 目标标题（GOODS/WANT/SWAP→title，USER→nickname）；目标彻底不存在时为 null */
    private String targetTitle;

    /** VIOLATION / FRAUD / COUNTERFEIT / OTHER */
    private String reportType;

    private String description;

    /** 举报截图 URL 列表（images JSON 列反序列化；失败时返回空列表） */
    private List<String> images;

    /** 0 待处理 / 1 已处置 / 2 已驳回 */
    private Integer status;

    /** 状态中文：待处理 / 已处置 / 已驳回 */
    private String statusText;

    /** 处置说明（M6 写入） */
    private String result;

    /** 处置时间（M6 写入） */
    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
