package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 数据大屏聚合 VO（PRD ADM-08）：单接口返回全部统计块，前端一次拉取渲染。
 */
@Getter
@Setter
public class DashboardVO {

    /** 用户总数 */
    private long userTotal;

    /** 今日新增用户 */
    private long userTodayNew;

    /** 在售商品数 */
    private long goodsOnSale;

    /** 订单总数 */
    private long orderTotal;

    /** 成交总额（COMPLETED 订单 amount 求和） */
    private double gmvCompleted;

    /** 待确认订单（WAIT_CONFIRM） */
    private long orderWaitConfirm;

    /** 待办交换（SCHEDULED） */
    private long orderScheduled;

    /** 待处理举报工单 */
    private long reportPending;

    /** 信用分布（四档：优秀/良好/一般/受限 → 人数） */
    private Map<String, Long> creditDistribution;

    /** 本周 7 天每日新增订单（周一→周日，无订单为 0） */
    private List<Long> ordersWeekTrend;

    /** 已完成校园认证的用户数（M6 收尾补齐，PRD ADM-08） */
    private long authUserTotal;

    /** 举报平均处理时长（小时，仅统计已处置/已驳回工单；无数据为 0） */
    private double reportAvgHandleHours;

    /** 近 30 天每日活跃用户（当日有任意行为记录的 distinct 用户数；一个点 = DauPoint） */
    private List<DauPoint> dauTrend30;

    /** DAU 单点：日期（yyyy-MM-dd）+ 当日活跃用户数 */
    @Getter
    @Setter
    public static class DauPoint {
        private String date;
        private long activeUsers;
    }
}
