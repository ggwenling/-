package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 标签实体，对应表 tag（PRD GDS-01 / 数据库设计文档 §3.5，T1 修订）。
 * 标签独立于分类，用于推荐的多维匹配；由后台"分类与标签管理"（M6）维护。
 */
@Getter
@Setter
@TableName("tag")
public class Tag {

    /** 启用状态 */
    public static final int STATUS_ENABLED = 0;
    /** 停用状态 */
    public static final int STATUS_DISABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名，全局唯一 */
    private String name;

    /** 排序，小者在前 */
    private Integer sort;

    /** 0=启用 1=停用 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
