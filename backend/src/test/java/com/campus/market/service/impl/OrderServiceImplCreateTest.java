package com.campus.market.service.impl;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsWant;
import com.campus.market.entity.Notification;
import com.campus.market.entity.Offer;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.SwapPost;
import com.campus.market.entity.SwapRequest;
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
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 下单链路深测（ORD-01 / T11 取号 / T5 想要回链 / 并发锁定兜底）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplCreateTest {

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
        MpTestSupport.initTables(OrderInfo.class, Goods.class, GoodsWant.class,
                UserBehavior.class, Offer.class, WantPost.class, SwapRequest.class, SwapPost.class);
    }

    @BeforeEach
    void setUp() {
    }

    // ==================== createSaleOrder 前置校验 ====================

    @Test
    void createSaleOrder_uncertified_rejected() {
        LoginUser buyer = LoginUserTestFactory.uncertified(1L);
        assertThatThrownBy(() -> service.createSaleOrder(buyer, dto(10L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_NOT_CERTIFIED));
    }

    @Test
    void createSaleOrder_creditRestricted_rejected() {
        LoginUser buyer = LoginUserTestFactory.restricted(1L);
        assertThatThrownBy(() -> service.createSaleOrder(buyer, dto(10L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_RESTRICTED));
    }

    @Test
    void createSaleOrder_goodsNotFound_rejected() {
        when(goodsMapper.selectById(10L)).thenReturn(null);
        assertThatThrownBy(() -> service.createSaleOrder(LoginUserTestFactory.user(1L), dto(10L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    @Test
    void createSaleOrder_ownGoods_rejected() {
        when(goodsMapper.selectById(10L)).thenReturn(goods(10L, 1L, Goods.STATUS_ON_SALE));
        assertThatThrownBy(() -> service.createSaleOrder(LoginUserTestFactory.user(1L), dto(10L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    void createSaleOrder_deletedGoods_rejected() {
        when(goodsMapper.selectById(10L)).thenReturn(goods(10L, 2L, Goods.STATUS_DELETED));
        assertThatThrownBy(() -> service.createSaleOrder(LoginUserTestFactory.user(1L), dto(10L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    // ==================== createTransactionOrder ====================

    @Test
    void createTransactionOrder_unknownType_rejected() {
        assertThatThrownBy(() -> service.createTransactionOrder(1L, 2L, "RENT", 10L, BigDecimal.ONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createTransactionOrder_sameParty_rejected() {
        assertThatThrownBy(() -> service.createTransactionOrder(1L, 1L, OrderInfo.TYPE_SALE, 10L, BigDecimal.ONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createTransactionOrder_concurrentLockFailed_rejected() {
        // 并发兜底：条件 UPDATE（ON_SALE→IN_TRANSACTION）rows=0，40907
        when(goodsMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.createTransactionOrder(1L, 2L, OrderInfo.TYPE_SALE, 10L, BigDecimal.ONE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_GOODS_NOT_AVAILABLE));
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    @Test
    void createSaleOrder_success_locksGoods_writesWant_notifies() {
        when(goodsMapper.selectById(10L)).thenReturn(goods(10L, 2L, Goods.STATUS_ON_SALE));
        when(goodsMapper.update(isNull(), any())).thenReturn(1);   // 锁定成功
        stubOrderNo(1L);
        when(goodsWantMapper.insert(any(GoodsWant.class))).thenReturn(1);

        OrderInfo order = service.createSaleOrder(LoginUserTestFactory.user(1L), dto(10L));

        assertThat(order.getType()).isEqualTo(OrderInfo.TYPE_SALE);
        assertThat(order.getStatus()).isEqualTo(OrderInfo.STATUS_WAIT_CONFIRM);
        assertThat(order.getBuyerId()).isEqualTo(1L);
        assertThat(order.getSellerId()).isEqualTo(2L);
        assertThat(order.getOrderNo()).isEqualTo("SH" + LocalDate.now().toString().replace("-", "") + "000001");

        // T5：想要事实写入 + want_count 原子 +1（锁定 1 次 + 计数 1 次）
        verify(goodsWantMapper).insert(any(GoodsWant.class));
        verify(goodsMapper, org.mockito.Mockito.times(2)).update(isNull(), any());
        // 通知卖方
        verify(notificationService).push(eq(2L), eq(Notification.TYPE_ORDER), any(), any(), any(), any());
        // SALE 不写 ORDER_DONE 行为（完成时才写）
        verify(userBehaviorMapper, never()).insert(any(UserBehavior.class));
    }

    @Test
    void createTransactionOrder_purchase_skipsLock_writesWantNever() {
        stubOrderNo(2L);
        OrderInfo order = service.createTransactionOrder(1L, 2L, OrderInfo.TYPE_PURCHASE, null, new BigDecimal("22.50"));
        assertThat(order.getStatus()).isEqualTo(OrderInfo.STATUS_WAIT_CONFIRM);
        assertThat(order.getAmount()).isEqualByComparingTo(new BigDecimal("22.50"));
        // PURCHASE 无商品：不锁定、不写想要
        verify(goodsWantMapper, never()).insert(any(GoodsWant.class));
    }

    @Test
    void createTransactionOrder_swap_createdAsScheduled() {
        stubOrderNo(3L);
        OrderInfo order = service.createTransactionOrder(1L, 2L, OrderInfo.TYPE_SWAP, null, BigDecimal.ZERO);
        assertThat(order.getStatus()).isEqualTo(OrderInfo.STATUS_SCHEDULED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void recordGoodsWant_duplicateKey_silentlyIgnored() {
        when(goodsMapper.selectById(10L)).thenReturn(goods(10L, 2L, Goods.STATUS_ON_SALE));
        when(goodsMapper.update(isNull(), any())).thenReturn(1);   // 锁定；第二次（want_count）不会发生
        stubOrderNo(1L);
        when(goodsWantMapper.insert(any(GoodsWant.class)))
                .thenThrow(new DuplicateKeyException("uk_user_goods"));

        OrderInfo order = service.createSaleOrder(LoginUserTestFactory.user(1L), dto(10L));
        assertThat(order.getType()).isEqualTo(OrderInfo.TYPE_SALE);   // 未抛异常即幂等成功
        // want_count 语句只发生锁定那一次，未追加
        verify(goodsMapper, org.mockito.Mockito.times(1)).update(isNull(), any());
    }

    // ==================== 辅助 ====================

    private OrderCreateDTO dto(Long goodsId) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setGoodsId(goodsId);
        return dto;
    }

    private Goods goods(Long id, Long userId, String status) {
        Goods goods = new Goods();
        goods.setId(id);
        goods.setUserId(userId);
        goods.setStatus(status);
        goods.setPrice(new BigDecimal("45.00"));
        return goods;
    }

    /** T11 两步式取号桩：INSERT IGNORE + UPDATE + LAST_INSERT_ID */
    private void stubOrderNo(long seq) {
        lenient().when(orderNoSeqMapper.insertIgnore(any(LocalDate.class))).thenReturn(1);
        lenient().when(orderNoSeqMapper.incrementAndGet(any(LocalDate.class))).thenReturn(1);
        lenient().when(orderNoSeqMapper.selectLastInsertId()).thenReturn(seq);
    }
}
