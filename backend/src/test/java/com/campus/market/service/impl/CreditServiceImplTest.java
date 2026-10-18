package com.campus.market.service.impl;

import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.User;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.NotificationService;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CreditServiceImpl 深测（CRD-01：标准原因映射、clamp 0~150、幂等事件键、行锁、操作人透传）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock CreditLogMapper creditLogMapper;
    @Mock UserMapper userMapper;
    @Mock CreditProperties creditProperties;
    @Mock NotificationService notificationService;

    @InjectMocks
    CreditServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(CreditLog.class, User.class);
    }

    private void stubUser(int score) {
        User user = new User();
        user.setId(USER_ID);
        user.setCreditScore(score);
        when(creditLogMapper.selectUserForUpdate(USER_ID)).thenReturn(user);
        when(creditLogMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void standardReasons_mapToPropertyValues() {
        when(creditProperties.getOrderCompleteBonus()).thenReturn(2);
        when(creditProperties.getOrderTimeoutPenalty()).thenReturn(2);
        when(creditProperties.getViolationReportPenalty()).thenReturn(10);
        when(creditProperties.getMaliciousReportPenalty()).thenReturn(5);
        when(creditProperties.getMaxScore()).thenReturn(150);

        stubUser(100);
        service.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, 9L);
        ArgumentCaptor<CreditLog> captor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAfterScore()).isEqualTo(102);

        stubUser(100);
        service.addCredit(USER_ID, CreditLog.REASON_CANCEL_TIMEOUT, CreditLog.REF_TYPE_ORDER, 9L);
        stubUser(100);
        service.addCredit(USER_ID, CreditLog.REASON_REPORT_VALID, CreditLog.REF_TYPE_REPORT, 9L);
        stubUser(100);
        service.addCredit(USER_ID, CreditLog.REASON_MALICIOUS_REPORT, CreditLog.REF_TYPE_REPORT, 9L);
        ArgumentCaptor<CreditLog> all = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper, org.mockito.Mockito.times(4)).insert(all.capture());
        assertThat(all.getAllValues().get(1).getAfterScore()).isEqualTo(98);   // -2
        assertThat(all.getAllValues().get(2).getAfterScore()).isEqualTo(90);   // -10
        assertThat(all.getAllValues().get(3).getAfterScore()).isEqualTo(95);   // -5
    }

    @Test
    void unknownReason_paramError() {
        assertThatThrownBy(() -> service.addCredit(USER_ID, "HACK", CreditLog.REF_TYPE_ORDER, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void duplicateEvent_idempotentSkip() {
        when(creditLogMapper.selectCount(any())).thenReturn(1L);   // 幂等键已存在

        service.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, 9L);

        verify(creditLogMapper, never()).insert(any(CreditLog.class));
        verify(creditLogMapper, never()).selectUserForUpdate(any());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void nullRef_manualAdjust_skipsIdempotencyCheck() {
        stubUser(100);
        when(creditProperties.getMaxScore()).thenReturn(150);

        service.addCredit(USER_ID, CreditLog.REASON_ADMIN_ADJUST, 5, null, null, "活动奖励", 900L);

        // ref 为空：不查幂等键，直接执行
        verify(creditLogMapper, never()).selectCount(any());
        ArgumentCaptor<CreditLog> captor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAfterScore()).isEqualTo(105);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(900L);
        assertThat(captor.getValue().getRemark()).isEqualTo("活动奖励");
    }

    @Test
    void clamp_upperAndLowerBounds() {
        when(creditProperties.getMaxScore()).thenReturn(150);
        when(creditProperties.getOrderCompleteBonus()).thenReturn(2);
        // 上限：149 + 2 → 150
        stubUser(149);
        service.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, 9L);
        ArgumentCaptor<CreditLog> captor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAfterScore()).isEqualTo(150);
        // 下限：5 - 10 → 0
        org.mockito.Mockito.reset(creditLogMapper);
        when(creditProperties.getViolationReportPenalty()).thenReturn(10);
        when(creditLogMapper.selectCount(any())).thenReturn(0L);
        User low = new User();
        low.setId(USER_ID);
        low.setCreditScore(5);
        when(creditLogMapper.selectUserForUpdate(USER_ID)).thenReturn(low);
        service.addCredit(USER_ID, CreditLog.REASON_REPORT_VALID, CreditLog.REF_TYPE_REPORT, 9L);
        ArgumentCaptor<CreditLog> captor2 = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(captor2.capture());
        assertThat(captor2.getValue().getAfterScore()).isEqualTo(0);
    }

    @Test
    void userNotFound_notFoundError() {
        when(creditLogMapper.selectCount(any())).thenReturn(0L);
        when(creditLogMapper.selectUserForUpdate(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void usesPessimisticLock_notPlainSelect() {
        stubUser(100);
        when(creditProperties.getMaxScore()).thenReturn(150);
        service.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, 9L);
        verify(creditLogMapper).selectUserForUpdate(USER_ID);   // 行锁口径（T5 防读脏 before_score）
        verify(userMapper).updateById(any(User.class));
        verify(notificationService).push(eq(USER_ID), eq(com.campus.market.entity.Notification.TYPE_CREDIT),
                any(), any(), any(), any());
    }
}
