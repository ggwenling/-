package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏事实 Mapper（PRD GDS-06 / 数据库设计文档 §3.17）
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
