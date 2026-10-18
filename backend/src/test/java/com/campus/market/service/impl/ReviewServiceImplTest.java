package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.ReviewCreateDTO;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.Review;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.NotificationService;
import com.campus.market.service.SensitiveWordService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewServiceImpl 中测（ORD-06：7 天窗口、每单每人一次、被评价人推导与通知）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long ORDER_ID = 100L;

    @Mock ReviewMapper reviewMapper;
    @Mock OrderInfoMapper orderInfoMapper;
    @Mock UserMapper userMapper;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock NotificationService notificationService;

    @InjectMocks ReviewServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Review.class, OrderInfo.class, com.campus.market.entity.User.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(sensitiveWordService.findHits(any())).thenReturn(List.of());
        lenient().when(userMapper.selectBatchIds(any())).thenReturn(List.of());
    }

    private OrderInfo completedOrder(LocalDateTime completedAt) {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setOrderNo("SH20261002000100");
        order.setType(OrderInfo.TYPE_SALE);
        order.setStatus(OrderInfo.STATUS_COMPLETED);
        order.setBuyerId(BUYER);
        order.setSellerId(SELLER);
        order.setAmount(new BigDecimal("45.00"));
        order.setCompletedAt(completedAt);
        return order;
    }

    @Test
    void create_orderMissing_rejected() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.create(ORDER_ID, LoginUserTestFactory.user(BUYER), dto(5, "很好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void create_nonParty_forbidden() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(completedOrder(LocalDateTime.now()));
        assertThatThrownBy(() -> service.create(ORDER_ID, LoginUserTestFactory.user(999L), dto(5, "很好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    void create_notCompleted_rejected() {
        OrderInfo order = completedOrder(LocalDateTime.now());
        order.setStatus(OrderInfo.STATUS_SCHEDULED);
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);
        assertThatThrownBy(() -> service.create(ORDER_ID, LoginUserTestFactory.user(BUYER), dto(5, "很好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));
    }

    @Test
    void create_overSevenDays_rejected() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(completedOrder(LocalDateTime.now().minusDays(8)));
        assertThatThrownBy(() -> service.create(ORDER_ID, LoginUserTestFactory.user(BUYER), dto(5, "很好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REVIEW_WINDOW_CLOSED));
    }

    @Test
    void create_sensitiveContent_rejected() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(completedOrder(LocalDateTime.now()));
        when(sensitiveWordService.findHits("骗人的卖家")).thenReturn(List.of("骗"));
        assertThatThrownBy(() -> service.create(ORDER_ID, LoginUserTestFactory.user(BUYER), dto(1, "骗人的卖家")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_SENSITIVE));
    }

    @Test
    void create_concurrentDuplicate_mapsToDuplicateReview() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(completedOrder(LocalDateTime.now()));
        when(reviewMapper.insert(any(Review.class))).thenThrow(new DuplicateKeyException("uk_order_reviewer"));

        assertThatThrownBy(() -> service.create(ORDER_ID, LoginUserTestFactory.user(BUYER), dto(5, "很好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_DUPLICATE_REVIEW));
    }

    @Test
    void create_byBuyer_revieweeIsSeller_notified() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(completedOrder(LocalDateTime.now()));
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);

        service.create(ORDER_ID, LoginUserTestFactory.user(BUYER), dto(5, "很好的卖家"));

        verify(notificationService).push(eq(SELLER), eq(Notification.TYPE_ORDER), eq("收到新评价"),
                contains("5 星"), any(), any());
    }

    // ==================== 辅助 ====================

    private ReviewCreateDTO dto(int score, String content) {
        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setScore(score);
        dto.setContent(content);
        return dto;
    }
}
