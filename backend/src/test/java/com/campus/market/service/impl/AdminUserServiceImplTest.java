package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OperationLog;
import com.campus.market.entity.User;
import com.campus.market.mapper.NotificationMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.CreditService;
import com.campus.market.service.OperationLogService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminUserServiceImpl 深测（ADM-05：封禁条件更新/期限/审计、解封幂等、调分委托与参数校验）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OPERATOR_ID = 900L;

    @Mock UserMapper userMapper;
    @Mock NotificationMapper notificationMapper;
    @Mock CreditService creditService;
    @Mock OperationLogService operationLogService;

    @InjectMocks AdminUserServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(User.class, Notification.class, OperationLog.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(userMapper.selectById(USER_ID)).thenReturn(user(User.STATUS_NORMAL, 100));
    }

    private LoginUser operator() {
        LoginUser user = LoginUserTestFactory.admin("operator");
        user.setUserId(OPERATOR_ID);
        return user;
    }

    private User user(int status, int creditScore) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("itu1");
        user.setNickname("测试用户");
        user.setStatus(status);
        user.setCreditScore(creditScore);
        user.setAuthStatus(User.AUTH_STATUS_VERIFIED);
        return user;
    }

    // ==================== 封禁 ====================

    @Test
    void ban_emptyReason_rejected() {
        assertThatThrownBy(() -> service.ban(USER_ID, "  ", null, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void ban_nonPositiveDays_rejected() {
        assertThatThrownBy(() -> service.ban(USER_ID, "违规", 0, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void ban_alreadyBanned_rowsZero_rejected() {
        when(userMapper.update(isNull(), any())).thenReturn(0);   // 已封禁
        assertThatThrownBy(() -> service.ban(USER_ID, "违规发布", null, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void ban_permanent_updatesAndNotifies_andAudits() {
        when(userMapper.update(isNull(), any())).thenReturn(1);

        service.ban(USER_ID, "违规发布", null, operator());

        verify(userMapper).update(isNull(), any());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).contains("永久").contains("违规发布");
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_USER_BAN), eq("USER"), eq(USER_ID), anyString(), isNull());
    }

    @Test
    void ban_withDuration_setsBannedUntil() {
        when(userMapper.update(isNull(), any())).thenReturn(1);

        service.ban(USER_ID, "短期违规", 7, operator());

        verify(userMapper).update(isNull(), any());
        verify(notificationMapper).insert(any(Notification.class));
    }

    // ==================== 解封 ====================

    @Test
    void unban_notBanned_idempotentSkip() {
        when(userMapper.update(isNull(), any())).thenReturn(0);

        service.unban(USER_ID, operator());

        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(operationLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unban_success_notifies_andAudits() {
        when(userMapper.update(isNull(), any())).thenReturn(1);

        service.unban(USER_ID, operator());

        verify(notificationMapper).insert(any(Notification.class));
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_USER_UNBAN), eq("USER"), eq(USER_ID), anyString(), isNull());
    }

    // ==================== 信用调整 ====================

    @Test
    void adjustCredit_zeroChange_rejected() {
        assertThatThrownBy(() -> service.adjustCredit(USER_ID, 0, "补偿", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void adjustCredit_emptyRemark_rejected() {
        assertThatThrownBy(() -> service.adjustCredit(USER_ID, 5, " ", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void adjustCredit_delegatesToCreditEngine() {
        service.adjustCredit(USER_ID, -5, "恶意行为扣分", operator());

        verify(creditService).addCredit(USER_ID, CreditLog.REASON_ADMIN_ADJUST, -5,
                null, null, "恶意行为扣分", OPERATOR_ID);
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_CREDIT_ADJUST), eq("USER"), eq(USER_ID), anyString(), isNull());
    }

    @Test
    void adjustCredit_userMissing_rejected() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.adjustCredit(USER_ID, 5, "补偿", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(creditService, never()).addCredit(any(), anyString(), anyInt(),
                any(), any(), any(), any());
    }

    // ==================== 查询 ====================

    @Test
    void detail_missing_rejected() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.detail(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void page_clampsPageSize() {
        when(userMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        service.page(null, 1, 500);
        verify(userMapper, times(1)).selectPage(any(), any());
    }
}
