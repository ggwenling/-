package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.GoodsTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品-标签关联 Mapper（数据库设计文档 §3.8）
 */
@Mapper
public interface GoodsTagMapper extends BaseMapper<GoodsTag> {
}
