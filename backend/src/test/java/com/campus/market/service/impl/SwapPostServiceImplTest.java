package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.dto.SwapRequestCreateDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.SwapPost;
import com.campus.market.entity.SwapRequest;
import com.campus.market.entity.User;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.SwapPostMapper;
import com.campus.market.mapper.SwapRequestMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserAccessGuard;
import com.campus.market.service.NotificationService;
import com.campus.market.service.OrderService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.SwapRequestVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SwapPostServiceImpl 深测（SWP-01~03：发起 40912/40913/40916、同意交换 T8 原子链、差价入单）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SwapPostServiceImplTest {

    private static final Long OWNER_ID = 1L;      // 交换帖帖主
    private static final Long REQUESTER_ID = 2L;  // 交换请求发起方
    private static final Long POST_ID = 10L;
    private static final Long REQUEST_ID = 20L;

    @Mock SwapPostMapper swapPostMapper;
    @Mock SwapRequestMapper swapRequestMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock UserMapper userMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock GoodsImageMapper goodsImageMapper;
    @Mock OrderService orderService;
    @Mock NotificationService notificationService;
    @Mock SensitiveWordService sensitiveWordService;
    /** 真实守卫（阈值取自 CreditProperties 默认 60），与生产口径一致 */
    @Spy UserAccessGuard userAccessGuard = new UserAccessGuard(new CreditProperties());

    @InjectMocks SwapPostServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(SwapPost.class, SwapRequest.class, Category.class, User.class,
                Goods.class, GoodsImage.class, OrderInfo.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(sensitiveWordService.findHits(any())).thenReturn(List.of());
        lenient().when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        lenient().when(goodsImageMapper.selectList(any())).thenReturn(List.of());
    }

    // ==================== 发起交换（SWP-02） ====================

    @Test
    void createRequest_uncertified_rejected() {
        assertThatThrownBy(() -> service.createRequest(POST_ID, requestDto(null, "TI-84 九成新"),
                LoginUserTestFactory.uncertified(2L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_NOT_CERTIFIED));
    }

    @Test
    void createRequest_ownPost_rejected() {
        when(swapPostMapper.selectById(POST_ID)).thenReturn(post(SwapPost.STATUS_OPEN));
        assertThatThrownBy(() -> service.createRequest(POST_ID, requestDto(null, "TI-84 九成新"), owner()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_REQUEST_SELF));
    }

    @Test
    void createRequest_postClosed_rejected() {
        when(swapPostMapper.selectById(POST_ID)).thenReturn(post(SwapPost.STATUS_CLOSED));
        assertThatThrownBy(() -> service.createRequest(POST_ID, requestDto(null, "TI-84 九成新"), requester()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_POST_CLOSED));
    }

    @Test
    void createRequest_duplicatePending_rejected() {
        when(swapPostMapper.selectById(POST_ID)).thenReturn(post(SwapPost.STATUS_OPEN));
        when(swapRequestMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createRequest(POST_ID, requestDto(null, "TI-84 九成新"), requester()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_REQUEST_DUPLICATE));
    }

    @Test
    void createRequest_concurrentUkConflict_mapsToDuplicate() {
        when(swapPostMapper.selectById(POST_ID)).thenReturn(post(SwapPost.STATUS_OPEN));
        when(swapRequestMapper.selectCount(any())).thenReturn(0L);
        when(swapRequestMapper.insert(any(SwapRequest.class)))
                .thenThrow(new DuplicateKeyException("uk_swap_req_pending"));

        assertThatThrownBy(() -> service.createRequest(POST_ID, requestDto(null, "TI-84 九成新"), requester()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_REQUEST_DUPLICATE));
    }

    @Test
    void createRequest_success_notifiesOwner() {
        when(swapPostMapper.selectById(POST_ID)).thenReturn(post(SwapPost.STATUS_OPEN));
        when(swapRequestMapper.selectCount(any())).thenReturn(0L);
        when(swapRequestMapper.insert(any(SwapRequest.class))).thenReturn(1);

        service.createRequest(POST_ID, requestDto(null, "TI-84 九成新"), requester());

        verify(notificationService).push(eq(OWNER_ID), eq(Notification.TYPE_ORDER), eq("收到新的交换请求"),
                contains("收到一条交换请求"), any(), any());
    }

    // ==================== 同意交换（SWP-03 / T8 原子链） ====================

    @Test
    void acceptRequest_requestMissing_rejected() {
        when(swapRequestMapper.selectByIdForUpdate(REQUEST_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.acceptRequest(REQUEST_ID, owner()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_REQUEST_NOT_FOUND));
    }

    @Test
    void acceptRequest_notOwner_forbidden() {
        stubAcceptChain(request(0), post(SwapPost.STATUS_OPEN));
        assertThatThrownBy(() -> service.acceptRequest(REQUEST_ID, requester()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_POST_FORBIDDEN));
    }

    @Test
    void acceptRequest_postNotOpen_rejected() {
        stubAcceptChain(request(0), post(SwapPost.STATUS_CLOSED));
        assertThatThrownBy(() -> service.acceptRequest(REQUEST_ID, owner()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_POST_CLOSED));
    }

    @Test
    void acceptRequest_alreadyHandled_rejected() {
        SwapRequest handled = request(0);
        handled.setStatus(SwapRequest.STATUS_ACCEPTED);
        stubAcceptChain(handled, post(SwapPost.STATUS_OPEN));
        assertThatThrownBy(() -> service.acceptRequest(REQUEST_ID, owner()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SWAP_REQUEST_HANDLED));
    }

    @Test
    void acceptRequest_success_fullAtomicChain_withDiffAmount() {
        stubAcceptChain(request(0), post(SwapPost.STATUS_OPEN, new BigDecimal("5.00")));
        when(swapPostMapper.update(isNull(), any())).thenReturn(1);          // ② 帖→DEALT
        when(swapRequestMapper.update(isNull(), any())).thenReturn(1, 1);    // ③ 本单→ACCEPTED + ⑤ orderId
        when(swapRequestMapper.rejectOtherPending(POST_ID, REQUEST_ID)).thenReturn(1);
        when(swapRequestMapper.selectList(any())).thenReturn(List.of());     // others 快照
        when(swapRequestMapper.selectById(REQUEST_ID)).thenReturn(requestAccepted());
        OrderInfo order = new OrderInfo();
        order.setId(66L);
        order.setOrderNo("SH20261002000066");
        // ⑤ buyer=发起方(2)、seller=帖主(1)、SWAP、金额=差价 5.00
        when(orderService.createTransactionOrder(REQUESTER_ID, OWNER_ID, OrderInfo.TYPE_SWAP, null,
                new BigDecimal("5.00"))).thenReturn(order);

        SwapRequestVO vo = service.acceptRequest(REQUEST_ID, owner());

        // 锁序：先锁请求再锁帖
        InOrder inOrder = inOrder(swapRequestMapper, swapPostMapper);
        inOrder.verify(swapRequestMapper).selectByIdForUpdate(REQUEST_ID);
        inOrder.verify(swapPostMapper).selectByIdForUpdate(POST_ID);
        inOrder.verify(swapPostMapper).update(isNull(), any());
        inOrder.verify(swapRequestMapper).update(isNull(), any());

        verify(swapRequestMapper).rejectOtherPending(POST_ID, REQUEST_ID);
        verify(orderService).createTransactionOrder(REQUESTER_ID, OWNER_ID, OrderInfo.TYPE_SWAP, null,
                new BigDecimal("5.00"));
        // 双方通知：发起方"已被同意"+ 帖主方
        verify(notificationService).push(eq(REQUESTER_ID), eq(Notification.TYPE_ORDER), contains("已被同意"),
                any(), any(), any());
        assertThat(vo.getStatus()).isEqualTo(SwapRequest.STATUS_ACCEPTED);
    }

    @Test
    void acceptRequest_allowDiffNo_zeroAmount() {
        stubAcceptChain(request(0), post(SwapPost.STATUS_OPEN, null));   // 关差价
        when(swapPostMapper.update(isNull(), any())).thenReturn(1);
        when(swapRequestMapper.update(isNull(), any())).thenReturn(1, 1);
        when(swapRequestMapper.rejectOtherPending(POST_ID, REQUEST_ID)).thenReturn(0);
        when(swapRequestMapper.selectList(any())).thenReturn(List.of());
        when(swapRequestMapper.selectById(REQUEST_ID)).thenReturn(requestAccepted());
        when(orderService.createTransactionOrder(REQUESTER_ID, OWNER_ID, OrderInfo.TYPE_SWAP, null,
                BigDecimal.ZERO)).thenReturn(newOrderId());

        service.acceptRequest(REQUEST_ID, owner());

        verify(orderService).createTransactionOrder(REQUESTER_ID, OWNER_ID, OrderInfo.TYPE_SWAP, null,
                BigDecimal.ZERO);
    }

    // ==================== 辅助 ====================

    private LoginUser owner() {
        return LoginUserTestFactory.user(OWNER_ID);
    }

    private LoginUser requester() {
        return LoginUserTestFactory.user(REQUESTER_ID);
    }

    private SwapPost post(String status) {
        return post(status, null);
    }

    private SwapPost post(String status, BigDecimal diff) {
        SwapPost post = new SwapPost();
        post.setId(POST_ID);
        post.setUserId(OWNER_ID);
        post.setTitle("冒烟-交换计算器");
        post.setStatus(status);
        post.setAllowDiff(diff != null ? 1 : 0);
        post.setDiffAmount(diff);
        return post;
    }

    private SwapRequest request(int status) {
        SwapRequest request = new SwapRequest();
        request.setId(REQUEST_ID);
        request.setSwapPostId(POST_ID);
        request.setUserId(REQUESTER_ID);
        request.setItemDesc("德州仪器TI-84 九五新");
        request.setStatus(status);
        return request;
    }

    private SwapRequest requestAccepted() {
        SwapRequest request = request(SwapRequest.STATUS_ACCEPTED);
        request.setOrderId(66L);
        return request;
    }

    private void stubAcceptChain(SwapRequest request, SwapPost post) {
        lenient().when(swapRequestMapper.selectByIdForUpdate(REQUEST_ID)).thenReturn(request);
        lenient().when(swapPostMapper.selectByIdForUpdate(POST_ID)).thenReturn(post);
    }

    private OrderInfo newOrderId() {
        OrderInfo order = new OrderInfo();
        order.setId(66L);
        order.setOrderNo("SH20261002000066");
        return order;
    }

    private SwapRequestCreateDTO requestDto(Long goodsId, String desc) {
        SwapRequestCreateDTO dto = new SwapRequestCreateDTO();
        dto.setGoodsId(goodsId);
        dto.setItemDesc(desc);
        return dto;
    }
}
