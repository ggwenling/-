package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树节点视图（PRD GDS-08：GET /api/categories 返回二级树，公共接口无需登录）。
 * 一级节点 children 为启用中的二级分类；发布表单 el-cascader 与首页宫格共用。
 */
@Getter
@Setter
public class CategoryTreeVO {

    private Long id;

    private Long parentId;

    private String name;

    /** 图标（前端图标名或 URL） */
    private String icon;

    private Integer sort;

    /** 二级分类（仅一级节点有值） */
    private List<CategoryTreeVO> children = new ArrayList<>();
}
