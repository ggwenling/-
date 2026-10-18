package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.Notification;
import com.campus.market.entity.Offer;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.Review;
import com.campus.market.entity.SwapPost;
import com.campus.market.entity.SwapRequest;
import com.campus.market.entity.User;
import com.campus.market.entity.UserBehavior;
import com.campus.market.entity.WantPost;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsWantMapper;
import com.campus.market.mapper.OfferMapper;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.OrderNoSeqMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.SwapPostMapper;
import com.campus.market.mapper.SwapRequestMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.mapper.WantPostMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserAccessGuard;
import com.campus.market.service.CreditService;
import com.campus.market.service.NotificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 状态机深测（ORD-02~04 确认/拒绝/取消/完成、SWAP 双确认 T7、超时任务、视角 VO）。
 * 一致性口径：状态迁移全部为条件 UPDATE，rows=0 即 40906；解锁仅 IN_TRANSACTION→ON_SALE。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplStateTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long ORDER_ID = 100L;
    private static final Long GOODS_ID = 10L;

    @Mock OrderInfoMapper orderInfoMapper;
    @Mock OrderNoSeqMapper orderNoSeqMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock GoodsWantMapper goodsWantMapper;
    @Mock GoodsImageMapper goodsImageMapper;
    @Mock UserMapper userMapper;
    @Mock ReviewMapper reviewMapper;
    @Mock UserBehaviorMapper userBehaviorMapper;
    @Mock OfferMapper offerMapper;
    @Mock WantPostMapper wantPostMapper;
    @Mock SwapRequestMapper swapRequestMapper;
    @Mock SwapPostMapper swapPostMapper;
    @Mock CreditService creditService;
    @Mock NotificationService notificationService;
    /** 真实守卫（阈值取自 CreditProperties 默认 60），与生产口径一致 */
    @Spy UserAccessGuard userAccessGuard = new UserAccessGuard(new CreditProperties());

    @InjectMocks
    OrderServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(OrderInfo.class, Goods.class, GoodsImage.class, Review.class,
                UserBehavior.class, Offer.class, WantPost.class, SwapRequest.class, SwapPost.class, User.class);
    }

    @BeforeEach
    void setUp() {
        // detail 组装链的轻量桩（空集合即可，不关注 VO 细节）
        lenient().when(goodsMapper.selectBatchIds(any())).thenReturn(List.of());
        lenient().when(goodsImageMapper.selectList(any())).thenReturn(List.of());
        lenient().when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        lenient().when(reviewMapper.selectList(any())).thenReturn(List.of());
        lenient().when(offerMapper.selectList(any())).thenReturn(List.of());
        lenient().when(swapRequestMapper.selectList(any())).thenReturn(List.of());
    }

    // ==================== ORD-02 确认 / 拒绝 ====================

    @Test
    void confirm_byBuyer_forbidden() {
        stubOrder(order(OrderInfo.STATUS_WAIT_CONFIRM));
        assertThatThrownBy(() -> service.confirm(ORDER_ID, user(BUYER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    void confirm_stateInvalid_rowsZero() {
        stubOrder(order(OrderInfo.STATUS_WAIT_CONFIRM));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.confirm(ORDER_ID, user(SELLER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));
    }

    @Test
    void confirm_success_notifiesBoth() {
        stubOrder(order(OrderInfo.STATUS_WAIT_CONFIRM));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);

        service.confirm(ORDER_ID, user(SELLER));

        verify(orderInfoMapper).update(isNull(), any());   // 条件更新 WAIT_CONFIRM→SCHEDULED
        verifyBothNotified("订单已确认");
    }

    @Test
    void reject_success_unlocksGoods() {
        stubOrder(order(OrderInfo.STATUS_WAIT_CONFIRM));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.reject(ORDER_ID, user(SELLER), "不想要了");

        verify(goodsMapper).update(isNull(), any());       // IN_TRANSACTION→ON_SALE 解锁
        verifyBothNotified("订单被拒绝");
    }

    // ==================== ORD-03 取消 ====================

    @Test
    void cancel_bySeller_forbidden() {
        stubOrder(order(OrderInfo.STATUS_WAIT_CONFIRM));
        assertThatThrownBy(() -> service.cancel(ORDER_ID, user(SELLER), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    void cancel_completedState_rowsZero_rejected() {
        stubOrder(order(OrderInfo.STATUS_COMPLETED));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.cancel(ORDER_ID, user(BUYER), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));
    }

    @Test
    void cancel_byBuyer_success_unlocksGoods() {
        stubOrder(order(OrderInfo.STATUS_SCHEDULED));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.cancel(ORDER_ID, user(BUYER), "临时有事");

        verify(goodsMapper).update(isNull(), any());
        verifyBothNotified("订单已取消");
    }

    // ==================== ORD-04 确认完成 ====================

    @Test
    void complete_byBuyer_onSaleOrder_forbidden() {
        stubOrder(order(OrderInfo.STATUS_SCHEDULED));
        assertThatThrownBy(() -> service.complete(ORDER_ID, user(BUYER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    void complete_success_rewardsBothParties() {
        OrderInfo order = order(OrderInfo.STATUS_SCHEDULED);
        stubOrder(order);
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.complete(ORDER_ID, user(SELLER));

        // 商品 IN_TRANSACTION→SOLD + 成交行为埋点 + 双方信用 +ORDER_COMPLETE
        verify(goodsMapper).update(isNull(), any());
        verify(userBehaviorMapper).insert(any(UserBehavior.class));
        verify(creditService).addCredit(BUYER, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_ID);
        verify(creditService).addCredit(SELLER, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_ID);
    }

    @Test
    void complete_rowsZero_butAlreadyCompleted_idempotentSuccess() {
        OrderInfo current = order(OrderInfo.STATUS_COMPLETED);   // 并发下已完成
        stubOrder(order(OrderInfo.STATUS_SCHEDULED));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(current);

        service.complete(ORDER_ID, user(SELLER));

        // 幂等：不重复发奖励
        verify(creditService, never()).addCredit(any(), any(), any(), any());
    }

    @Test
    void complete_rowsZero_andNotCompleted_rejected() {
        stubOrder(order(OrderInfo.STATUS_SCHEDULED));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order(OrderInfo.STATUS_WAIT_CONFIRM));

        assertThatThrownBy(() -> service.complete(ORDER_ID, user(SELLER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));
    }

    // ==================== SWAP 双确认（T7） ====================

    @Test
    void completeSwap_firstConfirm_notifiesCounterpartOnly() {
        OrderInfo swap = order(OrderInfo.STATUS_SCHEDULED);
        swap.setType(OrderInfo.TYPE_SWAP);
        stubOrder(swap);
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(swap);   // 双方确认时间仍为空

        service.complete(ORDER_ID, user(BUYER));

        // 仅提示对方确认，不发奖励
        verify(notificationService).push(eq(SELLER), eq(Notification.TYPE_ORDER), any(), any(), any(), any());
        verify(creditService, never()).addCredit(any(), any(), any(), any());
    }

    @Test
    void completeSwap_bothConfirmed_completesWithRewards() {
        OrderInfo swap = order(OrderInfo.STATUS_SCHEDULED);
        swap.setType(OrderInfo.TYPE_SWAP);
        OrderInfo afterBoth = order(OrderInfo.STATUS_SCHEDULED);
        afterBoth.setType(OrderInfo.TYPE_SWAP);
        afterBoth.setBuyerConfirmedAt(LocalDateTime.now());
        afterBoth.setSellerConfirmedAt(LocalDateTime.now());
        stubOrder(swap);
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1, 1);   // 首次确认 + 置 COMPLETED
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(afterBoth);
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.complete(ORDER_ID, user(SELLER));

        verify(creditService).addCredit(BUYER, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_ID);
        verify(creditService).addCredit(SELLER, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_ID);
    }

    @Test
    void completeSwap_secondConfirmBySameParty_rejected() {
        OrderInfo swap = order(OrderInfo.STATUS_SCHEDULED);
        swap.setType(OrderInfo.TYPE_SWAP);
        stubOrder(swap);
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.complete(ORDER_ID, user(BUYER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));
    }

    // ==================== 超时自动取消 ====================

    @Test
    void autoCancelTimeout_waitConfirmExpired_cancelsWithPenalty() {
        OrderInfo expired = order(OrderInfo.STATUS_WAIT_CONFIRM);
        expired.setCreatedAt(LocalDateTime.now().minusHours(49));
        when(orderInfoMapper.selectList(any())).thenReturn(List.of(expired), List.of());
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        int cancelled = service.autoCancelTimeoutOrders();

        assertThat(cancelled).isEqualTo(1);
        verify(creditService).addCredit(SELLER, CreditLog.REASON_CANCEL_TIMEOUT, CreditLog.REF_TYPE_ORDER, ORDER_ID);
        verifyBothNotified("订单超时自动取消");
    }

    @Test
    void autoCancelTimeout_conditionalUpdateRowsZero_skipped() {
        OrderInfo expired = order(OrderInfo.STATUS_WAIT_CONFIRM);
        expired.setCreatedAt(LocalDateTime.now().minusHours(49));
        when(orderInfoMapper.selectList(any())).thenReturn(List.of(expired), List.of());
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);   // 已被并发处理

        int cancelled = service.autoCancelTimeoutOrders();

        assertThat(cancelled).isEqualTo(0);
        verify(creditService, never()).addCredit(any(), any(), any(), any());
        verify(notificationService, never()).push(any(), any(), any(), any(), any(), any());
    }

    @Test
    void autoCancelTimeout_scheduledExpired_cancelsToo() {
        OrderInfo expired = order(OrderInfo.STATUS_SCHEDULED);
        expired.setConfirmedAt(LocalDateTime.now().minusDays(16));
        when(orderInfoMapper.selectList(any())).thenReturn(List.of(), List.of(expired));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);

        int cancelled = service.autoCancelTimeoutOrders();

        assertThat(cancelled).isEqualTo(1);
    }

    // ==================== ORD-05 列表 / 详情 ====================

    @Test
    void pageMyOrders_invalidRole_rejected() {
        assertThatThrownBy(() -> service.pageMyOrders(BUYER, "admin", null, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void detail_nonParty_forbidden() {
        stubOrder(order(OrderInfo.STATUS_WAIT_CONFIRM));
        assertThatThrownBy(() -> service.detail(ORDER_ID, 999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    void detail_notFound() {
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.detail(ORDER_ID, BUYER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ==================== 辅助 ====================

    private LoginUser user(Long id) {
        return LoginUserTestFactory.user(id);
    }

    private OrderInfo order(String status) {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setOrderNo("SH20261002000100");
        order.setType(OrderInfo.TYPE_SALE);
        order.setStatus(status);
        order.setBuyerId(BUYER);
        order.setSellerId(SELLER);
        order.setGoodsId(GOODS_ID);
        order.setAmount(new BigDecimal("45.00"));
        order.setCreatedAt(LocalDateTime.now().minusHours(1));
        return order;
    }

    private void stubOrder(OrderInfo order) {
        lenient().when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);
    }

    private void verifyBothNotified(String title) {
        verify(notificationService).push(eq(BUYER), eq(Notification.TYPE_ORDER), eq(title), any(), any(), any());
        verify(notificationService).push(eq(SELLER), eq(Notification.TYPE_ORDER), eq(title), any(), any(), any());
    }
}
