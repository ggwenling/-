package com.campus.market.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 交换帖列表查询参数（PRD SWP-02 交换广场：分页 + 状态筛选 + 分类 + 关键词）。
 */
@Getter
@Setter
public class SwapPostListQuery {

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数，默认 20，上限 100 */
    private long pageSize = 20;

    /** 状态筛选：OPEN（默认）/ DEALT / CLOSED；DELETED 永不返回 */
    private String status = "OPEN";

    /** 物品分类 ID（一级自动展开二级子类） */
    private Long categoryId;

    /** 只看我发布的 */
    private Boolean mine;

    /** 关键词，模糊匹配标题+物品描述 */
    private String q;
}
