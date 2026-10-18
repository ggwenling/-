package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper（PRD USR-04/06 聚合计数）。
 * 商品/收藏计数以只读 SQL 投影实现，避免跨模块实体依赖（goods/favorite 表归 M2 的实体所有）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 在售商品数（USR-04 个人主页） */
    @Select("SELECT COUNT(*) FROM goods WHERE user_id = #{userId} AND status = 'ON_SALE'")
    long countOnSaleGoods(@Param("userId") Long userId);

    /** 已售出商品数（USR-06 我的聚合） */
    @Select("SELECT COUNT(*) FROM goods WHERE user_id = #{userId} AND status = 'SOLD'")
    long countSoldGoods(@Param("userId") Long userId);

    /** 收藏数（USR-06 我的聚合） */
    @Select("SELECT COUNT(*) FROM favorite WHERE user_id = #{userId}")
    long countFavorites(@Param("userId") Long userId);

    /** 按用户名查询（USR-02 登录 / USR-01 注册查重） */
    @Select("SELECT * FROM user WHERE username = #{username} LIMIT 1")
    User selectByUsername(@Param("username") String username);
}
