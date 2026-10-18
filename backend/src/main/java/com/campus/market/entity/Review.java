package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 交易评价实体，对应表 review（PRD ORD-06 / 数据库设计文档 §3.15）。
 * 仅订单 COMPLETED 后 7 天内可评，双方可弃评；uk_order_reviewer 保证每单每人一次（CRD-03 查询入口见 ReviewService）。
 */
@Getter
@Setter
@TableName("review")
public class Review {

    /** 评价窗口：完成后 N 天内可评（PRD §5.6） */
    public static final int REVIEW_WINDOW_DAYS = 7;

    /** 评价内容最大长度（PRD §5.6） */
    public static final int MAX_CONTENT_LENGTH = 200;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单 ID（FK order_info.id） */
    private Long orderId;

    /** 评价人 ID（FK user.id） */
    private Long reviewerId;

    /** 被评价人 ID（FK user.id） */
    private Long revieweeId;

    /** 1~5 星 */
    private Integer score;

    /** 评价内容，≤200 字，敏感词校验后入库 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
