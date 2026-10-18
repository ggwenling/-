package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 标签视图（PRD GDS-08：GET /api/tags 返回启用中的标签，供发布表单多选 0~5）
 */
@Getter
@Setter
public class TagVO {

    private Long id;

    private String name;
}
