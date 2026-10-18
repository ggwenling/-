package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 分类实体（二级树形），对应表 category（PRD GDS-08 / 数据库设计文档 §3.4）。
 * parent_id=0 为一级分类（导航），其余为二级分类（商品归类与筛选）。
 */
@Getter
@Setter
@TableName("category")
public class Category {

    /** 一级分类的 parent_id 约定值 */
    public static final long ROOT_PARENT_ID = 0L;

    /** 启用状态 */
    public static final int STATUS_ENABLED = 0;
    /** 停用状态 */
    public static final int STATUS_DISABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类 ID，0=一级分类 */
    private Long parentId;

    /** 分类名，同一父级下不重名（应用层保证） */
    private String name;

    /** 图标（URL 或前端图标名） */
    private String icon;

    /** 排序，小者在前 */
    private Integer sort;

    /** 0=启用 1=停用 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
