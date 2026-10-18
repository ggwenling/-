package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 信用分流水 Mapper（PRD CRD-01 / 数据库设计文档 §3.16）。
 * credit_log 为信用分唯一事实来源，与 user.credit_score 同事务更新（T5）。
 */
@Mapper
public interface CreditLogMapper extends BaseMapper<CreditLog> {

    /**
     * 按主键锁定 user 行（SELECT ... FOR UPDATE）。
     * user 实体归 M1 所有、UserMapper 归 M1 所有，信用模块需要在变更分值前锁定行防止并发
     * before_score 读脏，故在信用 Mapper 内提供只读行锁查询（不做任何写操作）。
     */
    @Select("SELECT * FROM user WHERE id = #{userId} FOR UPDATE")
    User selectUserForUpdate(@Param("userId") Long userId);
}
