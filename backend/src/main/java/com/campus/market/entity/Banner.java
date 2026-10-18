package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 首页轮播图（PRD ADM-07 / 数据库设计文档 §3.22）
 */
@Getter
@Setter
@TableName("banner")
public class Banner {

    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题（管理端备注） */
    private String title;

    private String imageUrl;

    private String linkUrl;

    /** 排序，小者在前 */
    private Integer sort;

    /** 0 下线 / 1 上线 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
