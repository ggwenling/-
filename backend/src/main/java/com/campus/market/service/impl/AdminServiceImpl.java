package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.common.util.IpUtils;
import com.campus.market.entity.Admin;
import com.campus.market.entity.OperationLog;
import com.campus.market.mapper.AdminMapper;
import com.campus.market.security.JwtUtil;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AdminService;
import com.campus.market.service.OperationLogService;
import com.campus.market.vo.AdminLoginVO;
import com.campus.market.vo.AdminVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 管理后台账号服务实现（PRD ADM-01 / §4.2 权限矩阵 / T12 / 数据库设计文档 §3.1）。
 * 一致性要点：
 * - 角色大小写口径：DB 大写、JWT 与 @RequireRole 小写，登录签发时 toLowerCase；
 * - mustChangePassword=1 仅放行 /api/admin/auth/**（AuthInterceptor 门禁 40310）；
 * - 停用账号登录与既有会话均被拦截（applyFreshState 每请求校验）；
 * - 所有 mutating 动作写 operation_log（审计快照 T5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OperationLogService operationLogService;
    private final com.campus.market.common.util.RedisService redisService;
    private final com.campus.market.config.AuthProperties authProperties;

    @Override
    @Transactional
    public AdminLoginVO login(String username, String password, String ip) {
        // 后台登录防护（验收 P3，与前台同口径复用）：IP 5 次/分钟 + 账号失败 5 次锁 10 分钟
        String ipKey = "campus:market:auth:adminloginip:" + (ip == null || ip.isBlank() ? "unknown" : ip);
        long ipHits = redisService.increment(ipKey);
        if (ipHits == 1) {
            redisService.expire(ipKey, java.time.Duration.ofMinutes(1));
        }
        if (ipHits > authProperties.getLoginIpLimit()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        String failKey = "campus:market:auth:adminloginfail:" + username;
        String fails = redisService.get(failKey);
        if (fails != null && Integer.parseInt(fails) >= authProperties.getLoginFailLimit()) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED);
        }
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username));
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            long count = redisService.increment(failKey);
            if (count == 1) {
                redisService.expire(failKey, java.time.Duration.ofMinutes(authProperties.getLoginLockMinutes()));
            }
            throw new BusinessException(ErrorCode.AUTH_CREDENTIALS_INVALID);
        }
        if (admin.getStatus() != null && admin.getStatus() == Admin.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.ADMIN_DISABLED);
        }
        redisService.delete(failKey);

        Admin patch = new Admin();
        patch.setId(admin.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        adminMapper.updateById(patch);

        // JWT 签发：adminRole 转小写（口径：DB 大写 / JWT-注解小写）
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(admin.getId());
        loginUser.setUsername(admin.getUsername());
        loginUser.setUserType(LoginUser.UserType.ADMIN);
        loginUser.setAdminRole(admin.getRole().toLowerCase(Locale.ROOT));

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(jwtUtil.createAccessToken(loginUser));
        vo.setRefreshToken(jwtUtil.createRefreshToken(loginUser));
        vo.setAdminId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setRole(loginUser.getAdminRole());
        vo.setMustChangePassword(admin.getMustChangePassword() != null && admin.getMustChangePassword() == 1);
        log.info("管理员登录：id={} role={}", admin.getId(), loginUser.getAdminRole());
        return vo;
    }

    @Override
    public AdminVO me(Long adminId) {
        Admin admin = requireAdmin(adminId);
        return toVO(admin);
    }

    @Override
    @Transactional
    public void changePassword(Long adminId, String oldPassword, String newPassword) {
        Admin admin = requireAdmin(adminId);
        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            throw new BusinessException(ErrorCode.ADM_OLD_PASSWORD_WRONG);
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "新密码至少 8 位");
        }
        Admin patch = new Admin();
        patch.setId(adminId);
        patch.setPassword(passwordEncoder.encode(newPassword));
        patch.setMustChangePassword(0);   // T12：改密后强制标记清零
        adminMapper.updateById(patch);

        operationLogService.record(adminId, admin.getUsername(), OperationLog.ACTION_RESET_PASSWORD,
                "ADMIN", adminId, "管理员自行修改密码", currentIp());
        log.info("管理员修改密码：id={}", adminId);
    }

    @Override
    public PageResult<AdminVO> page(Long adminId, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<Admin>()
                .eq(adminId != null, Admin::getId, adminId)
                .orderByAsc(Admin::getId);
        IPage<Admin> result = adminMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AdminVO> vos = result.getRecords().stream().map(this::toVO).toList();
        PageResult<AdminVO> page = new PageResult<>();
        page.setList(vos);
        page.setTotal(result.getTotal());
        page.setPageNum(result.getCurrent());
        page.setPageSize(result.getSize());
        return page;
    }

    @Override
    @Transactional
    public Long create(String username, String password, String realName, String role, LoginUser operator) {
        String normalizedRole = normalizeRole(role);
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || password.length() < 8) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "用户名必填，密码至少 8 位");
        }
        Long exists = adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.ADMIN_USERNAME_DUP);
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRealName(realName);
        admin.setRole(normalizedRole);   // DB 大写
        admin.setStatus(Admin.STATUS_ENABLED);
        admin.setMustChangePassword(1);  // 新建账号同样强制首次改密（T12 语义一致）
        adminMapper.insert(admin);

        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_ADMIN_CREATE, "ADMIN", admin.getId(),
                "新建管理员 " + username + "（" + normalizedRole + "）", currentIp());
        return admin.getId();
    }

    @Override
    @Transactional
    public void update(Long id, String realName, String role, Integer status, LoginUser operator) {
        Admin admin = requireAdmin(id);
        Admin patch = new Admin();
        patch.setId(id);
        if (StringUtils.hasText(realName)) {
            patch.setRealName(realName);
        }
        if (StringUtils.hasText(role)) {
            patch.setRole(normalizeRole(role));
        }
        if (status != null) {
            if (status != Admin.STATUS_ENABLED && status != Admin.STATUS_DISABLED) {
                throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "状态取值非法");
            }
            patch.setStatus(status);
        }
        adminMapper.updateById(patch);

        String action = Integer.valueOf(Admin.STATUS_DISABLED).equals(patch.getStatus())
                ? OperationLog.ACTION_ADMIN_DISABLE : OperationLog.ACTION_ADMIN_UPDATE;
        operationLogService.record(operator.getUserId(), operator.getUsername(), action,
                "ADMIN", id, "更新管理员 " + admin.getUsername(), currentIp());
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword, LoginUser operator) {
        Admin admin = requireAdmin(id);
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "新密码至少 8 位");
        }
        Admin patch = new Admin();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(newPassword));
        patch.setMustChangePassword(1);   // 重置后同样强制改密
        adminMapper.updateById(patch);

        operationLogService.record(operator.getUserId(), operator.getUsername(),
                OperationLog.ACTION_RESET_PASSWORD, "ADMIN", id,
                "重置管理员 " + admin.getUsername() + " 的密码", currentIp());
    }

    @Override
    public void applyFreshState(LoginUser adminUser) {
        Admin admin = adminMapper.selectById(adminUser.getUserId());
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }
        if (admin.getStatus() != null && admin.getStatus() == Admin.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.ADMIN_DISABLED);
        }
        adminUser.setAdminRole(admin.getRole().toLowerCase(Locale.ROOT));
        adminUser.setMustChangePassword(admin.getMustChangePassword());
    }

    @Override
    public AdminVO toVO(Admin admin) {
        AdminVO vo = new AdminVO();
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setRole(admin.getRole() == null ? null : admin.getRole().toLowerCase(Locale.ROOT));
        vo.setStatus(admin.getStatus());
        vo.setMustChangePassword(admin.getMustChangePassword());
        vo.setLastLoginAt(admin.getLastLoginAt());
        vo.setCreatedAt(admin.getCreatedAt());
        return vo;
    }

    // ==================== 私有 ====================

    private Admin requireAdmin(Long id) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }
        return admin;
    }

    /** 角色小写入参 → DB 大写；非法角色直接拒绝 */
    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "角色必填");
        }
        String upper = role.toUpperCase(Locale.ROOT);
        if (!Admin.ROLE_SUPER.equals(upper) && !Admin.ROLE_AUDITOR.equals(upper)
                && !Admin.ROLE_OPERATOR.equals(upper)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "角色仅支持 super/auditor/operator");
        }
        return upper;
    }

    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? "unknown"
                : IpUtils.clientIp(attributes.getRequest());
    }
}
