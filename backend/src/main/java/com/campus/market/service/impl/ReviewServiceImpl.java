package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.ReviewCreateDTO;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.Review;
import com.campus.market.entity.User;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.NotificationService;
import com.campus.market.service.ReviewService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交易评价服务实现（PRD ORD-06 / CRD-03 / 数据库设计文档 §3.15）。
 * 幂等：uk_order_reviewer(order_id, reviewer_id) 唯一约束兜底，DuplicateKeyException 转业务码 40908；
 * 窗口：仅 COMPLETED 后 7 天内可评；内容先过 M2 SensitiveWordService（DFA）再入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReviewVO create(Long orderId, LoginUser reviewer, ReviewCreateDTO dto) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getBuyerId(), reviewer.getUserId())
                && !Objects.equals(order.getSellerId(), reviewer.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN);
        }
        if (!OrderInfo.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单完成后才能评价");
        }
        if (order.getCompletedAt() == null
                || order.getCompletedAt().plusDays(Review.REVIEW_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REVIEW_WINDOW_CLOSED);
        }

        // 敏感词检查（PRD §5.6 / §6.2），命中即拒绝并列出命中词
        String content = dto.getContent();
        if (content != null && !content.isBlank()) {
            List<String> hits = sensitiveWordService.findHits(content);
            if (!hits.isEmpty()) {
                throw new BusinessException(ErrorCode.GOODS_SENSITIVE, "评价内容包含敏感词：" + String.join("、", hits));
            }
        }

        Long revieweeId = Objects.equals(order.getBuyerId(), reviewer.getUserId())
                ? order.getSellerId() : order.getBuyerId();

        Review review = new Review();
        review.setOrderId(orderId);
        review.setReviewerId(reviewer.getUserId());
        review.setRevieweeId(revieweeId);
        review.setScore(dto.getScore());
        review.setContent(content);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // uk_order_reviewer 兜底：并发重复评价转业务码（PRD ORD-06 每单每人一次）
            throw new BusinessException(ErrorCode.ORDER_DUPLICATE_REVIEW);
        }

        // 通知被评价人（PRD ORD-06）
        notificationService.push(revieweeId, Notification.TYPE_ORDER, "收到新评价",
                String.format("%s 对订单 %s 的交易评价了 %d 星，快去看看吧",
                        reviewer.getUsername(), order.getOrderNo(), dto.getScore()),
                CreditLog.REF_TYPE_ORDER, order.getId());

        Map<Long, User> reviewerMap = userMapper.selectBatchIds(List.of(reviewer.getUserId())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return toReviewVO(review, reviewerMap);
    }

    @Override
    public PageResult<ReviewVO> pageByReviewee(Long revieweeId, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        IPage<Review> result = reviewMapper.selectPage(new Page<>(Math.max(pageNum, 1), pageSize),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getRevieweeId, revieweeId)
                        .orderByDesc(Review::getCreatedAt)
                        .orderByDesc(Review::getId));

        List<Review> records = result.getRecords();
        Map<Long, User> reviewerMap = records.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(records.stream().map(Review::getReviewerId).distinct().toList())
                        .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<ReviewVO> voList = records.stream().map(review -> toReviewVO(review, reviewerMap)).toList();

        PageResult<ReviewVO> voPage = new PageResult<>();
        voPage.setList(voList);
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    private ReviewVO toReviewVO(Review review, Map<Long, User> reviewerMap) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setReviewerId(review.getReviewerId());
        vo.setRevieweeId(review.getRevieweeId());
        vo.setScore(review.getScore());
        vo.setContent(review.getContent());
        vo.setCreatedAt(review.getCreatedAt());
        User reviewer = reviewerMap.get(review.getReviewerId());
        if (reviewer != null) {
            vo.setReviewerNickname(reviewer.getNickname());
            vo.setReviewerAvatar(reviewer.getAvatar());
        }
        return vo;
    }
}
