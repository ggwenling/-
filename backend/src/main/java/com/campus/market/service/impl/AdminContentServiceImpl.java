package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OperationLog;
import com.campus.market.entity.SwapPost;
import com.campus.market.entity.WantPost;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.SwapPostMapper;
import com.campus.market.mapper.WantPostMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AdminContentService;
import com.campus.market.service.NotificationService;
import com.campus.market.service.OperationLogService;
import com.campus.market.vo.AdminContentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 内容巡查处置实现（PRD ADM-02 / §6.2 / 数据库设计文档 T4）。
 * 状态口径：
 * - goods：下架 ON_SALE→OFF_SALE（off_sale_reason=管理理由，区别卖家自下架，卖家不可自行恢复）；
 * - 帖子：下架 OPEN→CLOSED（恢复 CLOSED→OPEN，仅 OPEN→CLOSED 可逆，DEALT 事实不可逆）；
 * - 软删：任意非交易中状态 → DELETED + 审计四元组（deleted_by_type=ADMIN）；恢复删除→回到下架态；
 * - 全部条件 UPDATE 防并发，rows=0 视为状态已变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContentServiceImpl implements AdminContentService {

    private static final Set<String> TARGET_TYPES = Set.of(AdminContentVO.TYPE_GOODS,
            AdminContentVO.TYPE_WANT, AdminContentVO.TYPE_SWAP);

    private final GoodsMapper goodsMapper;
    private final WantPostMapper wantPostMapper;
    private final SwapPostMapper swapPostMapper;
    private final OperationLogService operationLogService;
    private final NotificationService notificationService;

    @Override
    public PageResult<AdminContentVO> page(String targetType, String status, String keyword,
                                           long pageNum, long pageSize) {
        requireTargetType(targetType);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        boolean hasKeyword = StringUtils.hasText(keyword);
        return switch (targetType) {
            case AdminContentVO.TYPE_GOODS -> pageGoods(status, keyword, hasKeyword, pageNum, pageSize);
            case AdminContentVO.TYPE_WANT -> pageWants(status, keyword, hasKeyword, pageNum, pageSize);
            default -> pageSwaps(status, keyword, hasKeyword, pageNum, pageSize);
        };
    }

    // ==================== 下架 / 恢复 / 软删 ====================

    @Override
    @Transactional
    public void takeDown(String targetType, Long id, String reason, LoginUser operator) {
        requireTargetType(targetType);
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "下架理由必填");
        }
        String trimmed = reason.trim();
        int rows = switch (targetType) {
            case AdminContentVO.TYPE_GOODS -> goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, id)
                    .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                    .set(Goods::getStatus, Goods.STATUS_OFF_SALE)
                    .set(Goods::getOffSaleReason, trimmed));
            case AdminContentVO.TYPE_WANT -> wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                    .eq(WantPost::getId, id)
                    .eq(WantPost::getStatus, WantPost.STATUS_OPEN)
                    .set(WantPost::getStatus, WantPost.STATUS_CLOSED));
            default -> swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                    .eq(SwapPost::getId, id)
                    .eq(SwapPost::getStatus, SwapPost.STATUS_OPEN)
                    .set(SwapPost::getStatus, SwapPost.STATUS_CLOSED));
        };
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容当前状态不可下架（可能已下架/成交/删除）");
        }
        notifyOwner(targetType, id, String.format(
                "您发布的%s（#%d）因违规被平台下架：%s。如有疑问请联系管理员。", targetLabel(targetType), id, trimmed));
        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_GOODS_TAKE_DOWN, targetType, id, "平台下架：" + trimmed, null);
        log.info("管理下架内容：type={}, id={}, by={}", targetType, id, operator.getUsername());
    }

    @Override
    @Transactional
    public void restore(String targetType, Long id, LoginUser operator) {
        requireTargetType(targetType);
        int rows = switch (targetType) {
            case AdminContentVO.TYPE_GOODS -> goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, id)
                    .eq(Goods::getStatus, Goods.STATUS_OFF_SALE)
                    // 仅管理员下架（off_sale_reason 非空）的可由管理员恢复；
                    // 卖家自下架的走卖家本人 on-sale（验收 P2⑤：防误恢复卖家自下架内容）
                    .isNotNull(Goods::getOffSaleReason)
                    .set(Goods::getStatus, Goods.STATUS_ON_SALE)
                    .set(Goods::getOffSaleReason, null));
            case AdminContentVO.TYPE_WANT -> wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                    .eq(WantPost::getId, id)
                    .eq(WantPost::getStatus, WantPost.STATUS_CLOSED)
                    .set(WantPost::getStatus, WantPost.STATUS_OPEN));
            default -> swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                    .eq(SwapPost::getId, id)
                    .eq(SwapPost::getStatus, SwapPost.STATUS_CLOSED)
                    .set(SwapPost::getStatus, SwapPost.STATUS_OPEN));
        };
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "内容当前状态不可恢复（需为平台下架/平台关闭；卖家自下架的请由卖家自行重新上架）");
        }
        notifyOwner(targetType, id, String.format(
                "您发布的%s（#%d）已被管理员恢复展示。", targetLabel(targetType), id));
        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_GOODS_RESTORE, targetType, id, "恢复内容展示", null);
    }

    @Override
    @Transactional
    public void deleteContent(String targetType, Long id, String reason, LoginUser operator) {
        requireTargetType(targetType);
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "删除理由必填");
        }
        String trimmed = reason.trim();
        if (AdminContentVO.TYPE_GOODS.equals(targetType)) {
            Goods goods = goodsMapper.selectById(id);
            if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
                throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
            }
            if (Goods.STATUS_IN_TRANSACTION.equals(goods.getStatus())) {
                throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE, "交易中的商品不能删除");
            }
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, id)
                    .set(Goods::getStatus, Goods.STATUS_DELETED)
                    .set(Goods::getDeletedAt, LocalDateTime.now())
                    .set(Goods::getDeletedByType, Goods.DELETED_BY_ADMIN)
                    .set(Goods::getDeletedBy, operator.getUserId())
                    .set(Goods::getDeleteReason, trimmed));
        } else if (AdminContentVO.TYPE_WANT.equals(targetType)) {
            int rows = wantPostMapper.update(null, new LambdaUpdateWrapper<WantPost>()
                    .eq(WantPost::getId, id)
                    .ne(WantPost::getStatus, WantPost.STATUS_DELETED)
                    .set(WantPost::getStatus, WantPost.STATUS_DELETED)
                    .set(WantPost::getDeletedAt, LocalDateTime.now())
                    .set(WantPost::getDeletedByType, "ADMIN")
                    .set(WantPost::getDeletedBy, operator.getUserId())
                    .set(WantPost::getDeleteReason, trimmed));
            if (rows == 0) {
                throw new BusinessException(ErrorCode.WANT_POST_NOT_FOUND);
            }
        } else {
            int rows = swapPostMapper.update(null, new LambdaUpdateWrapper<SwapPost>()
                    .eq(SwapPost::getId, id)
                    .ne(SwapPost::getStatus, SwapPost.STATUS_DELETED)
                    .set(SwapPost::getStatus, SwapPost.STATUS_DELETED)
                    .set(SwapPost::getDeletedAt, LocalDateTime.now())
                    .set(SwapPost::getDeletedByType, "ADMIN")
                    .set(SwapPost::getDeletedBy, operator.getUserId())
                    .set(SwapPost::getDeleteReason, trimmed));
            if (rows == 0) {
                throw new BusinessException(ErrorCode.SWAP_POST_NOT_FOUND);
            }
        }
        notifyOwner(targetType, id, String.format(
                "您发布的%s（#%d）因违规被平台删除：%s。", targetLabel(targetType), id, trimmed));
        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_GOODS_DELETE, targetType, id, "软删内容：" + trimmed, null);
        log.info("管理软删内容：type={}, id={}, by={}", targetType, id, operator.getUsername());
    }

    /** 处置结果通知发布者（验收 P1：下架/删除/恢复均需通知，PRD ADM-02） */
    private void notifyOwner(String targetType, Long id, String content) {
        Long ownerId = switch (targetType) {
            case AdminContentVO.TYPE_GOODS -> {
                Goods goods = goodsMapper.selectById(id);
                yield goods == null ? null : goods.getUserId();
            }
            case AdminContentVO.TYPE_WANT -> {
                WantPost post = wantPostMapper.selectById(id);
                yield post == null ? null : post.getUserId();
            }
            default -> {
                SwapPost post = swapPostMapper.selectById(id);
                yield post == null ? null : post.getUserId();
            }
        };
        if (ownerId != null) {
            notificationService.push(ownerId, Notification.TYPE_AUDIT,
                    "内容处置通知", content, targetType, id);
        }
    }

    /** 目标类型的展示名（通知文案用） */
    private String targetLabel(String targetType) {
        return switch (targetType) {
            case AdminContentVO.TYPE_GOODS -> "商品";
            case AdminContentVO.TYPE_WANT -> "求购帖";
            default -> "交换帖";
        };
    }

    // ==================== 分页投影 ====================

    private PageResult<AdminContentVO> pageGoods(String status, String keyword, boolean hasKeyword,
                                                 long pageNum, long pageSize) {
        IPage<Goods> result = goodsMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Goods>()
                        .eq(StringUtils.hasText(status), Goods::getStatus, status)
                        .like(hasKeyword, Goods::getTitle, keyword)
                        .orderByDesc(Goods::getCreatedAt));
        return toPage(result, result.getRecords().stream().map(g -> {
            AdminContentVO vo = new AdminContentVO();
            vo.setTargetType(AdminContentVO.TYPE_GOODS);
            vo.setId(g.getId());
            vo.setTitle(g.getTitle());
            vo.setOwnerId(g.getUserId());
            vo.setStatus(g.getStatus());
            vo.setAmount(g.getPrice());
            vo.setOffSaleReason(g.getOffSaleReason());
            vo.setDeletedAt(g.getDeletedAt());
            vo.setDeleteReason(g.getDeleteReason());
            vo.setCreatedAt(g.getCreatedAt());
            return vo;
        }).toList());
    }

    private PageResult<AdminContentVO> pageWants(String status, String keyword, boolean hasKeyword,
                                                 long pageNum, long pageSize) {
        IPage<WantPost> result = wantPostMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<WantPost>()
                        .eq(StringUtils.hasText(status), WantPost::getStatus, status)
                        .like(hasKeyword, WantPost::getTitle, keyword)
                        .orderByDesc(WantPost::getCreatedAt));
        return toPage(result, result.getRecords().stream().map(w -> {
            AdminContentVO vo = new AdminContentVO();
            vo.setTargetType(AdminContentVO.TYPE_WANT);
            vo.setId(w.getId());
            vo.setTitle(w.getTitle());
            vo.setOwnerId(w.getUserId());
            vo.setStatus(w.getStatus());
            vo.setAmount(w.getBudget());
            vo.setDeletedAt(w.getDeletedAt());
            vo.setDeleteReason(w.getDeleteReason());
            vo.setCreatedAt(w.getCreatedAt());
            return vo;
        }).toList());
    }

    private PageResult<AdminContentVO> pageSwaps(String status, String keyword, boolean hasKeyword,
                                                 long pageNum, long pageSize) {
        IPage<SwapPost> result = swapPostMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SwapPost>()
                        .eq(StringUtils.hasText(status), SwapPost::getStatus, status)
                        .like(hasKeyword, SwapPost::getTitle, keyword)
                        .orderByDesc(SwapPost::getCreatedAt));
        return toPage(result, result.getRecords().stream().map(s -> {
            AdminContentVO vo = new AdminContentVO();
            vo.setTargetType(AdminContentVO.TYPE_SWAP);
            vo.setId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setOwnerId(s.getUserId());
            vo.setStatus(s.getStatus());
            vo.setAmount(s.getDiffAmount());
            vo.setDeletedAt(s.getDeletedAt());
            vo.setDeleteReason(s.getDeleteReason());
            vo.setCreatedAt(s.getCreatedAt());
            return vo;
        }).toList());
    }

    private <T> PageResult<AdminContentVO> toPage(IPage<T> result, List<AdminContentVO> vos) {
        PageResult<AdminContentVO> page = new PageResult<>();
        page.setList(vos);
        page.setTotal(result.getTotal());
        page.setPageNum(result.getCurrent());
        page.setPageSize(result.getSize());
        return page;
    }

    private void requireTargetType(String targetType) {
        if (targetType == null || !TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "targetType 仅支持 GOODS/WANT/SWAP");
        }
    }
}
