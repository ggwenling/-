package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Banner;
import com.campus.market.entity.Notice;
import com.campus.market.mapper.BannerMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.NoticeMapper;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminOpsServiceImpl 浅测（ADM-07/08：轮播 CRUD、公告状态机、大屏聚合数值口径）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminOpsServiceImplTest {

    private static final Long ID = 10L;
    private static final Long OPERATOR_ID = 900L;

    @Mock BannerMapper bannerMapper;
    @Mock NoticeMapper noticeMapper;
    @Mock UserMapper userMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock OrderInfoMapper orderInfoMapper;
    @Mock ReportMapper reportMapper;

    @InjectMocks AdminOpsServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Banner.class, Notice.class, com.campus.market.entity.User.class,
                com.campus.market.entity.OrderInfo.class, com.campus.market.entity.Report.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(bannerMapper.selectById(ID)).thenReturn(banner());
        lenient().when(noticeMapper.selectById(ID)).thenReturn(notice(Notice.STATUS_DRAFT));
    }

    private Banner banner() {
        Banner banner = new Banner();
        banner.setId(ID);
        banner.setImageUrl("/upload/202610/banner.png");
        banner.setStatus(Banner.STATUS_OFFLINE);
        return banner;
    }

    private Notice notice(int status) {
        Notice notice = new Notice();
        notice.setId(ID);
        notice.setTitle("开学季活动");
        notice.setContent("全场包邮");
        notice.setStatus(status);
        return notice;
    }

    private LoginUser operator() {
        LoginUser user = LoginUserTestFactory.admin("operator");
        user.setUserId(OPERATOR_ID);
        return user;
    }

    // ==================== 轮播 ====================

    @Test
    void createBanner_emptyImageUrl_rejected() {
        assertThatThrownBy(() -> service.createBanner("首页图", " ", null, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void createBanner_defaultsOffline() {
        when(bannerMapper.insert(any(Banner.class))).thenAnswer(inv -> {
            ((Banner) inv.getArgument(0)).setId(ID);
            return 1;
        });

        Banner created = service.createBanner("首页图", "/upload/b.png", "/goods/1", 1);

        assertThat(created.getStatus()).isEqualTo(Banner.STATUS_OFFLINE);   // 新建默认下线
    }

    @Test
    void deleteBanner_missing_rejected() {
        when(bannerMapper.selectById(ID)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteBanner(ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ==================== 公告状态机 ====================

    @Test
    void publishNotice_draftOnly() {
        when(noticeMapper.update(any(), any())).thenReturn(0);   // 非草稿
        assertThatThrownBy(() -> service.publishNotice(ID, operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void publishNotice_success_recordsPublisher() {
        when(noticeMapper.update(any(), any())).thenReturn(1);

        service.publishNotice(ID, operator());

        verify(noticeMapper).update(any(), any());
    }

    @Test
    void createNotice_over2000Chars_rejected() {
        assertThatThrownBy(() -> service.createNotice("标题", "长".repeat(2001), operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void createNotice_success_draftState() {
        when(noticeMapper.insert(any(Notice.class))).thenAnswer(inv -> {
            ((Notice) inv.getArgument(0)).setId(ID);
            return 1;
        });

        Notice created = service.createNotice("开学季", "全场包邮", operator());

        assertThat(created.getStatus()).isEqualTo(Notice.STATUS_DRAFT);
        assertThat(created.getPublisherId()).isEqualTo(OPERATOR_ID);
        verify(noticeMapper).insert(any(Notice.class));
    }

    // ==================== 大屏聚合（数值口径） ====================

    @Test
    void dashboard_aggregatesCounters() {
        when(userMapper.selectCount(any())).thenReturn(12L);
        when(goodsMapper.selectCount(any())).thenReturn(7L);
        when(orderInfoMapper.selectCount(any())).thenReturn(9L);
        when(orderInfoMapper.selectList(any())).thenReturn(java.util.List.of());   // GMV 空
        when(reportMapper.selectCount(any())).thenReturn(3L);

        com.campus.market.vo.DashboardVO vo = service.dashboard();

        assertThat(vo.getUserTotal()).isEqualTo(12L);
        assertThat(vo.getGoodsOnSale()).isEqualTo(7L);
        assertThat(vo.getGmvCompleted()).isZero();
        assertThat(vo.getOrdersWeekTrend()).isNotEmpty();   // 周一→今天
        assertThat(vo.getCreditDistribution()).containsKeys("优秀", "良好", "一般", "受限");
    }
}
