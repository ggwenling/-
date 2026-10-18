package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.OfferCreateDTO;
import com.campus.market.dto.WantPostListQuery;
import com.campus.market.dto.WantPostPublishDTO;
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
import com.campus.market.service.WantPostService;
import com.campus.market.vo.OfferVO;
import com.campus.market.vo.SellerVO;
import com.campus.market.vo.WantPostCardVO;
import com.campus.market.vo.WantPostDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 求购服务实现（PRD REQ-01~04、§5.4）。
 * 接受应约的原子性（数据库设计文档 §3.12 T8）：同一事务内 ①锁帖 ②帖置 DEALT ③本应约置已接受并回填
 * order_id ④其余待处理应约全部置已拒绝 ⑤创建 PURCHASE 订单；条件更新 + 行锁防重复接受。
 * 敏感词命中复用 GOODS_SENSITIVE(40001) 语义（与 M2 发布、M3 评价、M4 聊天口径一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WantPostServiceImpl implements WantPostService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final WantPostMapper wantPostMapper;
    private final OfferMapper offerMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final SensitiveWordService sensitiveWordService;
    private final UserAccessGuard userAccessGuard;

    // ==================== REQ-01 发布 / REQ-02 编辑 ====================

    @Override
    @Transactional
    public Long publish(WantPostPublishDTO dto, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        Category category = requireValidCategory(dto.getCategoryId());
        validateBudget(dto.getBudget());
        checkSensitive("标题", dto.getTitle());
        checkSensitive("求购描述", dto.getDescription());

        WantPost post = new WantPost();
        post.setUserId(user.getUserId());
        post.setCategoryId(category.getId());
        post.setTitle(dto.getTitle().trim());
        post.setDescription(dto.getDescription().trim());
        post.setBudget(dto.getBudget());
        post.setStatus(WantPost.STATUS_OPEN);
        wantPostMapper.insert(post);
        return post.getId();
    }

    @Override
    @Transactional
    public Long update(Long id, WantPostPublishDTO dto, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        WantPost post = requireExisting(id);
        requireOwner(post.getUserId(), user.getUserId());
        if (!WantPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED, "仅求购中的帖子可以编辑");
        }
        Category category = requireValidCategory(dto.getCategoryId());
        validateBudget(dto.getBudget());
        checkSensitive("标题", dto.getTitle());
        checkSensitive("求购描述", dto.getDescription());

        wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                .eq(WantPost::getId, id)
                .set(WantPost::getCategoryId, category.getId())
                .set(WantPost::getTitle, dto.getTitle().trim())
                .set(WantPost::getDescription, dto.getDescription().trim())
                .set(WantPost::getBudget, dto.getBudget()));
        return id;
    }

    @Override
    @Transactional
    public void close(Long id, LoginUser user, String reason) {
        WantPost post = requireExisting(id);
        requireOwner(post.getUserId(), user.getUserId());
        if (!WantPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED, "仅求购中的帖子可以关闭");
        }
        int rows = wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                .eq(WantPost::getId, id)
                .eq(WantPost::getStatus, WantPost.STATUS_OPEN)
                .set(WantPost::getStatus, WantPost.STATUS_CLOSED));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED, "帖子状态已变化，请刷新后重试");
        }
        // 关闭后该帖待处理应约统一拒绝（PRD §6.3：帖子关闭后禁止新增应约）
        List<Offer> pending = pendingOffers(id);
        if (!pending.isEmpty()) {
            offerMapper.rejectOtherPending(id, -1L);
            pending.forEach(offer -> notificationService.push(offer.getUserId(), Notification.TYPE_ORDER,
                    "求购帖已关闭", String.format("求购帖「%s」已被发布者关闭，您的应约已失效", post.getTitle()),
                    null, null));
        }
        log.info("求购帖关闭：postId={}, 理由={}", id, reason);
    }

    @Override
    @Transactional
    public void removeByOwner(Long id, LoginUser user) {
        WantPost post = requireExisting(id);
        requireOwner(post.getUserId(), user.getUserId());
        // T4 删除规则：已成交（存在订单）的帖子禁止删除，保留事实可追溯
        if (WantPost.STATUS_DEALT.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED, "已成交的求购帖不能删除");
        }
        wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                .eq(WantPost::getId, id)
                .set(WantPost::getStatus, WantPost.STATUS_DELETED)
                .set(WantPost::getDeletedAt, LocalDateTime.now(BUSINESS_ZONE))
                .set(WantPost::getDeletedByType, WantPost.DELETED_BY_USER)
                .set(WantPost::getDeletedBy, user.getUserId())
                .set(WantPost::getDeleteReason, WantPost.USER_DELETE_REASON));
    }

    @Override
    @Transactional
    public void restore(Long id, LoginUser user) {
        WantPost post = wantPostMapper.selectById(id);
        if (post == null || !WantPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
        }
        // T4 恢复规则：仅帖主自行删除（deleted_by_type=USER）且 30 天内可自行恢复
        if (!WantPost.DELETED_BY_USER.equals(post.getDeletedByType())
                || !Objects.equals(post.getDeletedBy(), user.getUserId())) {
            throw new BusinessException(ErrorCode.WANT_POST_FORBIDDEN, "只能恢复自己删除的求购帖");
        }
        if (post.getDeletedAt() == null
                || post.getDeletedAt().isBefore(LocalDateTime.now(BUSINESS_ZONE).minusDays(30))) {
            throw new BusinessException(ErrorCode.WANT_POST_FORBIDDEN, "已删除超过 30 天，无法自行恢复");
        }
        wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                .eq(WantPost::getId, id)
                .set(WantPost::getStatus, WantPost.STATUS_CLOSED)
                .set(WantPost::getDeletedAt, null)
                .set(WantPost::getDeletedByType, null)
                .set(WantPost::getDeletedBy, null)
                .set(WantPost::getDeleteReason, null));
    }

    // ==================== REQ-02 广场与详情 ====================

    @Override
    public PageResult<WantPostCardVO> pageList(WantPostListQuery query, LoginUser viewer) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        String status = normalizeStatus(query.getStatus());

        LambdaQueryWrapper<WantPost> wrapper = new LambdaQueryWrapper<WantPost>()
                .eq(WantPost::getStatus, status);
        if (Boolean.TRUE.equals(query.getMine())) {
            wrapper.eq(WantPost::getUserId, requireViewerId(viewer));
        }
        List<Long> categoryIds = resolveCategoryIds(query.getCategoryId());
        if (categoryIds != null) {
            wrapper.in(WantPost::getCategoryId, categoryIds);
        }
        if (query.getQ() != null && !query.getQ().isBlank()) {
            String q = query.getQ().trim();
            wrapper.and(w -> w.like(WantPost::getTitle, q).or().like(WantPost::getDescription, q));
        }
        if (WantPostListQuery.SORT_BUDGET_DESC.equals(query.getSort())) {
            // 预算高到低；面议（NULL）排在最后
            wrapper.orderByDesc(WantPost::getBudget).orderByDesc(WantPost::getId);
        } else {
            wrapper.orderByDesc(WantPost::getCreatedAt).orderByDesc(WantPost::getId);
        }

        IPage<WantPost> result = wantPostMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<WantPost> records = result.getRecords();
        if (records.isEmpty()) {
            return buildVoPage(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        // 批量预取（一次查询/一项），避免在 stream 内逐条查库
        List<Long> postIds = records.stream().map(WantPost::getId).toList();
        Map<Long, SellerVO> publisherMap = loadPublishers(records);
        Map<Long, Category> categoryMap = loadCategoryMap(records);
        Map<Long, Long> countMap = offerCounts(postIds);
        Map<Long, Integer> myStatusMap = myOfferStatuses(postIds, viewer == null ? null : viewer.getUserId());

        List<WantPostCardVO> voList = records.stream()
                .map(post -> toCard(post, publisherMap, categoryMap, countMap, myStatusMap))
                .toList();
        return buildVoPage(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public WantPostDetailVO detail(Long id, LoginUser viewer) {
        WantPost post = wantPostMapper.selectById(id);
        boolean isOwner = viewer != null && post != null && Objects.equals(post.getUserId(), viewer.getUserId());
        if (post == null || (WantPost.STATUS_DELETED.equals(post.getStatus()) && !isOwner)) {
            throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
        }
        Long viewerId = viewer == null ? null : viewer.getUserId();

        WantPostDetailVO vo = new WantPostDetailVO();
        fillCard(vo, post, loadPublishers(List.of(post)), loadCategoryMap(List.of(post)),
                offerCounts(List.of(id)), myOfferStatuses(List.of(id), viewerId));
        vo.setUpdatedAt(post.getUpdatedAt());

        List<Offer> allOffers = offerMapper.selectList(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getWantPostId, id)
                .orderByAsc(Offer::getStatus)
                .orderByDesc(Offer::getCreatedAt)
                .orderByDesc(Offer::getId));
        Map<Long, User> offerUsers = loadUsers(allOffers.stream().map(Offer::getUserId).toList());
        if (isOwner) {
            vo.setOffers(allOffers.stream().map(offer -> toOfferVO(offer, post, offerUsers)).toList());
        }
        if (viewerId != null) {
            Offer mine = allOffers.stream()
                    .filter(offer -> Objects.equals(offer.getUserId(), viewerId))
                    .findFirst().orElse(null);
            vo.setMyOffer(mine == null ? null : toOfferVO(mine, post, offerUsers));
            vo.setHasPendingOffer(mine != null && mine.getStatus() == Offer.STATUS_PENDING);
        }
        // 成交订单回链（DEALT 时被接受应约已回填 order_id）
        allOffers.stream()
                .filter(offer -> offer.getStatus() == Offer.STATUS_ACCEPTED && offer.getOrderId() != null)
                .findFirst()
                .ifPresent(offer -> vo.setOrderId(offer.getOrderId()));
        vo.setCanManage(isOwner && WantPost.STATUS_OPEN.equals(post.getStatus()));
        vo.setCanOffer(userAccessGuard.canInteract(viewer) && !isOwner && WantPost.STATUS_OPEN.equals(post.getStatus()));
        return vo;
    }

    // ==================== REQ-03 应约与接受 ====================

    @Override
    @Transactional
    public OfferVO createOffer(Long postId, OfferCreateDTO dto, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        WantPost post = wantPostMapper.selectById(postId);
        if (post == null || WantPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
        }
        if (!WantPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED);
        }
        if (Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.WANT_OFFER_SELF);
        }
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.WANT_PARAM_INVALID, "报价不能为负数");
        }
        checkSensitive("留言", dto.getMessage());

        // 一人一帖最多一条待处理应约（T8）：先查后插，并发由 uk_offer_pending 兜底
        Long pending = offerMapper.selectCount(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getWantPostId, postId)
                .eq(Offer::getUserId, user.getUserId())
                .eq(Offer::getStatus, Offer.STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.WANT_OFFER_DUPLICATE);
        }

        Offer offer = new Offer();
        offer.setWantPostId(postId);
        offer.setUserId(user.getUserId());
        offer.setPrice(dto.getPrice());
        offer.setMessage(dto.getMessage() == null || dto.getMessage().isBlank() ? null : dto.getMessage().trim());
        offer.setStatus(Offer.STATUS_PENDING);
        try {
            offerMapper.insert(offer);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.WANT_OFFER_DUPLICATE);
        }
        notificationService.push(post.getUserId(), Notification.TYPE_ORDER, "收到新的应约",
                String.format("您的求购帖「%s」收到一条应约，报价 ¥%s，请及时处理", post.getTitle(), dto.getPrice()),
                null, null);
        return toOfferVO(offer, post, loadUsers(List.of(user.getUserId())));
    }

    @Override
    @Transactional
    public void withdrawOffer(Long offerId, LoginUser user) {
        Offer offer = offerMapper.selectById(offerId);
        if (offer == null) {
            throw new BusinessException(ErrorCode.OFFER_NOT_FOUND);
        }
        if (!Objects.equals(offer.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.WANT_POST_FORBIDDEN, "只能撤回自己的应约");
        }
        int rows = offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offerId)
                .eq(Offer::getStatus, Offer.STATUS_PENDING)
                .set(Offer::getStatus, Offer.STATUS_WITHDRAWN));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.WANT_OFFER_HANDLED, "应约已处理，无法撤回");
        }
    }

    @Override
    @Transactional
    public OfferVO acceptOffer(Long offerId, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        // ① 锁应约（固定加锁顺序：应约 → 帖，避免死锁）
        Offer locked = offerMapper.selectByIdForUpdate(offerId);
        if (locked == null) {
            throw new BusinessException(ErrorCode.OFFER_NOT_FOUND);
        }
        WantPost post = wantPostMapper.selectByIdForUpdate(locked.getWantPostId());
        if (post == null || WantPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.WANT_POST_FORBIDDEN, "仅求购者可接受应约");
        }
        if (!WantPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED, "帖子已关闭或已成交");
        }
        if (locked.getStatus() != Offer.STATUS_PENDING) {
            throw new BusinessException(ErrorCode.WANT_OFFER_HANDLED);
        }

        // ② 帖子置 DEALT（条件更新兜底并发）
        int closed = wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                .eq(WantPost::getId, post.getId())
                .eq(WantPost::getStatus, WantPost.STATUS_OPEN)
                .set(WantPost::getStatus, WantPost.STATUS_DEALT));
        if (closed == 0) {
            throw new BusinessException(ErrorCode.WANT_POST_CLOSED, "帖子状态已变化，请刷新后重试");
        }
        // ③ 本应约置已接受
        int accepted = offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offerId)
                .eq(Offer::getStatus, Offer.STATUS_PENDING)
                .set(Offer::getStatus, Offer.STATUS_ACCEPTED));
        if (accepted == 0) {
            throw new BusinessException(ErrorCode.WANT_OFFER_HANDLED);
        }
        // ④ 该帖其余待处理应约先取快照（用于通知），再全部置已拒绝
        List<Offer> others = pendingOffers(post.getId()).stream()
                .filter(offer -> !Objects.equals(offer.getId(), offerId))
                .toList();
        offerMapper.rejectOtherPending(post.getId(), offerId);
        // ⑤ 创建 PURCHASE 订单：buyer=求购者，seller=应约者，金额=报价（PRD §5.4 / ORD-07）
        OrderInfo order = orderService.createTransactionOrder(post.getUserId(), locked.getUserId(),
                OrderInfo.TYPE_PURCHASE, null, locked.getPrice());
        offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offerId)
                .set(Offer::getOrderId, order.getId()));

        // 通知：应约者（成功）+ 其余应约者（失效）
        notificationService.push(locked.getUserId(), Notification.TYPE_ORDER, "应约已被接受",
                String.format("您对求购帖「%s」的应约已被接受，订单 %s 已生成，请及时确认",
                        post.getTitle(), order.getOrderNo()),
                com.campus.market.entity.CreditLog.REF_TYPE_ORDER, order.getId());
        others.forEach(offer -> notificationService.push(offer.getUserId(), Notification.TYPE_ORDER,
                "应约未被选中", String.format("求购帖「%s」已选择其他应约者，您的应约已关闭", post.getTitle()),
                null, null));
        log.info("接受应约：postId={}, offerId={}, orderId={}, 其余拒绝 {} 条",
                post.getId(), offerId, order.getId(), others.size());

        Offer refreshed = offerMapper.selectById(offerId);
        return toOfferVO(refreshed, post, loadUsers(List.of(refreshed.getUserId())));
    }

    @Override
    @Transactional
    public void rejectOffer(Long offerId, LoginUser user, String reason) {
        Offer offer = offerMapper.selectById(offerId);
        if (offer == null) {
            throw new BusinessException(ErrorCode.OFFER_NOT_FOUND);
        }
        WantPost post = wantPostMapper.selectById(offer.getWantPostId());
        if (post == null || WantPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.WANT_POST_FORBIDDEN, "仅求购者可拒绝应约");
        }
        int rows = offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offerId)
                .eq(Offer::getStatus, Offer.STATUS_PENDING)
                .set(Offer::getStatus, Offer.STATUS_REJECTED));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.WANT_OFFER_HANDLED);
        }
        String suffix = reason == null || reason.isBlank() ? "" : "，理由：" + reason.trim();
        notificationService.push(offer.getUserId(), Notification.TYPE_ORDER, "应约被拒绝",
                String.format("您对求购帖「%s」的应约未被接受%s", post.getTitle(), suffix), null, null);
    }

    @Override
    public PageResult<OfferVO> pageMyOffers(Long userId, Integer status, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        LambdaQueryWrapper<Offer> wrapper = new LambdaQueryWrapper<Offer>()
                .eq(Offer::getUserId, userId)
                .orderByDesc(Offer::getCreatedAt)
                .orderByDesc(Offer::getId);
        if (status != null) {
            wrapper.eq(Offer::getStatus, status);
        }
        IPage<Offer> result = offerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Offer> records = result.getRecords();
        if (records.isEmpty()) {
            return buildVoPage(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        Map<Long, WantPost> postMap = wantPostMapper.selectBatchIds(
                        records.stream().map(Offer::getWantPostId).distinct().toList()).stream()
                .collect(Collectors.toMap(WantPost::getId, Function.identity()));
        Map<Long, User> userMap = loadUsers(records.stream().map(Offer::getUserId).toList());
        List<OfferVO> voList = records.stream()
                .map(offer -> toOfferVO(offer, postMap.get(offer.getWantPostId()), userMap))
                .toList();
        return buildVoPage(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ==================== 私有辅助 ====================

    private Long requireViewerId(LoginUser viewer) {
        if (viewer == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return viewer.getUserId();
    }

    private WantPost requireExisting(Long id) {
        WantPost post = wantPostMapper.selectById(id);
        if (post == null || WantPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
        }
        return post;
    }

    private void requireOwner(Long ownerId, Long userId) {
        if (!Objects.equals(ownerId, userId)) {
            throw new BusinessException(ErrorCode.WANT_POST_FORBIDDEN);
        }
    }

    private void validateBudget(BigDecimal budget) {
        if (budget != null && budget.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.WANT_PARAM_INVALID, "心理价不能为负数");
        }
    }

    private Category requireValidCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.WANT_PARAM_INVALID, "请选择二级分类");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getParentId() == null
                || category.getParentId() == Category.ROOT_PARENT_ID
                || category.getStatus() != Category.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.WANT_PARAM_INVALID, "分类不存在或不是启用中的二级分类");
        }
        return category;
    }

    private void checkSensitive(String field, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> hits = sensitiveWordService.findHits(text);
        if (!hits.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_SENSITIVE,
                    field + "包含敏感词：" + String.join("、", hits));
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return WantPost.STATUS_OPEN;
        }
        return switch (status) {
            case WantPost.STATUS_OPEN, WantPost.STATUS_DEALT, WantPost.STATUS_CLOSED -> status;
            default -> WantPost.STATUS_OPEN;
        };
    }

    /** 分类筛选展开：一级分类取其启用中的二级子类；非法 ID 返回空结果哨兵（与 M2 口径一致） */
    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return Collections.singletonList(-1L);
        }
        if (category.getParentId() != null && category.getParentId() == Category.ROOT_PARENT_ID) {
            List<Long> childIds = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                            .eq(Category::getParentId, categoryId)
                            .eq(Category::getStatus, Category.STATUS_ENABLED))
                    .stream().map(Category::getId).toList();
            return childIds.isEmpty() ? Collections.singletonList(-1L) : childIds;
        }
        return Collections.singletonList(categoryId);
    }

    private List<Offer> pendingOffers(Long wantPostId) {
        return offerMapper.selectList(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getWantPostId, wantPostId)
                .eq(Offer::getStatus, Offer.STATUS_PENDING));
    }

    /** postId -> 待处理应约数（广场卡片"待处理口径"，与交换侧统一；批量一次查询避免 N+1） */
    private Map<Long, Long> offerCounts(List<Long> postIds) {
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = offerMapper.selectMaps(new QueryWrapper<Offer>()
                .select("want_post_id AS wantPostId", "COUNT(*) AS cnt")
                .in("want_post_id", ids)
                .eq("status", Offer.STATUS_PENDING)
                .groupBy("want_post_id"));
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            Object postId = row.get("wantPostId");
            Object cnt = row.get("cnt");
            if (postId instanceof Number idValue && cnt instanceof Number countValue) {
                counts.put(idValue.longValue(), countValue.longValue());
            }
        }
        return counts;
    }

    /** postId -> 当前用户应约状态（null 表示未应约） */
    private Map<Long, Integer> myOfferStatuses(List<Long> postIds, Long viewerId) {
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (viewerId == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Offer> mine = offerMapper.selectList(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getUserId, viewerId)
                .in(Offer::getWantPostId, ids)
                .orderByDesc(Offer::getId));
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (Offer offer : mine) {
            // 同一帖多条历史应约时，优先展示"待处理/已接受"等有效状态（按 id 倒序首条即为最新）
            result.putIfAbsent(offer.getWantPostId(), offer.getStatus());
        }
        return result;
    }

    private Map<Long, SellerVO> loadPublishers(List<WantPost> posts) {
        Map<Long, User> users = loadUsers(posts.stream().map(WantPost::getUserId).toList());
        Map<Long, SellerVO> result = new java.util.HashMap<>();
        users.forEach((userId, user) -> result.put(userId, toSellerVO(user)));
        return result;
    }

    private Map<Long, Category> loadCategoryMap(List<WantPost> posts) {
        List<Long> categoryIds = posts.stream().map(WantPost::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Category> map = new java.util.HashMap<>();
        List<Category> children = categoryMapper.selectBatchIds(categoryIds);
        children.forEach(category -> map.put(category.getId(), category));
        List<Long> parentIds = children.stream().map(Category::getParentId)
                .filter(parentId -> parentId != null && parentId != Category.ROOT_PARENT_ID)
                .distinct().toList();
        if (!parentIds.isEmpty()) {
            categoryMapper.selectBatchIds(parentIds).forEach(parent -> map.put(parent.getId(), parent));
        }
        return map;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private WantPostCardVO toCard(WantPost post, Map<Long, SellerVO> publisherMap,
                                  Map<Long, Category> categoryMap, Map<Long, Long> countMap,
                                  Map<Long, Integer> myStatusMap) {
        WantPostCardVO vo = new WantPostCardVO();
        fillCard(vo, post, publisherMap, categoryMap, countMap, myStatusMap);
        return vo;
    }

    private void fillCard(WantPostCardVO vo, WantPost post, Map<Long, SellerVO> publisherMap,
                          Map<Long, Category> categoryMap, Map<Long, Long> countMap,
                          Map<Long, Integer> myStatusMap) {
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setDescription(post.getDescription());
        vo.setBudget(post.getBudget());
        vo.setCategoryId(post.getCategoryId());
        Category category = categoryMap.get(post.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
            if (category.getParentId() != null && category.getParentId() != Category.ROOT_PARENT_ID) {
                Category parent = categoryMap.get(category.getParentId());
                if (parent != null) {
                    vo.setParentCategoryId(parent.getId());
                    vo.setParentCategoryName(parent.getName());
                }
            }
        }
        vo.setPublisher(publisherMap.get(post.getUserId()));
        vo.setStatus(post.getStatus());
        vo.setOfferCount(countMap.getOrDefault(post.getId(), 0L));
        vo.setMyOfferStatus(myStatusMap.get(post.getId()));
        vo.setCreatedAt(post.getCreatedAt());
    }

    private OfferVO toOfferVO(Offer offer, WantPost post, Map<Long, User> userMap) {
        OfferVO vo = new OfferVO();
        vo.setId(offer.getId());
        vo.setWantPostId(offer.getWantPostId());
        if (post != null) {
            vo.setWantPostTitle(post.getTitle());
            vo.setWantPostStatus(post.getStatus());
        }
        vo.setUserId(offer.getUserId());
        User user = userMap.get(offer.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setCreditScore(user.getCreditScore());
            vo.setAuthStatus(user.getAuthStatus());
        }
        vo.setPrice(offer.getPrice());
        vo.setMessage(offer.getMessage());
        vo.setStatus(offer.getStatus());
        vo.setOrderId(offer.getOrderId());
        vo.setCreatedAt(offer.getCreatedAt());
        return vo;
    }

    private SellerVO toSellerVO(User user) {
        SellerVO vo = new SellerVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setCreditScore(user.getCreditScore());
        vo.setAuthStatus(user.getAuthStatus());
        return vo;
    }

    private <T> PageResult<T> buildVoPage(List<T> list, long total, long pageNum, long pageSize) {
        PageResult<T> page = new PageResult<>();
        page.setList(new ArrayList<>(list));
        page.setTotal(total);
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        return page;
    }
}
