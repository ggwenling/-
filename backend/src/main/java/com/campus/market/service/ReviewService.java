package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.ReviewCreateDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.ReviewVO;

/**
 * 交易评价服务（PRD ORD-06 互评 / CRD-03 评价展示）。
 * 规则：仅订单 COMPLETED 后 7 天内可评、每单每人一次（uk_order_reviewer 兜底）、
 * 仅订单双方可评、内容过敏感词检查、评价后对方收到 ORDER 通知（PRD §5.6）。
 */
public interface ReviewService {

    /**
     * ORD-06 创建评价。
     *
     * @param orderId  订单 ID
     * @param reviewer 评价人（须为订单双方之一）
     * @param dto      score 1~5 + content ≤200（敏感词校验）
     * @return 评价视图（含评价人摘要）
     */
    ReviewVO create(Long orderId, LoginUser reviewer, ReviewCreateDTO dto);

    /**
     * CRD-03 按被评价人分页查询评价（公开接口，时间倒序）；个人主页评价 Tab 数据源。
     */
    PageResult<ReviewVO> pageByReviewee(Long revieweeId, long pageNum, long pageSize);
}
