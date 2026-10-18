package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Goods;
import com.campus.market.entity.OperationLog;
import com.campus.market.entity.SwapPost;
import com.campus.market.entity.WantPost;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.SwapPostMapper;
import com.campus.market.mapper.WantPostMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.OperationLogService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminContentServiceImpl 深测（ADM-02：三类内容下架/恢复/软删条件更新 + 审计 + 参数校验）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminContentServiceImplTest {

    private static final Long ID = 10L;
    private static final Long OPERATOR_ID = 900L;

    @Mock GoodsMapper goodsMapper;
    @Mock WantPostMapper wantPostMapper;
    @Mock SwapPostMapper swapPostMapper;
    @Mock OperationLogService operationLogService;

    @InjectMocks AdminContentServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Goods.class, WantPost.class, SwapPost.class, OperationLog.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(goodsMapper.selectById(ID)).thenReturn(goods(Goods.STATUS_OFF_SALE));
    }

    private LoginUser operator() {
        LoginUser user = LoginUserTestFactory.admin("auditor");
        user.setUserId(OPERATOR_ID);
        return user;
    }

    private Goods goods(String status) {
        Goods goods = new Goods();
        goods.setId(ID);
        goods.setUserId(1L);
        goods.setTitle("九成新台灯");
        goods.setStatus(status);
        goods.setPrice(new BigDecimal("45.00"));
        return goods;
    }

    // ==================== 参数校验 ====================

    @Test
    void takeDown_invalidTargetType_rejected() {
        assertThatThrownBy(() -> service.takeDown("USER", ID, "理由", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void takeDown_emptyReason_rejected() {
        assertThatThrownBy(() -> service.takeDown("GOODS", ID, " ", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void deleteContent_emptyReason_rejected() {
        assertThatThrownBy(() -> service.deleteContent("GOODS", ID, null, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    // ==================== goods 下架 / 恢复 / 软删 ====================

    @Test
    void takeDown_goods_rowsZero_rejected() {
        when(goodsMapper.update(isNull(), any())).thenReturn(0);   // 已下架/成交/删除
        assertThatThrownBy(() -> service.takeDown("GOODS", ID, "违规内容", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void takeDown_goods_success_audited() {
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.takeDown("GOODS", ID, "违规内容", operator());

        verify(goodsMapper).update(isNull(), any());
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_GOODS_TAKE_DOWN), eq("GOODS"), eq(ID),
                anyString(), isNull());
    }

    @Test
    void restore_goods_rowsZero_rejected() {
        when(goodsMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.restore("GOODS", ID, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void restore_goods_success_audited() {
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.restore("GOODS", ID, operator());

        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_GOODS_RESTORE), eq("GOODS"), eq(ID),
                anyString(), isNull());
    }

    @Test
    void deleteContent_goods_inTransaction_rejected() {
        when(goodsMapper.selectById(ID)).thenReturn(goods(Goods.STATUS_IN_TRANSACTION));
        assertThatThrownBy(() -> service.deleteContent("GOODS", ID, "违规", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_ON_SALE));
    }

    @Test
    void deleteContent_goods_alreadyDeleted_rejected() {
        when(goodsMapper.selectById(ID)).thenReturn(goods(Goods.STATUS_DELETED));
        assertThatThrownBy(() -> service.deleteContent("GOODS", ID, "违规", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    @Test
    void deleteContent_goods_success_writesAuditTuple() {
        when(goodsMapper.selectById(ID)).thenReturn(goods(Goods.STATUS_ON_SALE));
        when(goodsMapper.update(isNull(), any())).thenReturn(1);

        service.deleteContent("GOODS", ID, "售假违规", operator());

        verify(goodsMapper).update(isNull(), any());
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_GOODS_DELETE), eq("GOODS"), eq(ID),
                anyString(), isNull());
    }

    // ==================== 帖子处置 ====================

    @Test
    void takeDown_wantPost_success() {
        when(wantPostMapper.update(isNull(), any())).thenReturn(1);

        service.takeDown("WANT", ID, "违规求购", operator());

        verify(wantPostMapper).update(isNull(), any());
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_GOODS_TAKE_DOWN), eq("WANT"), eq(ID), anyString(), isNull());
    }

    @Test
    void restore_swapPost_rowsZero_rejected() {
        when(swapPostMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.restore("SWAP", ID, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void deleteContent_wantPost_rowsZero_notFound() {
        when(wantPostMapper.update(isNull(), any())).thenReturn(0);   // 不存在或已删
        assertThatThrownBy(() -> service.deleteContent("WANT", ID, "违规", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WANT_POST_NOT_FOUND));
    }

    @Test
    void deleteContent_swapPost_success() {
        when(swapPostMapper.update(isNull(), any())).thenReturn(1);

        service.deleteContent("SWAP", ID, "欺诈交换", operator());

        verify(swapPostMapper).update(isNull(), any());
        verify(operationLogService).record(eq(OPERATOR_ID), anyString(),
                eq(OperationLog.ACTION_GOODS_DELETE), eq("SWAP"), eq(ID), anyString(), isNull());
    }
}
