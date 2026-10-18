package com.campus.market.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情视图（PRD GDS-05：商品 + 图集 + 标签 + 卖家摘要 + 计数 + 相似推荐位）。
 * similarGoods 为 M5 推荐服务预留字段，M2 恒返回空列表（前端渲染 EmptyBlock 占位）。
 */
@Getter
@Setter
public class GoodsDetailVO {

    private Long id;

    private String title;

    private String description;

    /** 面交价，0=免费 */
    private BigDecimal price;

    /** 成色 1~4 */
    private Integer conditionLevel;

    /** 教材课程名（教材书籍分类） */
    private String courseName;

    private String isbn;

    private String tradeLocation;

    /** 状态：ON_SALE / IN_TRANSACTION / SOLD / OFF_SALE / DELETED */
    private String status;

    /** 二级分类 */
    private Long categoryId;

    private String categoryName;

    /** 一级分类（导航聚合用） */
    private Long parentCategoryId;

    private String parentCategoryName;

    private Long viewCount;

    private Long wantCount;

    private Long favoriteCount;

    private Integer heatScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 图集（按 sort 升序） */
    private List<GoodsImageVO> images;

    /** 标签 */
    private List<GoodsTagVO> tags;

    /** 卖家摘要（昵称/头像/信用分/认证状态） */
    private SellerVO seller;

    /** 当前登录用户是否已收藏；游客为 null */
    private Boolean favorited;

    /** 相似推荐位（M5 REC 填充，M2 预留恒为空列表） */
    private List<GoodsCardVO> similarGoods = List.of();
}
