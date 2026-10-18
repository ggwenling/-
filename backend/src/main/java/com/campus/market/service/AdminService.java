package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.entity.Admin;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.AdminLoginVO;
import com.campus.market.vo.AdminVO;

/**
 * 管理后台账号服务（PRD ADM-01 / T12）：登录、双 token、强制改密、账号 CRUD（SUPER 专属）。
 * 角色口径：DB 大写 SUPER/AUDITOR/OPERATOR，JWT/注解一律小写（登录签发时转小写）。
 */
public interface AdminService {

    /** 后台登录（PRD ADM-01）：BCrypt 校验 + 停用拦截 + mustChangePassword 透出 */
    AdminLoginVO login(String username, String password, String ip);

    /** 当前登录管理员信息 */
    AdminVO me(Long adminId);

    /** 修改自己的密码（原密码校验 + 强制改密标记清零 + 操作日志） */
    void changePassword(Long adminId, String oldPassword, String newPassword);

    /** 管理员分页（SUPER；adminId 精确过滤可选） */
    PageResult<AdminVO> page(Long adminId, long pageNum, long pageSize);

    /** 新建管理员（SUPER）：role 小写入参，落库转大写 */
    Long create(String username, String password, String realName, String role, LoginUser operator);

    /** 编辑真实姓名/角色/状态（SUPER） */
    void update(Long id, String realName, String role, Integer status, LoginUser operator);

    /** 重置他人密码（SUPER，操作日志 ADMIN_RESET_PASSWORD） */
    void resetPassword(Long id, String newPassword, LoginUser operator);

    /** 拦截器每请求刷新 ADMIN 状态：停用即抛 ADMIN_DISABLED，并回填 mustChangePassword */
    void applyFreshState(LoginUser adminUser);

    /** 实体 → VO（角色转小写，密码不出） */
    AdminVO toVO(Admin admin);
}
