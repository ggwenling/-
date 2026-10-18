package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Notification;
import com.campus.market.entity.OperationLog;
import com.campus.market.entity.Report;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.NotificationMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AdminContentService;
import com.campus.market.service.AdminUserService;
import com.campus.market.service.CreditService;
import com.campus.market.service.OperationLogService;
import com.campus.market.service.ReportService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminReportServiceImpl 深测（ADM-04：状态机 40918、组合处置分发、目标缺失 40410、双方通知）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReportServiceImplTest {

    private static final Long REPORT_ID = 700L;
    private static final Long TARGET_ID = 10L;
    private static final Long REPORTER = 1L;
    private static final Long OWNER = 2L;
    private static final Long OPERATOR_ID = 900L;

    @Mock ReportMapper reportMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock com.campus.market.mapper.WantPostMapper wantPostMapper;
    @Mock com.campus.market.mapper.SwapPostMapper swapPostMapper;
    @Mock NotificationMapper notificationMapper;
    @Mock ReportService reportService;
    @Mock AdminContentService adminContentService;
    @Mock AdminUserService adminUserService;
    @Mock CreditService creditService;
    @Mock OperationLogService operationLogService;

    @InjectMocks AdminReportServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Report.class, Goods.class, OperationLog.class, Notification.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(reportService.requireById(REPORT_ID)).thenReturn(report(Report.STATUS_PENDING));
        lenient().when(goodsMapper.selectById(TARGET_ID)).thenReturn(goods());
    }

    private Report report(int status) {
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(REPORTER);
        report.setTargetType("GOODS");
        report.setTargetId(TARGET_ID);
        report.setReportType("FAKE");
        report.setDescription("售假");
        report.setStatus(status);
        return report;
    }

    private Goods goods() {
        Goods goods = new Goods();
        goods.setId(TARGET_ID);
        goods.setUserId(OWNER);
        goods.setStatus(Goods.STATUS_ON_SALE);
        return goods;
    }

    private LoginUser operator() {
        LoginUser user = LoginUserTestFactory.admin("auditor");
        user.setUserId(OPERATOR_ID);
        return user;
    }

    // ==================== 校验 ====================

    @Test
    void handle_alreadyHandled_rejected() {
        when(reportService.requireById(REPORT_ID)).thenReturn(report(Report.STATUS_HANDLED));
        assertThatThrownBy(() -> service.handle(REPORT_ID, List.of("WARN"), "核查属实", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_ALREADY_HANDLED));
    }

    @Test
    void handle_emptyResult_rejected() {
        assertThatThrownBy(() -> service.handle(REPORT_ID, List.of("WARN"), " ", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void handle_unknownAction_rejected() {
        assertThatThrownBy(() -> service.handle(REPORT_ID, List.of("HACK"), "结果", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void handle_targetMissing_rejected() {
        when(goodsMapper.selectById(TARGET_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.handle(REPORT_ID, List.of("TAKE_DOWN"), "结果", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_TARGET_MISSING));
    }

    // ==================== 组合处置 ====================

    @Test
    void handle_comboAction_dispatchesInOrder_andMarksHandled() {
        LoginUser op = operator();
        service.handle(REPORT_ID, List.of("TAKE_DOWN", "DEDUCT", "BAN", "WARN"),
                "售假属实，下架并封号", op);

        // 委托动作
        verify(adminContentService).takeDown("GOODS", TARGET_ID, "售假属实，下架并封号", op);
        verify(creditService).addCredit(OWNER, CreditLog.REASON_REPORT_VALID,
                CreditLog.REF_TYPE_REPORT, REPORT_ID);
        verify(adminUserService).ban(eq(OWNER), contains("售假属实"), eq((Integer) null), eq(op));
        // 工单落处置
        verify(reportService).markHandled(REPORT_ID, OPERATOR_ID, "售假属实，下架并封号", Report.STATUS_HANDLED);
        // 审计 + 举报人通知
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_REPORT_HANDLE), eq("REPORT"), eq(REPORT_ID),
                contains("TAKE_DOWN"), any());
        verify(notificationMapper, times(2)).insert(any(Notification.class));   // WARN 通知 + 举报人通知（BAN 为 mock 不落库）
    }

    @Test
    void handle_noActions_onlyMarksHandled() {
        service.handle(REPORT_ID, null, "调解解决", operator());

        verify(adminContentService, never()).takeDown(any(), any(), any(), any());
        verify(reportService).markHandled(REPORT_ID, OPERATOR_ID, "调解解决", Report.STATUS_HANDLED);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());   // 仅举报人通知
        assertThat(captor.getValue().getUserId()).isEqualTo(REPORTER);
    }

    // ==================== 驳回 ====================

    @Test
    void reject_emptyResult_rejected() {
        assertThatThrownBy(() -> service.reject(REPORT_ID, null, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void reject_success_marksRejected_andNotifiesReporter() {
        service.reject(REPORT_ID, "证据不足", operator());

        verify(reportService).markHandled(REPORT_ID, OPERATOR_ID, "证据不足", Report.STATUS_REJECTED);
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_REPORT_HANDLE), eq("REPORT"), eq(REPORT_ID),
                contains("驳回"), any());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).contains("未成立").contains("证据不足");
        // 驳回不执行任何处置动作
        verify(adminContentService, never()).takeDown(any(), any(), any(), any());
        verify(creditService, never()).addCredit(any(), anyString(), any(), any());
    }
}
