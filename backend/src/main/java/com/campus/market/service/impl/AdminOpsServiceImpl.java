package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Banner;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Notice;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.Report;
import com.campus.market.entity.User;
import com.campus.market.mapper.BannerMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.NoticeMapper;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AdminOpsService;
import com.campus.market.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维管理实现（PRD ADM-07/ADM-08）：
 * - 轮播/公告为薄 CRUD；公告状态机 0 草稿→1 发布→2 下线（不逆向），发布记录 publisher/published_at；
 * - 大屏为只读聚合：多表 count + GMV 求和 + 信用四档分布 + 本周订单趋势，单接口一次拉齐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOpsServiceImpl implements AdminOpsService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final BannerMapper bannerMapper;
    private final NoticeMapper noticeMapper;
    private final UserMapper userMapper;
    private final GoodsMapper goodsMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final ReportMapper reportMapper;
    private final UserBehaviorMapper userBehaviorMapper;

    // ==================== 轮播 ====================

    @Override
    public List<Banner> bannerList() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .orderByAsc(Banner::getSort)
                .orderByAsc(Banner::getId));
    }

    @Override
    @Transactional
    public Banner createBanner(String title, String imageUrl, String linkUrl, Integer sort) {
        requireImageUrl(imageUrl);
        Banner banner = new Banner();
        banner.setTitle(title);
        banner.setImageUrl(imageUrl.trim());
        banner.setLinkUrl(linkUrl);
        banner.setSort(sort == null ? 0 : sort);
        banner.setStatus(Banner.STATUS_OFFLINE);   // 新建默认下线，确认后上线
        bannerMapper.insert(banner);
        return banner;
    }

    @Override
    @Transactional
    public void updateBanner(Long id, String title, String imageUrl, String linkUrl, Integer sort, Integer status) {
        requireBanner(id);
        Banner patch = new Banner();
        patch.setId(id);
        if (StringUtils.hasText(title)) {
            patch.setTitle(title);
        }
        if (StringUtils.hasText(imageUrl)) {
            patch.setImageUrl(imageUrl.trim());
        }
        if (linkUrl != null) {
            patch.setLinkUrl(linkUrl);
        }
        if (sort != null) {
            patch.setSort(sort);
        }
        if (status != null) {
            if (status != Banner.STATUS_OFFLINE && status != Banner.STATUS_ONLINE) {
                throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "状态取值非法");
            }
            patch.setStatus(status);
        }
        bannerMapper.updateById(patch);
    }

    @Override
    @Transactional
    public void deleteBanner(Long id) {
        requireBanner(id);
        bannerMapper.deleteById(id);
    }

    // ==================== 公告 ====================

    @Override
    public List<Notice> noticeList() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByAsc(Notice::getStatus)
                .orderByDesc(Notice::getCreatedAt));
    }

    @Override
    @Transactional
    public Notice createNotice(String title, String content, LoginUser operator) {
        requireNoticeFields(title, content);
        Notice notice = new Notice();
        notice.setTitle(title.trim());
        notice.setContent(content.trim());
        notice.setStatus(Notice.STATUS_DRAFT);
        notice.setPublisherId(operator.getUserId());
        noticeMapper.insert(notice);
        return notice;
    }

    @Override
    @Transactional
    public void updateNotice(Long id, String title, String content) {
        requireNotice(id);
        if (StringUtils.hasText(title)) {
            Notice patch = new Notice();
            patch.setId(id);
            patch.setTitle(title.trim());
            if (StringUtils.hasText(content)) {
                patch.setContent(content.trim());
            }
            noticeMapper.updateById(patch);
            return;
        }
        if (StringUtils.hasText(content)) {
            Notice patch = new Notice();
            patch.setId(id);
            patch.setContent(content.trim());
            noticeMapper.updateById(patch);
        }
    }

    @Override
    @Transactional
    public void publishNotice(Long id, LoginUser operator) {
        int rows = noticeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notice>()
                .eq(Notice::getId, id)
                .eq(Notice::getStatus, Notice.STATUS_DRAFT)
                .set(Notice::getStatus, Notice.STATUS_PUBLISHED)
                .set(Notice::getPublisherId, operator.getUserId())
                .set(Notice::getPublishedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅草稿可发布");
        }
    }

    @Override
    @Transactional
    public void offlineNotice(Long id, LoginUser operator) {
        int rows = noticeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notice>()
                .eq(Notice::getId, id)
                .eq(Notice::getStatus, Notice.STATUS_PUBLISHED)
                .set(Notice::getStatus, Notice.STATUS_OFFLINE));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅已发布公告可下线");
        }
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        requireNotice(id);
        noticeMapper.deleteById(id);
    }

    // ==================== 数据大屏（ADM-08） ====================

    @Override
    public DashboardVO dashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setUserTotal(userMapper.selectCount(null));
        vo.setUserTodayNew(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, LocalDate.now(BUSINESS_ZONE).atStartOfDay())));
        vo.setGoodsOnSale(goodsMapper.selectCount(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, Goods.STATUS_ON_SALE)));
        vo.setOrderTotal(orderInfoMapper.selectCount(null));
        vo.setOrderWaitConfirm(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_WAIT_CONFIRM)));
        vo.setOrderScheduled(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStatus, OrderInfo.STATUS_SCHEDULED)));
        vo.setReportPending(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, Report.STATUS_PENDING)));
        vo.setGmvCompleted(sumGmv());

        Map<String, Long> credit = new LinkedHashMap<>();
        credit.put("优秀", countCreditRange(120, null));
        credit.put("良好", countCreditRange(80, 119));
        credit.put("一般", countCreditRange(60, 79));
        credit.put("受限", countCreditRange(null, 59));
        vo.setCreditDistribution(credit);
        vo.setOrdersWeekTrend(weekOrderTrend());

        // M6 收尾补齐三项指标（验收遗留：认证用户数/举报处理时效/30 天活跃趋势）
        vo.setAuthUserTotal(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getAuthStatus, User.AUTH_STATUS_VERIFIED)));
        vo.setReportAvgHandleHours(avgReportHandleHours());
        vo.setDauTrend30(dauTrend30());
        return vo;
    }

    /** COMPLETED 订单金额求和（内存聚合，毕设数据量级可接受；上量后改 SQL SUM） */
    private double sumGmv() {
        return orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getStatus, OrderInfo.STATUS_COMPLETED)
                        .select(OrderInfo::getAmount))
                .stream()
                .mapToDouble(o -> o.getAmount() == null ? 0 : o.getAmount().doubleValue())
                .sum();
    }

    /** 信用分区间人数（区间闭区间，null 表示无界） */
    private long countCreditRange(Integer min, Integer max) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>();
        if (min != null) {
            wrapper.ge(User::getCreditScore, min);
        }
        if (max != null) {
            wrapper.le(User::getCreditScore, max);
        }
        return userMapper.selectCount(wrapper);
    }

    /** 本周（周一→今天）每日新增订单数 */
    private List<Long> weekOrderTrend() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<Long> trend = new ArrayList<>();
        for (LocalDate day = monday; !day.isAfter(today); day = day.plusDays(1)) {
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            trend.add(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                    .ge(OrderInfo::getCreatedAt, start)
                    .lt(OrderInfo::getCreatedAt, end)));
        }
        return trend;
    }

    /** 举报平均处理时长（小时）：status=已处置/已驳回 的工单，created_at → handled_at 内存聚合 */
    private double avgReportHandleHours() {
        List<Report> handled = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .in(Report::getStatus, Report.STATUS_HANDLED, Report.STATUS_REJECTED)
                .isNotNull(Report::getHandledAt)
                .select(Report::getCreatedAt, Report::getHandledAt));
        if (handled.isEmpty()) {
            return 0;
        }
        double totalMinutes = handled.stream()
                .mapToLong(r -> java.time.Duration.between(r.getCreatedAt(), r.getHandledAt()).toMinutes())
                .sum();
        return Math.round(totalMinutes / handled.size() / 6.0) / 10.0;
    }

    /** 近 30 天每日活跃用户（当日有任意行为记录的 distinct 用户数，M6 收尾） */
    private List<DashboardVO.DauPoint> dauTrend30() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate since = today.minusDays(29);
        List<Map<String, Object>> rows = userBehaviorMapper.selectDauSince(since);
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object dateVal = row.get("date");
            LocalDate day = dateVal == null ? null
                    : (dateVal instanceof LocalDate d ? d : LocalDate.parse(String.valueOf(dateVal)));
            Object cnt = row.get("activeUsers");
            if (day != null) {
                byDay.put(day, cnt == null ? 0 : ((Number) cnt).longValue());
            }
        }
        List<DashboardVO.DauPoint> trend = new ArrayList<>();
        for (LocalDate day = since; !day.isAfter(today); day = day.plusDays(1)) {
            DashboardVO.DauPoint point = new DashboardVO.DauPoint();
            point.setDate(day.toString());
            point.setActiveUsers(byDay.getOrDefault(day, 0L));
            trend.add(point);
        }
        return trend;
    }

    // ==================== 私有 ====================

    private void requireImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "轮播图片 URL 必填");
        }
    }

    private void requireBanner(Long id) {
        if (bannerMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播不存在");
        }
    }

    private void requireNotice(Long id) {
        if (noticeMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
    }

    private void requireNoticeFields(String title, String content) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "公告标题与内容必填");
        }
        if (content.trim().length() > 2000) {
            throw new BusinessException(ErrorCode.ADM_PARAM_INVALID, "公告内容最长 2000 字");
        }
    }
}
