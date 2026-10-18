package com.campus.market.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 求购帖列表查询参数（PRD REQ-02 求购广场：分页 + 状态筛选 + 分类 + 关键词）。
 * GET /api/want-posts 查询参数小驼峰绑定；默认只返回 OPEN，DELETED 永不返回。
 */
@Getter
@Setter
public class WantPostListQuery {

    public static final String SORT_LATEST = "latest";
    public static final String SORT_BUDGET_DESC = "budgetDesc";

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数，默认 20，上限 100 */
    private long pageSize = 20;

    /** 状态筛选：OPEN（默认）/ DEALT / CLOSED；DELETED 不接受 */
    private String status = "OPEN";

    /** 期望分类 ID（一级分类自动展开二级子类） */
    private Long categoryId;

    /** 只看我发布的（个人中心/我的求购），需登录 */
    private Boolean mine;

    /** 关键词，模糊匹配标题+描述 */
    private String q;

    /** 排序：latest 最新（默认）/ budgetDesc 预算高到低 */
    private String sort;
}
