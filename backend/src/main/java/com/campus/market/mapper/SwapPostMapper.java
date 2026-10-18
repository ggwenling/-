package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.SwapPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 交换帖 Mapper（PRD SWP-01~03）。
 * selectByIdForUpdate 提供同意交换请求时的事务行锁（数据库设计文档 §3.14 原子同意流程）。
 */
@Mapper
public interface SwapPostMapper extends BaseMapper<SwapPost> {

    /** 事务内加行锁读取交换帖（T8 同意交换第①步） */
    @Select("SELECT * FROM swap_post WHERE id = #{id} FOR UPDATE")
    SwapPost selectByIdForUpdate(@Param("id") Long id);
}
