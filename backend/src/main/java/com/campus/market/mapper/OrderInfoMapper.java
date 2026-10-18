package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper（PRD ORD-01~07）。
 * 状态迁移一律使用 MyBatis-Plus 条件更新（LambdaUpdateWrapper）在数据库行锁下完成，
 * 防止重复下单、重复确认、重复完成（PRD §5.2 / §8.3）；列表联查在 service 层批量装配，避免跨模块 XML。
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {
}
