package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OperationLog;
import com.campus.market.entity.User;
import com.campus.market.mapper.NotificationMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AdminUserService;
import com.campus.market.service.CreditService;
import com.campus.market.service.OperationLogService;
import com.campus.market.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台用户服务实现（PRD ADM-05 / §5.7 / 数据库设计文档 §3.2）。
 * 一致性要点：
 * - 封禁/解封为条件 UPDATE（status 原值约束），rows=0 即状态已变，防重复操作；
 * - 封禁同事务写 ban_reason/banned_until 并通知用户（PRD §3.1：用户可查看封禁原因与期限）；
 * - 信用调整委托 CreditService 通用重载（ref 为空不走幂等键，operatorId/remark 落流水）；
 * - 全部 mutating 动作写 operation_log 审计（ADMIN_BAN/UNBAN/CREDIT_ADJUST）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final CreditService creditService;
    private final OperationLogService operationLogService;

    @Override
    public PageResult<AdminUserVO> page(String username, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(username), User::getUsername, username)
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);
        IPage<User> result = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AdminUserVO> records = result.getRecords().stream().map(AdminUserServiceImpl::toVO).toList();
        PageResult<AdminUserVO> page = new PageResult<>();
        page.setList(records);
        page.setTotal(result.getTotal());
        page.setPageNum(result.getCurrent());
        page.setPageSize(result.getSize());
        return page;
    }

    @Override
    public AdminUserVO detail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    /** 实体 → 管理端 VO：不拷贝 password（BCrypt 哈希不随 JSON 输出，验收 P1） */
    private static AdminUserVO toVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setCollege(user.getCollege());
        vo.setBio(user.getBio());
        vo.setCreditScore(user.getCreditScore());
        vo.setAuthStatus(user.getAuthStatus());
        vo.setStatus(user.getStatus());
        vo.setBanReason(user.getBanReason());
        vo.setBannedUntil(user.getBannedUntil());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional
    public void ban(Long userId, String reason, Integer durationDays, LoginUser operator) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "封禁理由必填");
        }
        if (durationDays != null && durationDays <= 0) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "封禁天数须为正整数");
        }
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, User.STATUS_NORMAL)
                .set(User::getStatus, User.STATUS_BANNED)
                .set(User::getBanReason, reason.trim())
                .set(User::getBannedUntil, durationDays == null ? null : LocalDateTime.now().plusDays(durationDays)));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该用户已处于封禁状态");
        }
        String untilText = durationDays == null ? "永久" : durationDays + " 天";
        notification(userId, String.format("您的账号已被封禁（%s）：%s", untilText, reason.trim()));
        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_USER_BAN, "USER", userId,
                String.format("封禁用户 %d（%s）：%s", userId, untilText, reason.trim()), null);
        log.info("管理员封禁用户：userId={}, days={}, by={}", userId, durationDays, operator.getUsername());
    }

    @Override
    @Transactional
    public void unban(Long userId, LoginUser operator) {
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, User.STATUS_BANNED)
                .set(User::getStatus, User.STATUS_NORMAL)
                .set(User::getBanReason, null)
                .set(User::getBannedUntil, null));
        if (rows == 0) {
            return;   // 未封禁：幂等跳过
        }
        notification(userId, "您的账号封禁已解除，欢迎回来");
        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_USER_UNBAN, "USER", userId, "解封用户 " + userId, null);
        log.info("管理员解封用户：userId={}, by={}", userId, operator.getUsername());
    }

    @Override
    @Transactional
    public void adjustCredit(Long userId, int change, String remark, LoginUser operator) {
        if (change == 0 || !StringUtils.hasText(remark)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "分值变动不可为 0，调整说明必填");
        }
        requireUser(userId);
        creditService.addCredit(userId, CreditLog.REASON_ADMIN_ADJUST, change, null, null, remark,
                operator.getUserId());
        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_CREDIT_ADJUST, "USER", userId,
                String.format("调整用户 %d 信用分 %+d：%s", userId, change, remark), null);
    }

    // ==================== 私有 ====================

    private void requireUser(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    /** 封禁/解封站内通知（与业务同事务落库，read_at=NULL 即未读） */
    private void notification(Long userId, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(Notification.TYPE_SYSTEM);
        n.setTitle("账号状态通知");
        n.setContent(content);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }
}
