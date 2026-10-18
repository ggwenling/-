package com.campus.market.service;

import com.campus.market.dto.UserEditDTO;
import com.campus.market.entity.User;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.UserDetailVO;
import com.campus.market.vo.UserOverviewVO;

/**
 * 用户服务（PRD USR-04/05/06 + §4.1 封禁状态）。
 */
public interface UserService {

    /** 用户主页（公开，PRD USR-04）；不存在抛 NOT_FOUND */
    UserDetailVO getProfile(Long userId);

    /** 资料编辑（PRD USR-05），bio 入库前 XSS 转义 */
    void updateMe(Long userId, UserEditDTO dto);

    /** 我的聚合概览（PRD USR-06） */
    UserOverviewVO overview(Long userId);

    /**
     * 刷新登录主体的封禁/认证/信用状态（PRD §4.1/§5.7）：
     * 封禁未到期抛 ACCOUNT_BANNED；到期自动解封；回填 authStatus 与 creditScore。
     * 由 AuthInterceptor 每次请求调用（USER 主体）。
     */
    void applyFreshState(LoginUser loginUser);
}
