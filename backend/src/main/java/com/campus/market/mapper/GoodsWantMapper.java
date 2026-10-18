package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.GoodsWant;
import org.apache.ibatis.annotations.Mapper;

/**
 * "想要"事实 Mapper（数据库设计文档 §3.9）。M2 只读不写，写入方为 M3 下单流程。
 */
@Mapper
public interface GoodsWantMapper extends BaseMapper<GoodsWant> {
}
