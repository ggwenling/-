package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.SwapPostListQuery;
import com.campus.market.dto.SwapPostPublishDTO;
import com.campus.market.dto.SwapRequestCreateDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.CreditLog;
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
import com.campus.market.service.SwapPostService;
import com.campus.market.vo.SellerVO;
import com.campus.market.vo.SwapPostCardVO;
import com.campus.market.vo.SwapPostDetailVO;
import com.campus.market.vo.SwapRequestVO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交换服务实现（PRD SWP-01~03、§5.5）。
 * 同意请求的原子性（数据库设计文档 §3.14 T8）：同一事务内 ①锁请求（固定加锁顺序：请求 → 帖）②帖置 DEALT
 * ③本请求置已同意并回填 order_id ④其余待处理请求全部置已拒绝 ⑤创建 SWAP 订单（创建即 SCHEDULED，双方各自确认完成后才 COMPLETED）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwapPostServiceImpl implements SwapPostService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int AUTO_RESTORE_DAYS = 30;

    private final SwapPostMapper swapPostMapper;
    private final SwapRequestMapper swapRequestMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsImageMapper goodsImageMapper;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final SensitiveWordService sensitiveWordService;
    private final UserAccessGuard userAccessGuard;

    // ==================== SWP-01 发布 / 编辑 ====================

    @Override
    @Transactional
    public Long publish(SwapPostPublishDTO dto, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        Category category = requireValidCategory(dto.getCategoryId());
        BigDecimal diffAmount = resolveDiffAmount(dto);
        checkSensitive("标题", dto.getTitle());
        checkSensitive("我的物品描述", dto.getMyItemDesc());
        checkSensitive("想要的物品描述", dto.getWantItemDesc());

        SwapPost post = new SwapPost();
        post.setUserId(user.getUserId());
        post.setCategoryId(category.getId());
        post.setTitle(dto.getTitle().trim());
        post.setMyItemDesc(dto.getMyItemDesc().trim());
        post.setWantItemDesc(dto.getWantItemDesc().trim());
        post.setAllowDiff(dto.getAllowDiff());
        post.setDiffAmount(diffAmount);
        post.setStatus(SwapPost.STATUS_OPEN);
        swapPostMapper.insert(post);
        return post.getId();
    }

    @Override
    @Transactional
    public Long update(Long id, SwapPostPublishDTO dto, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        SwapPost post = requireExisting(id);
        requireOwner(post.getUserId(), user.getUserId());
        if (!SwapPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED, "仅交换中的帖子可以编辑");
        }
        Category category = requireValidCategory(dto.getCategoryId());
        BigDecimal diffAmount = resolveDiffAmount(dto);
        checkSensitive("标题", dto.getTitle());
        checkSensitive("我的物品描述", dto.getMyItemDesc());
        checkSensitive("想要的物品描述", dto.getWantItemDesc());

        swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                .eq(SwapPost::getId, id)
                .set(SwapPost::getCategoryId, category.getId())
                .set(SwapPost::getTitle, dto.getTitle().trim())
                .set(SwapPost::getMyItemDesc, dto.getMyItemDesc().trim())
                .set(SwapPost::getWantItemDesc, dto.getWantItemDesc().trim())
                .set(SwapPost::getAllowDiff, dto.getAllowDiff())
                .set(SwapPost::getDiffAmount, diffAmount));
        return id;
    }

    @Override
    @Transactional
    public void close(Long id, LoginUser user, String reason) {
        SwapPost post = requireExisting(id);
        requireOwner(post.getUserId(), user.getUserId());
        if (!SwapPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED, "仅交换中的帖子可以关闭");
        }
        int rows = swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                .eq(SwapPost::getId, id)
                .eq(SwapPost::getStatus, SwapPost.STATUS_OPEN)
                .set(SwapPost::getStatus, SwapPost.STATUS_CLOSED));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED, "帖子状态已变化，请刷新后重试");
        }
        List<SwapRequest> pending = pendingRequests(id);
        if (!pending.isEmpty()) {
            swapRequestMapper.rejectOtherPending(id, -1L);
            pending.forEach(request -> notificationService.push(request.getUserId(), Notification.TYPE_ORDER,
                    "交换帖已关闭", String.format("交换帖「%s」已被帖主关闭，您的交换请求已失效", post.getTitle()),
                    null, null));
        }
        log.info("交换帖关闭：postId={}, 理由={}", id, reason);
    }

    @Override
    @Transactional
    public void removeByOwner(Long id, LoginUser user) {
        SwapPost post = requireExisting(id);
        requireOwner(post.getUserId(), user.getUserId());
        if (SwapPost.STATUS_DEALT.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED, "已成交的交换帖不能删除");
        }
        swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                .eq(SwapPost::getId, id)
                .set(SwapPost::getStatus, SwapPost.STATUS_DELETED)
                .set(SwapPost::getDeletedAt, LocalDateTime.now(BUSINESS_ZONE))
                .set(SwapPost::getDeletedByType, SwapPost.DELETED_BY_USER)
                .set(SwapPost::getDeletedBy, user.getUserId())
                .set(SwapPost::getDeleteReason, SwapPost.USER_DELETE_REASON));
    }

    @Override
    @Transactional
    public void restore(Long id, LoginUser user) {
        SwapPost post = swapPostMapper.selectById(id);
        if (post == null || !SwapPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
        }
        if (!SwapPost.DELETED_BY_USER.equals(post.getDeletedByType())
                || !Objects.equals(post.getDeletedBy(), user.getUserId())) {
            throw new BusinessException(ErrorCode.SWAP_POST_FORBIDDEN, "只能恢复自己删除的交换帖");
        }
        if (post.getDeletedAt() == null
                || post.getDeletedAt().isBefore(LocalDateTime.now(BUSINESS_ZONE).minusDays(AUTO_RESTORE_DAYS))) {
            throw new BusinessException(ErrorCode.SWAP_POST_FORBIDDEN, "已删除超过 30 天，无法自行恢复");
        }
        swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                .eq(SwapPost::getId, id)
                .set(SwapPost::getStatus, SwapPost.STATUS_CLOSED)
                .set(SwapPost::getDeletedAt, null)
                .set(SwapPost::getDeletedByType, null)
                .set(SwapPost::getDeletedBy, null)
                .set(SwapPost::getDeleteReason, null));
    }

    // ==================== SWP-02 广场与详情 ====================

    @Override
    public PageResult<SwapPostCardVO> pageList(SwapPostListQuery query, LoginUser viewer) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        String status = normalizeStatus(query.getStatus());

        LambdaQueryWrapper<SwapPost> wrapper = new LambdaQueryWrapper<SwapPost>()
                .eq(SwapPost::getStatus, status);
        if (Boolean.TRUE.equals(query.getMine())) {
            if (viewer == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            wrapper.eq(SwapPost::getUserId, viewer.getUserId());
        }
        List<Long> categoryIds = resolveCategoryIds(query.getCategoryId());
        if (categoryIds != null) {
            wrapper.in(SwapPost::getCategoryId, categoryIds);
        }
        if (query.getQ() != null && !query.getQ().isBlank()) {
            String q = query.getQ().trim();
            wrapper.and(w -> w.like(SwapPost::getTitle, q)
                    .or().like(SwapPost::getMyItemDesc, q)
                    .or().like(SwapPost::getWantItemDesc, q));
        }
        wrapper.orderByDesc(SwapPost::getCreatedAt).orderByDesc(SwapPost::getId);

        IPage<SwapPost> result = swapPostMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SwapPost> records = result.getRecords();
        if (records.isEmpty()) {
            return buildVoPage(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        List<Long> postIds = records.stream().map(SwapPost::getId).toList();
        Map<Long, SellerVO> publisherMap = loadPublishers(records);
        Map<Long, Category> categoryMap = loadCategoryMap(records);
        Map<Long, Long> pendingMap = pendingCounts(postIds);
        Map<Long, Integer> myStatusMap = myRequestStatuses(postIds, viewer == null ? null : viewer.getUserId());

        List<SwapPostCardVO> voList = records.stream()
                .map(post -> toCard(post, publisherMap, categoryMap, pendingMap, myStatusMap))
                .toList();
        return buildVoPage(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public SwapPostDetailVO detail(Long id, LoginUser viewer) {
        SwapPost post = swapPostMapper.selectById(id);
        boolean isOwner = viewer != null && post != null && Objects.equals(post.getUserId(), viewer.getUserId());
        if (post == null || (SwapPost.STATUS_DELETED.equals(post.getStatus()) && !isOwner)) {
            throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
        }
        Long viewerId = viewer == null ? null : viewer.getUserId();

        SwapPostDetailVO vo = new SwapPostDetailVO();
        fillCard(vo, post, loadPublishers(List.of(post)), loadCategoryMap(List.of(post)),
                pendingCounts(List.of(id)), myRequestStatuses(List.of(id), viewerId));
        vo.setUpdatedAt(post.getUpdatedAt());

        List<SwapRequest> allRequests = swapRequestMapper.selectList(new LambdaQueryWrapper<SwapRequest>()
                .eq(SwapRequest::getSwapPostId, id)
                .orderByAsc(SwapRequest::getStatus)
                .orderByDesc(SwapRequest::getCreatedAt)
                .orderByDesc(SwapRequest::getId));
        Map<Long, User> users = loadUsers(allRequests.stream().map(SwapRequest::getUserId).toList());
        Map<Long, Goods> goodsMap = loadLinkedGoods(allRequests);
        Map<Long, String> coverMap = loadGoodsCovers(goodsMap.keySet());
        if (isOwner) {
            vo.setRequests(allRequests.stream()
                    .map(request -> toRequestVO(request, users, goodsMap, coverMap)).toList());
        }
        if (viewerId != null) {
            SwapRequest mine = allRequests.stream()
                    .filter(request -> Objects.equals(request.getUserId(), viewerId))
                    .findFirst().orElse(null);
            vo.setMyRequest(mine == null ? null : toRequestVO(mine, users, goodsMap, coverMap));
        }
        allRequests.stream()
                .filter(request -> request.getStatus() == SwapRequest.STATUS_ACCEPTED && request.getOrderId() != null)
                .findFirst()
                .ifPresent(request -> vo.setOrderId(request.getOrderId()));
        vo.setCanManage(isOwner && SwapPost.STATUS_OPEN.equals(post.getStatus()));
        vo.setCanRequest(userAccessGuard.canInteract(viewer) && !isOwner && SwapPost.STATUS_OPEN.equals(post.getStatus()));
        return vo;
    }

    // ==================== SWP-02/03 发起 / 同意 / 拒绝 ====================

    @Override
    @Transactional
    public SwapRequestVO createRequest(Long postId, SwapRequestCreateDTO dto, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        SwapPost post = swapPostMapper.selectById(postId);
        if (post == null || SwapPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
        }
        if (!SwapPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED);
        }
        if (Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_SELF);
        }
        checkSensitive("物品说明", dto.getItemDesc());

        // 关联商品必须是自己 ON_SALE 的商品（PRD SWP-02）
        Goods linkedGoods = null;
        if (dto.getGoodsId() != null) {
            linkedGoods = goodsMapper.selectById(dto.getGoodsId());
            if (linkedGoods == null || Goods.STATUS_DELETED.equals(linkedGoods.getStatus())) {
                throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
            }
            if (!Objects.equals(linkedGoods.getUserId(), user.getUserId())) {
                throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "只能关联自己发布的商品");
            }
            if (!Goods.STATUS_ON_SALE.equals(linkedGoods.getStatus())) {
                throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "关联商品须为在售状态");
            }
        }

        Long pending = swapRequestMapper.selectCount(new LambdaQueryWrapper<SwapRequest>()
                .eq(SwapRequest::getSwapPostId, postId)
                .eq(SwapRequest::getUserId, user.getUserId())
                .eq(SwapRequest::getStatus, SwapRequest.STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_DUPLICATE);
        }

        SwapRequest request = new SwapRequest();
        request.setSwapPostId(postId);
        request.setUserId(user.getUserId());
        request.setItemDesc(dto.getItemDesc().trim());
        request.setGoodsId(dto.getGoodsId());
        request.setStatus(SwapRequest.STATUS_PENDING);
        try {
            swapRequestMapper.insert(request);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_DUPLICATE);
        }
        notificationService.push(post.getUserId(), Notification.TYPE_ORDER, "收到新的交换请求",
                String.format("交换帖「%s」收到一条交换请求，请及时处理", post.getTitle()), null, null);

        Map<Long, User> users = loadUsers(List.of(user.getUserId()));
        Map<Long, Goods> goodsMap = linkedGoods == null ? Map.of() : Map.of(linkedGoods.getId(), linkedGoods);
        return toRequestVO(request, users, goodsMap, loadGoodsCovers(goodsMap.keySet()));
    }

    @Override
    @Transactional
    public SwapRequestVO acceptRequest(Long requestId, LoginUser user) {
        userAccessGuard.requireInteractive(user);
        // ① 锁帖（固定顺序：请求 → 帖）
        SwapRequest locked = swapRequestMapper.selectByIdForUpdate(requestId);
        if (locked == null) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_NOT_FOUND);
        }
        SwapPost post = swapPostMapper.selectByIdForUpdate(locked.getSwapPostId());
        if (post == null || SwapPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.SWAP_POST_FORBIDDEN, "仅帖主可同意交换请求");
        }
        if (!SwapPost.STATUS_OPEN.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED, "帖子已关闭或已成交");
        }
        if (locked.getStatus() != SwapRequest.STATUS_PENDING) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_HANDLED);
        }

        // ② 帖置 DEALT
        int closed = swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                .eq(SwapPost::getId, post.getId())
                .eq(SwapPost::getStatus, SwapPost.STATUS_OPEN)
                .set(SwapPost::getStatus, SwapPost.STATUS_DEALT));
        if (closed == 0) {
            throw new BusinessException(ErrorCode.SWAP_POST_CLOSED, "帖子状态已变化，请刷新后重试");
        }
        // ③ 本请求置已同意
        int accepted = swapRequestMapper.update(null, new LambdaUpdateWrapper<SwapRequest>()
                .eq(SwapRequest::getId, requestId)
                .eq(SwapRequest::getStatus, SwapRequest.STATUS_PENDING)
                .set(SwapRequest::getStatus, SwapRequest.STATUS_ACCEPTED));
        if (accepted == 0) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_HANDLED);
        }
        // ④ 其余待处理请求取快照后全部置已拒绝
        List<SwapRequest> others = pendingRequests(post.getId()).stream()
                .filter(request -> !Objects.equals(request.getId(), requestId))
                .toList();
        swapRequestMapper.rejectOtherPending(post.getId(), requestId);
        // ⑤ 创建 SWAP 订单：buyer=发起方，seller=帖主，金额=差价或 0（创建即 SCHEDULED，T7 双确认）
        BigDecimal amount = Integer.valueOf(SwapPost.ALLOW_DIFF_YES).equals(post.getAllowDiff())
                && post.getDiffAmount() != null ? post.getDiffAmount() : BigDecimal.ZERO;
        OrderInfo order = orderService.createTransactionOrder(locked.getUserId(), post.getUserId(),
                OrderInfo.TYPE_SWAP, locked.getGoodsId(), amount);
        swapRequestMapper.update(null, new LambdaUpdateWrapper<SwapRequest>()
                .eq(SwapRequest::getId, requestId)
                .set(SwapRequest::getOrderId, order.getId()));

        notificationService.push(locked.getUserId(), Notification.TYPE_ORDER, "交换请求已被同意",
                String.format("您对交换帖「%s」的请求已被同意，订单 %s 已生成，双方确认完成即交易成功",
                        post.getTitle(), order.getOrderNo()),
                CreditLog.REF_TYPE_ORDER, order.getId());
        others.forEach(request -> notificationService.push(request.getUserId(), Notification.TYPE_ORDER,
                "交换请求未被同意", String.format("交换帖「%s」已与其他用户达成交换，您的请求已关闭", post.getTitle()),
                null, null));
        log.info("同意交换：postId={}, requestId={}, orderId={}, 其余拒绝 {} 条",
                post.getId(), requestId, order.getId(), others.size());

        SwapRequest refreshed = swapRequestMapper.selectById(requestId);
        Map<Long, Goods> goodsMap = loadLinkedGoods(List.of(refreshed));
        return toRequestVO(refreshed, loadUsers(List.of(refreshed.getUserId())), goodsMap,
                loadGoodsCovers(goodsMap.keySet()));
    }

    @Override
    @Transactional
    public void rejectRequest(Long requestId, LoginUser user, String reason) {
        SwapRequest request = swapRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_NOT_FOUND);
        }
        SwapPost post = swapPostMapper.selectById(request.getSwapPostId());
        if (post == null || SwapPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.SWAP_POST_FORBIDDEN, "仅帖主可拒绝交换请求");
        }
        int rows = swapRequestMapper.update(null, new LambdaUpdateWrapper<SwapRequest>()
                .eq(SwapRequest::getId, requestId)
                .eq(SwapRequest::getStatus, SwapRequest.STATUS_PENDING)
                .set(SwapRequest::getStatus, SwapRequest.STATUS_REJECTED));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SWAP_REQUEST_HANDLED);
        }
        String suffix = reason == null || reason.isBlank() ? "" : "，理由：" + reason.trim();
        notificationService.push(request.getUserId(), Notification.TYPE_ORDER, "交换请求被拒绝",
                String.format("您对交换帖「%s」的交换请求未被接受%s", post.getTitle(), suffix), null, null);
    }

    @Override
    public PageResult<SwapRequestVO> pageMyRequests(Long userId, Integer status, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        LambdaQueryWrapper<SwapRequest> wrapper = new LambdaQueryWrapper<SwapRequest>()
                .eq(SwapRequest::getUserId, userId)
                .orderByDesc(SwapRequest::getCreatedAt)
                .orderByDesc(SwapRequest::getId);
        if (status != null) {
            wrapper.eq(SwapRequest::getStatus, status);
        }
        IPage<SwapRequest> result = swapRequestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SwapRequest> records = result.getRecords();
        if (records.isEmpty()) {
            return buildVoPage(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        Map<Long, User> users = loadUsers(records.stream().map(SwapRequest::getUserId).toList());
        Map<Long, Goods> goodsMap = loadLinkedGoods(records);
        Map<Long, String> coverMap = loadGoodsCovers(goodsMap.keySet());
        List<SwapRequestVO> voList = records.stream()
                .map(request -> toRequestVO(request, users, goodsMap, coverMap)).toList();
        return buildVoPage(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ==================== 私有辅助 ====================

    private SwapPost requireExisting(Long id) {
        SwapPost post = swapPostMapper.selectById(id);
        if (post == null || SwapPost.STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
        }
        return post;
    }

    private void requireOwner(Long ownerId, Long userId) {
        if (!Objects.equals(ownerId, userId)) {
            throw new BusinessException(ErrorCode.SWAP_POST_FORBIDDEN);
        }
    }

    /** 差价联动校验：allowDiff=1 → 必填且 ≥0；allowDiff=0 → 强制 NULL（PRD SWP-01） */
    private BigDecimal resolveDiffAmount(SwapPostPublishDTO dto) {
        if (dto.getAllowDiff() == null || (dto.getAllowDiff() != SwapPost.ALLOW_DIFF_NO
                && dto.getAllowDiff() != SwapPost.ALLOW_DIFF_YES)) {
            throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "是否接受补差价取值须为 0 或 1");
        }
        if (dto.getAllowDiff() == SwapPost.ALLOW_DIFF_NO) {
            return null;
        }
        if (dto.getDiffAmount() == null) {
            throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "接受补差价时请填写期望差价金额");
        }
        if (dto.getDiffAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "差价金额不能为负数");
        }
        return dto.getDiffAmount();
    }

    private Category requireValidCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "请选择二级分类");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getParentId() == null
                || category.getParentId() == Category.ROOT_PARENT_ID
                || category.getStatus() != Category.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.SWAP_PARAM_INVALID, "分类不存在或不是启用中的二级分类");
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
            return SwapPost.STATUS_OPEN;
        }
        return switch (status) {
            case SwapPost.STATUS_OPEN, SwapPost.STATUS_DEALT, SwapPost.STATUS_CLOSED -> status;
            default -> SwapPost.STATUS_OPEN;
        };
    }

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

    private List<SwapRequest> pendingRequests(Long swapPostId) {
        return swapRequestMapper.selectList(new LambdaQueryWrapper<SwapRequest>()
                .eq(SwapRequest::getSwapPostId, swapPostId)
                .eq(SwapRequest::getStatus, SwapRequest.STATUS_PENDING));
    }

    /** postId -> 待处理请求数（广场卡片"已有 N 人想换"） */
    private Map<Long, Long> pendingCounts(List<Long> postIds) {
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = swapRequestMapper.selectMaps(new QueryWrapper<SwapRequest>()
                .select("swap_post_id AS swapPostId", "COUNT(*) AS cnt")
                .in("swap_post_id", ids)
                .eq("status", SwapRequest.STATUS_PENDING)
                .groupBy("swap_post_id"));
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object postId = row.get("swapPostId");
            Object cnt = row.get("cnt");
            if (postId instanceof Number idValue && cnt instanceof Number countValue) {
                counts.put(idValue.longValue(), countValue.longValue());
            }
        }
        return counts;
    }

    private Map<Long, Integer> myRequestStatuses(List<Long> postIds, Long viewerId) {
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (viewerId == null || ids.isEmpty()) {
            return Map.of();
        }
        List<SwapRequest> mine = swapRequestMapper.selectList(new LambdaQueryWrapper<SwapRequest>()
                .eq(SwapRequest::getUserId, viewerId)
                .in(SwapRequest::getSwapPostId, ids)
                .orderByDesc(SwapRequest::getId));
        Map<Long, Integer> result = new HashMap<>();
        for (SwapRequest request : mine) {
            result.putIfAbsent(request.getSwapPostId(), request.getStatus());
        }
        return result;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, SellerVO> loadPublishers(List<SwapPost> posts) {
        Map<Long, SellerVO> result = new HashMap<>();
        loadUsers(posts.stream().map(SwapPost::getUserId).toList())
                .forEach((userId, user) -> result.put(userId, toSellerVO(user)));
        return result;
    }

    /** 分类投影（含一级父类，供编辑表单回填 cascader 路径）：id -> Category */
    private Map<Long, Category> loadCategoryMap(List<SwapPost> posts) {
        List<Long> categoryIds = posts.stream().map(SwapPost::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Category> map = new HashMap<>();
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

    private Map<Long, Goods> loadLinkedGoods(List<SwapRequest> requests) {
        List<Long> goodsIds = requests.stream().map(SwapRequest::getGoodsId)
                .filter(Objects::nonNull).distinct().toList();
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        return goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));
    }

    private Map<Long, String> loadGoodsCovers(java.util.Collection<Long> goodsIds) {
        List<Long> ids = goodsIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return goodsImageMapper.selectList(new LambdaQueryWrapper<GoodsImage>()
                        .in(GoodsImage::getGoodsId, ids)
                        .orderByAsc(GoodsImage::getSort))
                .stream()
                .collect(Collectors.toMap(GoodsImage::getGoodsId, GoodsImage::getThumbUrl, (a, b) -> a));
    }

    private SwapPostCardVO toCard(SwapPost post, Map<Long, SellerVO> publisherMap,
                                  Map<Long, Category> categoryMap, Map<Long, Long> pendingMap,
                                  Map<Long, Integer> myStatusMap) {
        SwapPostCardVO vo = new SwapPostCardVO();
        fillCard(vo, post, publisherMap, categoryMap, pendingMap, myStatusMap);
        return vo;
    }

    private void fillCard(SwapPostCardVO vo, SwapPost post, Map<Long, SellerVO> publisherMap,
                          Map<Long, Category> categoryMap, Map<Long, Long> pendingMap,
                          Map<Long, Integer> myStatusMap) {
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
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
        vo.setMyItemDesc(post.getMyItemDesc());
        vo.setWantItemDesc(post.getWantItemDesc());
        vo.setAllowDiff(post.getAllowDiff());
        vo.setDiffAmount(post.getDiffAmount());
        vo.setPublisher(publisherMap.get(post.getUserId()));
        vo.setStatus(post.getStatus());
        vo.setPendingRequestCount(pendingMap.getOrDefault(post.getId(), 0L));
        vo.setMyRequestStatus(myStatusMap.get(post.getId()));
        vo.setCreatedAt(post.getCreatedAt());
    }

    private SwapRequestVO toRequestVO(SwapRequest request, Map<Long, User> userMap,
                                      Map<Long, Goods> goodsMap, Map<Long, String> coverMap) {
        SwapRequestVO vo = new SwapRequestVO();
        vo.setId(request.getId());
        vo.setSwapPostId(request.getSwapPostId());
        vo.setUserId(request.getUserId());
        User user = userMap.get(request.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setCreditScore(user.getCreditScore());
            vo.setAuthStatus(user.getAuthStatus());
        }
        vo.setItemDesc(request.getItemDesc());
        vo.setGoodsId(request.getGoodsId());
        Goods goods = request.getGoodsId() == null ? null : goodsMap.get(request.getGoodsId());
        if (goods != null) {
            vo.setGoodsTitle(goods.getTitle());
            vo.setGoodsCoverUrl(coverMap.get(goods.getId()));
            vo.setGoodsPrice(goods.getPrice());
        }
        vo.setStatus(request.getStatus());
        vo.setOrderId(request.getOrderId());
        vo.setCreatedAt(request.getCreatedAt());
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
