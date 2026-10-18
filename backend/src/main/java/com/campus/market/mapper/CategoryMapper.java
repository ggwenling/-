package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper（PRD GDS-08 / 数据库设计文档 §3.4）
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
