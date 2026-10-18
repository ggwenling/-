package com.campus.market.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Notification;
import com.campus.market.mapper.NotificationMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 站内通知接口（PRD NTF-01/02）：分页（type 筛选）、未读数、单条已读、全部已读；
 * 全部 @RequireAuth 且只操作自己的通知（他人通知按不存在处理）。
 * 通知写入走共享契约 {@code NotificationService#push}（M3/M6 调用，签名不变）；
 * 因该接口为 M3/M4 共享契约，查询侧直接使用 NotificationMapper 只读投影，不改动其既有方法。
 */
@Tag(name = "消息-通知")
@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /** NTF-01 type 筛选合法值（与 Notification.TYPE_* 常量一致） */
    private static final Set<String> FILTERABLE_TYPES = Set.of(
            Notification.TYPE_ORDER, Notification.TYPE_AUDIT, Notification.TYPE_REPORT,
            Notification.TYPE_CREDIT, Notification.TYPE_SYSTEM);

    private final NotificationMapper notificationMapper;

    @Operation(summary = "我的通知分页（NTF-01，type 筛选）")
    @GetMapping
    public Result<PageResult<NotificationVO>> page(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        Long userId = requireFrontUser().getUserId();
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);
        if (type != null && !type.isBlank()) {
            if (!FILTERABLE_TYPES.contains(type)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的通知类型筛选");
            }
            wrapper.eq(Notification::getType, type);
        }
        wrapper.orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId);
        IPage<Notification> result = notificationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<NotificationVO> voList = result.getRecords().stream().map(this::toVO).toList();
        PageResult<NotificationVO> voPage = new PageResult<>();
        voPage.setList(voList);
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return Result.ok(voPage);
    }

    @Operation(summary = "未读通知数（NTF-01，导航红点）")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationMapper.countUnread(requireFrontUser().getUserId()));
    }

    @Operation(summary = "单条已读（NTF-02，幂等；他人通知按不存在处理）")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = requireFrontUser().getUserId();
        Notification notification = notificationMapper.selectById(id);
        // 只能操作自己的通知：他人通知一律 NOT_FOUND，不暴露存在性（PRD §9.2）
        if (notification == null || !userId.equals(notification.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (notification.getIsRead() == null || notification.getIsRead() == 0) {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId)
                    .set(Notification::getIsRead, 1));
        }
        return Result.ok();
    }

    @Operation(summary = "全部已读（NTF-02，幂等）")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, requireFrontUser().getUserId())
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        return Result.ok();
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setRefType(notification.getRefType());
        vo.setRefId(notification.getRefId());
        vo.setIsRead(notification.getIsRead());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }

    /** 通知仅限前台用户主体 */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
