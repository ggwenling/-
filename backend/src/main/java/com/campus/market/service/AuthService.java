package com.campus.market.service;

import com.campus.market.dto.LoginDTO;
import com.campus.market.dto.RefreshDTO;
import com.campus.market.dto.RegisterDTO;
import com.campus.market.dto.SendVerifyCodeDTO;
import com.campus.market.dto.VerifyCheckDTO;
import com.campus.market.vo.LoginVO;

/**
 * 认证服务（PRD USR-01/02/03、§5.1）。
 */
public interface AuthService {

    /** 注册并自动登录（PRD USR-01） */
    LoginVO register(RegisterDTO dto);

    /** 登录：失败 5 次锁 10 分钟（PRD USR-02）+ 同 IP 限流（PRD §7）；封禁用户可登录但后续接口全部拦截 */
    LoginVO login(LoginDTO dto, String clientIp);

    /** 用 refreshToken 换发新双 token（PRD USR-02） */
    LoginVO refresh(RefreshDTO dto);

    /** 登出（无状态 JWT，客户端丢弃 token） */
    void logout(String token);

    /** 发送校园认证验证码（PRD §5.1）：白名单 + 限频；演示模式打印控制台 */
    void sendVerifyCode(SendVerifyCodeDTO dto, Long userId);

    /** 校验验证码并完成校园认证（PRD USR-03） */
    void certify(VerifyCheckDTO dto, Long userId);
}
