package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易评价 Mapper（PRD ORD-06 / CRD-03）。
 * uk_order_reviewer(order_id, reviewer_id) 保证每单每人一次，DuplicateKeyException 兜底幂等；
 * 评价列表联查评价人信息在 service 层批量装配。
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
