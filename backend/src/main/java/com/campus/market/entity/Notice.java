package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台公告（PRD ADM-07 / 数据库设计文档 §3.23）：状态机 0 草稿 → 1 发布 → 2 下线。
 */
@Getter
@Setter
@TableName("notice")
public class Notice {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_OFFLINE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 公告内容（≤2000） */
    private String content;

    /** 0 草稿 / 1 发布 / 2 下线 */
    private Integer status;

    /** 发布管理员 ID（fk_notice_publisher） */
    private Long publisherId;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
