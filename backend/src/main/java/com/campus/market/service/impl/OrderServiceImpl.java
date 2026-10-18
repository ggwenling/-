package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.GoodsWant;
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
import com.campus.market.service.OrderService;
import com.campus.market.vo.OrderDetailVO;
import com.campus.market.vo.OrderListVO;
import com.campus.market.vo.ReviewVO;
import com.campus.market.vo.SellerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务实现（PRD ORD-01~07 / §5.2 状态机 / 数据库设计文档 §3.11、§3.27、§3.9）。
 * 并发一致性策略：
 * - 商品锁定/解锁/售出、订单状态迁移全部为单语句条件 UPDATE（行锁），rows=0 即视为并发冲突或状态已变；
 * - 下单锁定（PRD §8.3 同一商品并发下单只成功一个）、取消解锁（§5.1 IN_TRANSACTION→ON_SALE）、完成售出（→SOLD）；
 * - 取号用 order_no_seq 两步式 SQL（T11，与订单同事务同连接）；
 * - "想要"首次写入与 want_count+1 同事务，uk_user_goods 冲突静默忽略（T5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderInfoMapper orderInfoMapper;
    private final OrderNoSeqMapper orderNoSeqMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsWantMapper goodsWantMapper;
    private final GoodsImageMapper goodsImageMapper;
    private final UserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final OfferMapper offerMapper;
    private final WantPostMapper wantPostMapper;
    private final SwapRequestMapper swapRequestMapper;
    private final SwapPostMapper swapPostMapper;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final UserAccessGuard userAccessGuard;

    // ==================== ORD-01 下单 ====================

    @Override
    @Transactional
    public OrderInfo createSaleOrder(LoginUser buyer, OrderCreateDTO dto) {
        // 认证 + 受限校验（PRD §3.1 / §4.1 / §5.7 CRD-02）：LoginUser 快照由拦截器每请求刷新，统一由 UserAccessGuard 承担
        userAccessGuard.requireInteractive(buyer);

        Goods goods = goodsMapper.selectById(dto.getGoodsId());
        if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        if (Objects.equals(goods.getUserId(), buyer.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN, "不能下单自己发布的商品");
        }
        return createTransactionOrder(buyer.getUserId(), goods.getUserId(),
                OrderInfo.TYPE_SALE, goods.getId(), goods.getPrice());
    }

    @Override
    @Transactional
    public OrderInfo createTransactionOrder(Long buyerId, Long sellerId, String type, Long goodsId, BigDecimal amount) {
        if (!OrderInfo.TYPE_PURCHASE.equals(type) && !OrderInfo.TYPE_SWAP.equals(type)
                && !OrderInfo.TYPE_SALE.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知订单类型：" + type);
        }
        if (Objects.equals(buyerId, sellerId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单双方不能为同一用户");
        }

        // SALE：仅 ON_SALE 可锁定为 IN_TRANSACTION，并发下单只有一次 UPDATE 生效（PRD §5.1/§8.3）
        // SWAP：交换成交同样占用实物，创建即锁定发起方商品（验收 P1：原不锁定，交换期间可被二次下单）；
        // 完成/取消/超时路径按 goodsId 条件迁移，无需按类型分支
        if (OrderInfo.TYPE_SALE.equals(type)
                || (OrderInfo.TYPE_SWAP.equals(type) && goodsId != null)) {
            int locked = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, goodsId)
                    .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                    .set(Goods::getStatus, Goods.STATUS_IN_TRANSACTION));
            if (locked == 0) {
                throw new BusinessException(ErrorCode.ORDER_GOODS_NOT_AVAILABLE);
            }
        }

        OrderInfo order = new OrderInfo();
        order.setOrderNo(generateOrderNo());
        order.setType(type);
        // SWAP 创建即 SCHEDULED，confirmed_at=创建时间（PRD §5.2 / ORD-07）；SALE/PURCHASE 待卖家确认
        if (OrderInfo.TYPE_SWAP.equals(type)) {
            order.setStatus(OrderInfo.STATUS_SCHEDULED);
            order.setConfirmedAt(LocalDateTime.now());
        } else {
            order.setStatus(OrderInfo.STATUS_WAIT_CONFIRM);
        }
        order.setGoodsId(goodsId);
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setAmount(amount == null ? BigDecimal.ZERO : amount);
        orderInfoMapper.insert(order);

        // 首次"想要"回链（T5）：与下单同事务，一人一商品一条，重复下单不再计数
        if (OrderInfo.TYPE_SALE.equals(type)) {
            recordGoodsWant(buyerId, goodsId, order.getId());
        }

        // 通知卖方有新订单（SWAP 语义为"帖主"，展示文案通用化）
        notificationService.push(sellerId, Notification.TYPE_ORDER, "新订单待处理",
                String.format("您有新的%s订单 %s，请及时处理", typeLabel(type), order.getOrderNo()),
                CreditLog.REF_TYPE_ORDER, order.getId());
        return order;
    }

    // ==================== ORD-02 确认 / 拒绝 ====================

    @Override
    @Transactional
    public OrderDetailVO confirm(Long orderId, LoginUser operator) {
        OrderInfo order = requireOrderAndParty(orderId, operator.getUserId());
        if (!Objects.equals(order.getSellerId(), operator.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN, "仅卖家可确认订单");
        }
        int rows = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_WAIT_CONFIRM)
                .set(OrderInfo::getStatus, OrderInfo.STATUS_SCHEDULED)
                .set(OrderInfo::getConfirmedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已被处理，无法重复确认");
        }
        notifyBoth(order, "订单已确认",
                "卖家已确认订单 %s，请与卖家约定面交时间地点",
                "您已确认订单 %s，请按约定与买家面交");
        return detail(orderId, operator.getUserId());
    }

    @Override
    @Transactional
    public OrderDetailVO reject(Long orderId, LoginUser operator, String reason) {
        OrderInfo order = requireOrderAndParty(orderId, operator.getUserId());
        if (!Objects.equals(order.getSellerId(), operator.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN, "仅卖家可拒绝订单");
        }
        int rows = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_WAIT_CONFIRM)
                .set(OrderInfo::getStatus, OrderInfo.STATUS_CANCELLED)
                .set(OrderInfo::getCancelledAt, LocalDateTime.now())
                .set(OrderInfo::getCancelledBy, OrderInfo.CANCELLED_BY_SELLER)
                .set(OrderInfo::getCancelReason, normalizeReason(reason, "卖家拒绝交易")));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已被处理，无法拒绝");
        }
        // 商品解锁回 ON_SALE（PRD §5.1：订单取消后才释放锁定）
        unlockGoods(order.getGoodsId());
        notifyBoth(order, "订单被拒绝",
                "卖家拒绝了订单 %s：" + normalizeReason(reason, "卖家拒绝交易") + "，商品已重新上架",
                "您已拒绝订单 %s，商品已重新上架");
        return detail(orderId, operator.getUserId());
    }

    // ==================== ORD-03 取消 ====================

    @Override
    @Transactional
    public OrderDetailVO cancel(Long orderId, LoginUser operator, String reason) {
        OrderInfo order = requireOrderAndParty(orderId, operator.getUserId());
        if (!Objects.equals(order.getBuyerId(), operator.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN, "仅买家可取消订单");
        }
        int rows = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .in(OrderInfo::getStatus, OrderInfo.STATUS_WAIT_CONFIRM, OrderInfo.STATUS_SCHEDULED)
                .set(OrderInfo::getStatus, OrderInfo.STATUS_CANCELLED)
                .set(OrderInfo::getCancelledAt, LocalDateTime.now())
                .set(OrderInfo::getCancelledBy, OrderInfo.CANCELLED_BY_BUYER)
                .set(OrderInfo::getCancelReason, normalizeReason(reason, "买家主动取消")));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单当前状态不可取消");
        }
        unlockGoods(order.getGoodsId());
        notifyBoth(order, "订单已取消",
                "买家取消了订单 %s：" + normalizeReason(reason, "买家主动取消"),
                "买家已取消订单 %s，商品已重新上架");
        return detail(orderId, operator.getUserId());
    }

    // ==================== ORD-04 确认完成 ====================

    @Override
    @Transactional
    public OrderDetailVO complete(Long orderId, LoginUser operator) {
        OrderInfo order = requireOrderAndParty(orderId, operator.getUserId());
        if (OrderInfo.TYPE_SWAP.equals(order.getType())) {
            return completeSwap(order, operator.getUserId());
        }
        // SALE/PURCHASE：卖家确认收款即完成（T7 完成确认规则表）
        if (!Objects.equals(order.getSellerId(), operator.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN, "仅卖家可确认完成");
        }
        int rows = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_SCHEDULED)
                .isNull(OrderInfo::getSellerConfirmedAt)
                .set(OrderInfo::getStatus, OrderInfo.STATUS_COMPLETED)
                .set(OrderInfo::getSellerConfirmedAt, LocalDateTime.now())
                .set(OrderInfo::getCompletedAt, LocalDateTime.now()));
        if (rows == 0) {
            // 并发/重复点击兜底：已由对方或自己完成则幂等成功，否则状态非法
            OrderInfo latest = orderInfoMapper.selectById(orderId);
            if (latest == null || !OrderInfo.STATUS_COMPLETED.equals(latest.getStatus())) {
                throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单当前状态不可确认完成");
            }
        } else {
            finishOrderRewards(order);
        }
        return detail(orderId, operator.getUserId());
    }

    /** SWAP 双确认（T7）：双方各自写入完成确认时间，都写入才 COMPLETED */
    private OrderDetailVO completeSwap(OrderInfo order, Long userId) {
        boolean isBuyer = Objects.equals(order.getBuyerId(), userId);
        int rows = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, order.getId())
                .isNull(isBuyer ? OrderInfo::getBuyerConfirmedAt : OrderInfo::getSellerConfirmedAt)
                .set(isBuyer ? OrderInfo::getBuyerConfirmedAt : OrderInfo::getSellerConfirmedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "您已确认过，请等待对方确认");
        }
        OrderInfo latest = orderInfoMapper.selectById(order.getId());
        Long counterpartId = isBuyer ? order.getSellerId() : order.getBuyerId();
        if (OrderInfo.STATUS_COMPLETED.equals(latest.getStatus())) {
            // 对方在同一瞬间完成最后一确认：本次仅为补记时间戳，幂等返回
            return detail(order.getId(), userId);
        }
        if (latest.getBuyerConfirmedAt() != null && latest.getSellerConfirmedAt() != null) {
            int done = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                    .eq(OrderInfo::getId, order.getId())
                    .eq(OrderInfo::getStatus, OrderInfo.STATUS_SCHEDULED)
                    .set(OrderInfo::getStatus, OrderInfo.STATUS_COMPLETED)
                    .set(OrderInfo::getCompletedAt, LocalDateTime.now()));
            if (done > 0) {
                finishOrderRewards(latest);
                return detail(order.getId(), userId);
            }
        }
        // 尚未双确认：提示对方完成（PRD §5.5 双方分别确认）
        notificationService.push(counterpartId, Notification.TYPE_ORDER, "交换对方已确认",
                String.format("交换订单 %s 对方已确认收到物品，请尽快确认完成交易", order.getOrderNo()),
                CreditLog.REF_TYPE_ORDER, order.getId());
        return detail(order.getId(), userId);
    }

    /** 完成奖励（PRD ORD-04）：商品 SOLD + 双方信用 +2（ORDER_COMPLETE）+ 双方通知，同事务 */
    private void finishOrderRewards(OrderInfo order) {
        if (order.getGoodsId() != null) {
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, order.getGoodsId())
                    .eq(Goods::getStatus, Goods.STATUS_IN_TRANSACTION)
                    .set(Goods::getStatus, Goods.STATUS_SOLD));
            // 成交行为埋点（PRD §6.6：成交权重 5，推荐输入），仅商品类订单记录
            recordOrderDoneBehavior(order.getBuyerId(), order.getGoodsId());
        }
        creditService.addCredit(order.getBuyerId(), CreditLog.REASON_ORDER_COMPLETE,
                CreditLog.REF_TYPE_ORDER, order.getId());
        creditService.addCredit(order.getSellerId(), CreditLog.REASON_ORDER_COMPLETE,
                CreditLog.REF_TYPE_ORDER, order.getId());
        notifyBoth(order, "订单已完成",
                "订单 %s 已完成，信用分 +2，欢迎在 7 天内互评",
                "订单 %s 已完成，信用分 +2，欢迎在 7 天内互评");
    }

    /** 成交行为埋点（REC-01 输入）：user_behavior(ORDER_DONE, 当日)；仅在本方法内调用，随完成事务提交一次 */
    private void recordOrderDoneBehavior(Long userId, Long goodsId) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setGoodsId(goodsId);
        behavior.setBehavior(UserBehavior.BEHAVIOR_ORDER_DONE);
        behavior.setBehaviorDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        userBehaviorMapper.insert(behavior);
    }

    // ==================== ORD-03 超时自动取消 ====================

    @Override
    @Transactional
    public int autoCancelTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now();
        int cancelled = 0;
        // WAIT_CONFIRM 超 created_at+48h（PRD ORD-03）
        List<OrderInfo> waitExpired = orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_WAIT_CONFIRM)
                .le(OrderInfo::getCreatedAt, now.minusHours(OrderInfo.WAIT_CONFIRM_TIMEOUT_HOURS)));
        for (OrderInfo order : waitExpired) {
            cancelled += cancelByTimeout(order, now);
        }
        // SCHEDULED 超 confirmed_at+15d（PRD ORD-03）
        List<OrderInfo> scheduledExpired = orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_SCHEDULED)
                .le(OrderInfo::getConfirmedAt, now.minusDays(OrderInfo.SCHEDULED_TIMEOUT_DAYS)));
        for (OrderInfo order : scheduledExpired) {
            cancelled += cancelByTimeout(order, now);
        }
        return cancelled;
    }

    /** 单笔超时取消：条件更新防重复执行，成功后解锁商品 + 卖方信用 -2 + 双方通知 */
    private int cancelByTimeout(OrderInfo order, LocalDateTime now) {
        int rows = orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, order.getId())
                .eq(OrderInfo::getStatus, order.getStatus())
                .set(OrderInfo::getStatus, OrderInfo.STATUS_CANCELLED)
                .set(OrderInfo::getCancelledAt, now)
                .set(OrderInfo::getCancelledBy, OrderInfo.CANCELLED_BY_TIMEOUT)
                .set(OrderInfo::getCancelReason, "超时未确认，系统自动取消"));
        if (rows == 0) {
            return 0;
        }
        unlockGoods(order.getGoodsId());
        creditService.addCredit(order.getSellerId(), CreditLog.REASON_CANCEL_TIMEOUT,
                CreditLog.REF_TYPE_ORDER, order.getId());
        notificationService.push(order.getBuyerId(), Notification.TYPE_ORDER, "订单超时自动取消",
                String.format("订单 %s 超时未确认，已自动取消", order.getOrderNo()),
                CreditLog.REF_TYPE_ORDER, order.getId());
        notificationService.push(order.getSellerId(), Notification.TYPE_ORDER, "订单超时自动取消",
                String.format("订单 %s 超时未确认，已自动取消，您的信用分 -2", order.getOrderNo()),
                CreditLog.REF_TYPE_ORDER, order.getId());
        log.info("订单超时自动取消：orderId={}, oldStatus={}", order.getId(), order.getStatus());
        return 1;
    }

    // ==================== ORD-05 列表 / 详情 ====================

    @Override
    public PageResult<OrderListVO> pageMyOrders(Long viewerId, String role, String status, long pageNum, long pageSize) {
        if (!"buyer".equals(role) && !"seller".equals(role)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "role 仅支持 buyer/seller");
        }
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq("buyer".equals(role) ? OrderInfo::getBuyerId : OrderInfo::getSellerId, viewerId)
                .orderByDesc(OrderInfo::getCreatedAt)
                .orderByDesc(OrderInfo::getId);
        if (status != null && !status.isBlank()) {
            validateStatus(status);
            wrapper.eq(OrderInfo::getStatus, status);
        }
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        IPage<OrderInfo> result = orderInfoMapper.selectPage(new Page<>(Math.max(pageNum, 1), pageSize), wrapper);

        List<OrderInfo> records = result.getRecords();
        Map<Long, Goods> goodsMap = loadGoods(records);
        Map<Long, String> coverMap = loadCovers(records);
        Map<Long, User> userMap = loadUsers(records.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getBuyerId(), o.getSellerId())).distinct().toList());
        Set<Long> reviewedOrderIds = loadReviewedOrderIds(records, viewerId);
        Map<Long, String> sourceTitles = loadSourceTitles(records);

        List<OrderListVO> voList = records.stream()
                .map(order -> buildListVO(new OrderListVO(), order, viewerId, goodsMap, coverMap, userMap,
                        reviewedOrderIds, sourceTitles))
                .toList();
        PageResult<OrderListVO> voPage = new PageResult<>();
        voPage.setList(voList);
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    @Override
    public OrderDetailVO detail(Long orderId, Long viewerId) {
        OrderInfo order = requireOrderAndParty(orderId, viewerId);
        Map<Long, Goods> goodsMap = loadGoods(List.of(order));
        Map<Long, String> coverMap = loadCovers(List.of(order));
        Map<Long, User> userMap = loadUsers(List.of(order.getBuyerId(), order.getSellerId()));

        // OrderDetailVO extends OrderListVO：行卡字段直接装配进详情 VO，再补详情独有字段（取代旧 copyBase 手工逐字段复制）
        OrderDetailVO vo = buildListVO(new OrderDetailVO(), order, viewerId, goodsMap, coverMap, userMap,
                loadReviewedOrderIds(List.of(order), viewerId), loadSourceTitles(List.of(order)));
        vo.setConfirmedAt(order.getConfirmedAt());
        vo.setBuyerConfirmedAt(order.getBuyerConfirmedAt());
        vo.setSellerConfirmedAt(order.getSellerConfirmedAt());
        vo.setCancelledBy(order.getCancelledBy());
        vo.setCancelReason(order.getCancelReason());
        vo.setSeller(toSellerVO(userMap.get(order.getSellerId())));
        vo.setBuyer(toSellerVO(userMap.get(order.getBuyerId())));
        vo.setReviews(loadOrderReviews(orderId));
        return vo;
    }

    // ==================== 私有辅助 ====================

    /** T11 两步式取号（同事务同连接）：SH+yyyyMMdd+6 位序号，超 999999 自动扩 7 位 */
    private String generateOrderNo() {
        LocalDate today = LocalDate.now();
        orderNoSeqMapper.insertIgnore(today);
        orderNoSeqMapper.incrementAndGet(today);
        long seq = orderNoSeqMapper.selectLastInsertId();
        return OrderInfo.ORDER_NO_PREFIX + today.format(ORDER_NO_DATE) + String.format("%06d", seq);
    }

    /** "想要"事实写入（T5）：一人一商品一条，uk 冲突=曾经想要过，不重复计数，不回填首次 order_id */
    private void recordGoodsWant(Long userId, Long goodsId, Long orderId) {
        GoodsWant want = new GoodsWant();
        want.setUserId(userId);
        want.setGoodsId(goodsId);
        want.setOrderId(orderId);
        try {
            int rows = goodsWantMapper.insert(want);
            if (rows > 0) {
                goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, goodsId)
                        .setSql("want_count = want_count + 1"));
            }
        } catch (DuplicateKeyException e) {
            log.debug("重复想要并发冲突，幂等忽略：userId={}, goodsId={}", userId, goodsId);
        }
    }

    /** 商品解锁回 ON_SALE（PRD §5.1：仅 IN_TRANSACTION 解锁，SOLD 不动） */
    private void unlockGoods(Long goodsId) {
        if (goodsId == null) {
            return;
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .eq(Goods::getStatus, Goods.STATUS_IN_TRANSACTION)
                .set(Goods::getStatus, Goods.STATUS_ON_SALE));
    }

    private OrderInfo requireOrderAndParty(Long orderId, Long viewerId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getBuyerId(), viewerId) && !Objects.equals(order.getSellerId(), viewerId)) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN);
        }
        return order;
    }

    private void validateStatus(String status) {
        Set<String> valid = Set.of(OrderInfo.STATUS_WAIT_CONFIRM, OrderInfo.STATUS_SCHEDULED,
                OrderInfo.STATUS_COMPLETED, OrderInfo.STATUS_CANCELLED);
        if (!valid.contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知订单状态：" + status);
        }
    }

    private void notifyBoth(OrderInfo order, String title, String buyerContent, String sellerContent) {
        String orderNo = order.getOrderNo();
        notificationService.push(order.getBuyerId(), Notification.TYPE_ORDER, title,
                String.format(buyerContent, orderNo), CreditLog.REF_TYPE_ORDER, order.getId());
        notificationService.push(order.getSellerId(), Notification.TYPE_ORDER, title,
                String.format(sellerContent, orderNo), CreditLog.REF_TYPE_ORDER, order.getId());
    }

    private String normalizeReason(String reason, String defaultReason) {
        return reason == null || reason.isBlank() ? defaultReason : reason.trim();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case OrderInfo.TYPE_SALE -> "出售";
            case OrderInfo.TYPE_PURCHASE -> "求购";
            case OrderInfo.TYPE_SWAP -> "交换";
            default -> type;
        };
    }

    // ---------- VO 装配 ----------

    /** 行卡公共字段装配：目标 VO 由调用方传入（列表传 OrderListVO、详情传 OrderDetailVO，后者继承前者） */
    private <T extends OrderListVO> T buildListVO(T vo, OrderInfo order, Long viewerId, Map<Long, Goods> goodsMap,
                                    Map<Long, String> coverMap, Map<Long, User> userMap,
                                    Set<Long> reviewedOrderIds, Map<Long, String> sourceTitles) {
        boolean isBuyer = Objects.equals(order.getBuyerId(), viewerId);
        Long counterpartId = isBuyer ? order.getSellerId() : order.getBuyerId();
        User counterpart = userMap.get(counterpartId);

        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setType(order.getType());
        vo.setStatus(order.getStatus());
        vo.setGoodsId(order.getGoodsId());
        Goods goods = order.getGoodsId() == null ? null : goodsMap.get(order.getGoodsId());
        if (goods != null) {
            vo.setGoodsTitle(goods.getTitle());
            vo.setGoodsCoverUrl(coverMap.get(goods.getId()));
        } else {
            // PURCHASE/SWAP 订单无关联商品：标题回溯来源帖子（PRD §4.3 订单来源可追溯）
            vo.setGoodsTitle(sourceTitles.get(order.getId()));
        }
        vo.setAmount(order.getAmount());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setViewRole(isBuyer ? "buyer" : "seller");
        vo.setCounterpartId(counterpartId);
        if (counterpart != null) {
            vo.setCounterpartNickname(counterpart.getNickname());
            vo.setCounterpartAvatar(counterpart.getAvatar());
            vo.setCounterpartCreditScore(counterpart.getCreditScore());
        }
        vo.setCreatedAt(order.getCreatedAt());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setCancelledAt(order.getCancelledAt());

        boolean hasReviewed = reviewedOrderIds.contains(order.getId());
        vo.setHasReviewedByMe(hasReviewed);
        vo.setCanConfirm(isBuyer ? false : OrderInfo.STATUS_WAIT_CONFIRM.equals(order.getStatus()));
        vo.setCanReject(isBuyer ? false : OrderInfo.STATUS_WAIT_CONFIRM.equals(order.getStatus()));
        vo.setCanCancel(isBuyer && (OrderInfo.STATUS_WAIT_CONFIRM.equals(order.getStatus())
                || OrderInfo.STATUS_SCHEDULED.equals(order.getStatus())));
        boolean scheduled = OrderInfo.STATUS_SCHEDULED.equals(order.getStatus());
        boolean canComplete;
        if (OrderInfo.TYPE_SWAP.equals(order.getType())) {
            canComplete = scheduled && (isBuyer
                    ? order.getBuyerConfirmedAt() == null : order.getSellerConfirmedAt() == null);
        } else {
            canComplete = scheduled && !isBuyer;
        }
        vo.setCanComplete(canComplete);
        vo.setCanReview(OrderInfo.STATUS_COMPLETED.equals(order.getStatus()) && !hasReviewed
                && withinReviewWindow(order));
        return vo;
    }

    private boolean withinReviewWindow(OrderInfo order) {
        return order.getCompletedAt() != null
                && order.getCompletedAt().plusDays(Review.REVIEW_WINDOW_DAYS).isAfter(LocalDateTime.now());
    }

    private List<ReviewVO> loadOrderReviews(Long orderId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId)
                .orderByAsc(Review::getCreatedAt));
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<Long, User> reviewerMap = loadUsers(reviews.stream().map(Review::getReviewerId).distinct().toList());
        return reviews.stream().map(review -> toReviewVO(review, reviewerMap)).toList();
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

    private SellerVO toSellerVO(User user) {
        if (user == null) {
            return null;
        }
        SellerVO vo = new SellerVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setCreditScore(user.getCreditScore());
        vo.setAuthStatus(user.getAuthStatus());
        return vo;
    }

    /**
     * 订单来源标题回溯（PRD §4.3：每个订单必须能追溯到唯一业务来源）。
     * PURCHASE：offer.order_id = 订单 ID → want_post.title；SWAP：swap_request.order_id = 订单 ID → swap_post.title。
     * order_info 无标题快照列，good 因软删不物理删除故 SALE 订单可由 goods_id 追溯，此处补齐非商品类订单。
     */
    private Map<Long, String> loadSourceTitles(List<OrderInfo> orders) {
        List<Long> orderIds = orders.stream()
                .filter(order -> order.getGoodsId() == null)
                .map(OrderInfo::getId)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titles = new java.util.HashMap<>();
        List<Offer> offers = offerMapper.selectList(new LambdaQueryWrapper<Offer>()
                .in(Offer::getOrderId, orderIds));
        if (!offers.isEmpty()) {
            Map<Long, WantPost> posts = wantPostMapper.selectBatchIds(offers.stream()
                            .map(Offer::getWantPostId).distinct().toList()).stream()
                    .collect(Collectors.toMap(WantPost::getId, Function.identity(), (a, b) -> a));
            offers.forEach(offer -> {
                WantPost post = posts.get(offer.getWantPostId());
                if (post != null) {
                    titles.put(offer.getOrderId(), post.getTitle());
                }
            });
        }
        List<SwapRequest> requests = swapRequestMapper.selectList(new LambdaQueryWrapper<SwapRequest>()
                .in(SwapRequest::getOrderId, orderIds));
        if (!requests.isEmpty()) {
            Map<Long, SwapPost> posts = swapPostMapper.selectBatchIds(requests.stream()
                            .map(SwapRequest::getSwapPostId).distinct().toList()).stream()
                    .collect(Collectors.toMap(SwapPost::getId, Function.identity(), (a, b) -> a));
            requests.forEach(request -> {
                SwapPost post = posts.get(request.getSwapPostId());
                if (post != null) {
                    titles.putIfAbsent(request.getOrderId(), post.getTitle());
                }
            });
        }
        return titles;
    }

    private Map<Long, Goods> loadGoods(List<OrderInfo> orders) {
        List<Long> goodsIds = orders.stream().map(OrderInfo::getGoodsId)
                .filter(Objects::nonNull).distinct().toList();
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        return goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));
    }

    /** goods_id -> sort 最小的缩略图（封面） */
    private Map<Long, String> loadCovers(List<OrderInfo> orders) {
        List<Long> goodsIds = orders.stream().map(OrderInfo::getGoodsId)
                .filter(Objects::nonNull).distinct().toList();
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        return goodsImageMapper.selectList(new LambdaQueryWrapper<GoodsImage>()
                        .in(GoodsImage::getGoodsId, goodsIds)
                        .orderByAsc(GoodsImage::getSort))
                .stream()
                .collect(Collectors.toMap(GoodsImage::getGoodsId, GoodsImage::getThumbUrl, (a, b) -> a));
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 当前查看者在订单列表范围内已评价的订单 ID 集（"去评价"按钮显隐） */
    private Set<Long> loadReviewedOrderIds(List<OrderInfo> orders, Long viewerId) {
        List<Long> orderIds = orders.stream().map(OrderInfo::getId).toList();
        if (orderIds.isEmpty()) {
            return Set.of();
        }
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .in(Review::getOrderId, orderIds)
                        .eq(Review::getReviewerId, viewerId))
                .stream().map(Review::getOrderId).collect(Collectors.toSet());
    }
}
