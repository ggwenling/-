package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.dto.OfferCreateDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Notification;
import com.campus.market.entity.Offer;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.User;
import com.campus.market.entity.WantPost;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.OfferMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.mapper.WantPostMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserAccessGuard;
import com.campus.market.service.NotificationService;
import com.campus.market.service.OrderService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.OfferVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WantPostServiceImpl 深测（REQ-01~04：发布校验、应约 40909~40915、T8 原子接受锁序与建单回填）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WantPostServiceImplTest {

    private static final Long BUYER_ID = 1L;      // 求购者（帖主）
    private static final Long SELLER_ID = 2L;     // 应约者
    private static final Long POST_ID = 10L;
    private static final Long OFFER_ID = 20L;

    @Mock WantPostMapper wantPostMapper;
    @Mock OfferMapper offerMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock UserMapper userMapper;
    @Mock OrderService orderService;
    @Mock NotificationService notificationService;
    @Mock SensitiveWordService sensitiveWordService;
    /** 真实守卫（阈值取自 CreditProperties 默认 60），与生产口径一致 */
    @Spy UserAccessGuard userAccessGuard = new UserAccessGuard(new CreditProperties());

    @InjectMocks WantPostServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(WantPost.class, Offer.class, Category.class, User.class, OrderInfo.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(sensitiveWordService.findHits(any())).thenReturn(List.of());
        lenient().when(userMapper.selectBatchIds(any())).thenReturn(List.of());
    }

    // ==================== createOffer 校验链 ====================

    @Test
    void createOffer_uncertified_rejected() {
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), LoginUserTestFactory.uncertified(1L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_NOT_CERTIFIED));
    }

    @Test
    void createOffer_creditRestricted_rejected() {
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), LoginUserTestFactory.restricted(1L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_RESTRICTED));
    }

    @Test
    void createOffer_postMissing_rejected() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_NOT_FOUND));
    }

    @Test
    void createOffer_postClosed_rejected() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_CLOSED));
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_CLOSED));
    }

    @Test
    void createOffer_ownPost_rejected() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), buyer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_OFFER_SELF));
    }

    @Test
    void createOffer_negativePrice_rejected() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("-1", null), seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_PARAM_INVALID));
    }

    @Test
    void createOffer_sensitiveMessage_rejected() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        org.mockito.Mockito.doReturn(List.of("代考")).when(sensitiveWordService).findHits(any());
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", "提供代考服务"), seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_SENSITIVE));
        // 敏感词拦截时不应落库
        verify(offerMapper, never()).insert(any(Offer.class));
    }

    @Test
    void createOffer_duplicatePending_rejected() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        when(offerMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_OFFER_DUPLICATE));
    }

    @Test
    void createOffer_concurrentUkConflict_mapsToDuplicate() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        when(offerMapper.selectCount(any())).thenReturn(0L);
        when(offerMapper.insert(any(Offer.class))).thenThrow(new DuplicateKeyException("uk_offer_pending"));

        assertThatThrownBy(() -> service.createOffer(POST_ID, offerDto("22.50", null), seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_OFFER_DUPLICATE));
    }

    @Test
    void createOffer_success_notifiesBuyer() {
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        when(offerMapper.selectCount(any())).thenReturn(0L);
        when(offerMapper.insert(any(Offer.class))).thenReturn(1);

        service.createOffer(POST_ID, offerDto("22.50", "九成新可面交"), seller());

        verify(notificationService).push(eq(BUYER_ID), eq(Notification.TYPE_ORDER), eq("收到新的应约"),
                contains("收到一条应约"), any(), any());
    }

    // ==================== acceptOffer（T8 原子接受） ====================

    @Test
    void acceptOffer_offerMissing_rejected() {
        when(offerMapper.selectByIdForUpdate(OFFER_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, buyer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OFFER_NOT_FOUND));
    }

    @Test
    void acceptOffer_notBuyer_forbidden() {
        stubAcceptChain(offerPending(), post(WantPost.STATUS_OPEN));
        // 应约者自己尝试接受
        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_FORBIDDEN));
    }

    @Test
    void acceptOffer_postNotOpen_rejected() {
        stubAcceptChain(offerPending(), post(WantPost.STATUS_CLOSED));
        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, buyer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_CLOSED));
    }

    @Test
    void acceptOffer_offerAlreadyHandled_rejected() {
        Offer handled = offerPending();
        handled.setStatus(Offer.STATUS_ACCEPTED);
        stubAcceptChain(handled, post(WantPost.STATUS_OPEN));
        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, buyer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_OFFER_HANDLED));
    }

    @Test
    void acceptOffer_postConditionalUpdateRowsZero_rejected() {
        stubAcceptChain(offerPending(), post(WantPost.STATUS_OPEN));
        when(wantPostMapper.update(isNull(), any())).thenReturn(0);   // 并发：帖状态已变
        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, buyer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_CLOSED));
    }

    @Test
    void acceptOffer_success_fullAtomicChain() {
        stubAcceptChain(offerPending(), post(WantPost.STATUS_OPEN));
        when(wantPostMapper.update(isNull(), any())).thenReturn(1);          // ② 帖→DEALT
        when(offerMapper.update(isNull(), any())).thenReturn(1, 1);          // ③ 本单→ACCEPTED + ⑤ orderId 回填
        when(offerMapper.rejectOtherPending(POST_ID, OFFER_ID)).thenReturn(2);
        when(offerMapper.selectList(any())).thenReturn(List.of());           // others 快照
        when(offerMapper.selectById(OFFER_ID)).thenReturn(offerAccepted());
        OrderInfo order = new OrderInfo();
        order.setId(55L);
        order.setOrderNo("SH20261002000055");
        // ⑤ buyer=求购者(1)、seller=应约者(2)、PURCHASE、无商品、金额=报价
        when(orderService.createTransactionOrder(BUYER_ID, SELLER_ID, OrderInfo.TYPE_PURCHASE, null,
                new BigDecimal("22.50"))).thenReturn(order);

        OfferVO vo = service.acceptOffer(OFFER_ID, buyer());

        // 锁序断言（T8 固定加锁顺序避免死锁）：先锁应约，再锁帖
        InOrder inOrder = inOrder(offerMapper, wantPostMapper);
        inOrder.verify(offerMapper).selectByIdForUpdate(OFFER_ID);
        inOrder.verify(wantPostMapper).selectByIdForUpdate(POST_ID);
        inOrder.verify(wantPostMapper).update(isNull(), any());              // 帖 DEALT
        inOrder.verify(offerMapper).update(isNull(), any());                 // 本单 ACCEPTED

        // 其余待处理全部拒绝
        verify(offerMapper).rejectOtherPending(POST_ID, OFFER_ID);
        // 建单 + order_id 回填
        verify(orderService).createTransactionOrder(BUYER_ID, SELLER_ID, OrderInfo.TYPE_PURCHASE, null,
                new BigDecimal("22.50"));
        verify(offerMapper, org.mockito.Mockito.times(2)).update(isNull(), any());
        // 双方通知：应约者成功 + 无其余应约（others 空）
        verify(notificationService).push(eq(SELLER_ID), eq(Notification.TYPE_ORDER), eq("应约已被接受"),
                contains("SH20261002000055"), any(), any());
        verify(notificationService, never()).push(eq(SELLER_ID), eq(Notification.TYPE_ORDER),
                eq("应约未被选中"), any(), any(), any());
        assertThat(vo.getStatus()).isEqualTo(Offer.STATUS_ACCEPTED);
    }

    // ==================== withdraw / reject ====================

    @Test
    void withdrawOffer_handledOffer_rejected() {
        Offer offer = offerPending();
        offer.setUserId(SELLER_ID);
        when(offerMapper.selectById(OFFER_ID)).thenReturn(offer);
        when(offerMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.withdrawOffer(OFFER_ID, seller()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_OFFER_HANDLED));
    }

    @Test
    void rejectOffer_byNonOwner_forbidden() {
        Offer offer = offerPending();
        offer.setUserId(SELLER_ID);
        when(offerMapper.selectById(OFFER_ID)).thenReturn(offer);
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));

        assertThatThrownBy(() -> service.rejectOffer(OFFER_ID, seller(), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_FORBIDDEN));
    }

    @Test
    void rejectOffer_success_notifiesOfferUser() {
        Offer offer = offerPending();
        offer.setUserId(SELLER_ID);
        when(offerMapper.selectById(OFFER_ID)).thenReturn(offer);
        when(wantPostMapper.selectById(POST_ID)).thenReturn(post(WantPost.STATUS_OPEN));
        when(offerMapper.update(isNull(), any())).thenReturn(1);

        service.rejectOffer(OFFER_ID, buyer(), "价格不合适");

        verify(notificationService).push(eq(SELLER_ID), eq(Notification.TYPE_ORDER), eq("应约被拒绝"),
                contains("价格不合适"), any(), any());
    }

    // ==================== 辅助 ====================

    private LoginUser buyer() {
        return LoginUserTestFactory.user(BUYER_ID);
    }

    private LoginUser seller() {
        return LoginUserTestFactory.user(SELLER_ID);
    }

    private WantPost post(String status) {
        WantPost post = new WantPost();
        post.setId(POST_ID);
        post.setUserId(BUYER_ID);
        post.setTitle("冒烟-求购二手高数教材");
        post.setStatus(status);
        post.setBudget(new BigDecimal("25.00"));
        return post;
    }

    private Offer offerPending() {
        Offer offer = new Offer();
        offer.setId(OFFER_ID);
        offer.setWantPostId(POST_ID);
        offer.setUserId(SELLER_ID);
        offer.setPrice(new BigDecimal("22.50"));
        offer.setStatus(Offer.STATUS_PENDING);
        return offer;
    }

    private Offer offerAccepted() {
        Offer offer = offerPending();
        offer.setStatus(Offer.STATUS_ACCEPTED);
        offer.setOrderId(55L);
        return offer;
    }

    private void stubAcceptChain(Offer offer, WantPost post) {
        lenient().when(offerMapper.selectByIdForUpdate(OFFER_ID)).thenReturn(offer);
        lenient().when(wantPostMapper.selectByIdForUpdate(POST_ID)).thenReturn(post);
    }

    private OfferCreateDTO offerDto(String price, String message) {
        OfferCreateDTO dto = new OfferCreateDTO();
        dto.setPrice(new BigDecimal(price));
        dto.setMessage(message);
        return dto;
    }
}
