package com.campus.market.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 商品列表查询参数（PRD GDS-03 筛选排序 / GDS-04 搜索 / GDS-07 教材检索）。
 * GET /api/goods 查询参数小驼峰绑定；分页默认 20 条/页（前端设计文档 §7）。
 */
@Getter
@Setter
public class GoodsListQuery {

    /** 排序：latest 最新（默认）/ priceAsc 价格升 / priceDesc 价格降 / hot 热度 */
    public static final String SORT_LATEST = "latest";
    public static final String SORT_PRICE_ASC = "priceAsc";
    public static final String SORT_PRICE_DESC = "priceDesc";
    public static final String SORT_HOT = "hot";

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数，默认 20，上限 100 */
    private long pageSize = 20;

    /** 分类 ID（一级分类自动展开其二级子类；二级分类精确匹配） */
    private Long categoryId;

    /** 成色 1~4 */
    private Integer conditionLevel;

    /** 最低价（含） */
    private BigDecimal minPrice;

    /** 最高价（含） */
    private BigDecimal maxPrice;

    /** 关键词，ngram 全文检索 title+description（GDS-04） */
    private String q;

    /** 教材课程名，模糊匹配（GDS-07） */
    private String courseName;

    /** ISBN，模糊匹配（GDS-07） */
    private String isbn;

    /** 排序方式，见 SORT_* 常量，非法值回退 latest */
    private String sort;
}
