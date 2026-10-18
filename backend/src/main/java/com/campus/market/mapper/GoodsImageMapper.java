package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.GoodsImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品图片 Mapper（PRD GDS-01 / 数据库设计文档 §3.7）
 */
@Mapper
public interface GoodsImageMapper extends BaseMapper<GoodsImage> {
}
