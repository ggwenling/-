package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.UserAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 校园认证 Mapper（PRD USR-03）。
 */
@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuth> {

    /** 学号查认证记录（查重：一学号一账号） */
    @Select("SELECT * FROM user_auth WHERE student_no = #{studentNo} LIMIT 1")
    UserAuth selectByStudentNo(@Param("studentNo") String studentNo);

    /** 校园邮箱查认证记录（查重） */
    @Select("SELECT * FROM user_auth WHERE campus_email = #{email} LIMIT 1")
    UserAuth selectByCampusEmail(@Param("email") String email);

    /** 按用户 ID 查认证记录（user_id 是唯一键而非主键，不能用 selectById） */
    @Select("SELECT * FROM user_auth WHERE user_id = #{userId} LIMIT 1")
    UserAuth selectByUserId(@Param("userId") Long userId);
}
