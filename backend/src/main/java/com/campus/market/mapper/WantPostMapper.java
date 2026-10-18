package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.WantPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 求购帖 Mapper（PRD REQ-01~04）。
 * selectByIdForUpdate 提供接受应约时的事务行锁（数据库设计文档 §3.12 原子接受流程①）。
 */
@Mapper
public interface WantPostMapper extends BaseMapper<WantPost> {

    /** 事务内加行锁读取求购帖（T8 接受应约第①步：SELECT ... FOR UPDATE） */
    @Select("SELECT * FROM want_post WHERE id = #{id} FOR UPDATE")
    WantPost selectByIdForUpdate(@Param("id") Long id);
}
