package com.campus.market.security;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一身份守卫（PRD §3.1 / §4.1 / §5.7，CRD-02）。
 * 收敛商品（GDS-01/02）、订单（ORD-01）、求购（REQ-01）、交换（SWP-01）四处的
 * "校园认证 + 信用受限" 校验，保证流程唯一、口径不漂移；编辑与发布共用同一口径，避免绕过。
 * 数据来源为 {@link LoginUser} 快照（AuthInterceptor 每请求从 DB 刷新），本类不查库。
 */
@Component
@RequiredArgsConstructor
public class UserAccessGuard {

    private final CreditProperties creditProperties;

    /** 校园认证校验：未认证抛 AUTH_NOT_CERTIFIED(40304) */
    public void requireCertified(LoginUser user) {
        if (user.getAuthStatus() == null || user.getAuthStatus() != User.AUTH_STATUS_VERIFIED) {
            throw new BusinessException(ErrorCode.AUTH_NOT_CERTIFIED);
        }
    }

    /** 信用受限校验（CRD-02）：creditScore 为空或低于受限阈值抛 ACCOUNT_RESTRICTED(40302)，为空视为受限 */
    public void requireNotRestricted(LoginUser user) {
        if (user.getCreditScore() == null
                || user.getCreditScore() < creditProperties.getRestrictedThreshold()) {
            throw new BusinessException(ErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    /** 互动前置校验（PRD §3.1）：受限用户不能发布、应约、发起交换或创建新订单；先认证后受限 */
    public void requireInteractive(LoginUser user) {
        requireCertified(user);
        requireNotRestricted(user);
    }

    /** 不抛异常的可判式（供 VO 的 canOffer / canRequest 等按钮显隐使用） */
    public boolean canInteract(LoginUser user) {
        return user != null
                && user.getAuthStatus() != null && user.getAuthStatus() == User.AUTH_STATUS_VERIFIED
                && user.getCreditScore() != null
                && user.getCreditScore() >= creditProperties.getRestrictedThreshold();
    }
}
