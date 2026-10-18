package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper（PRD GDS-08 / 数据库设计文档 §3.5）
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
