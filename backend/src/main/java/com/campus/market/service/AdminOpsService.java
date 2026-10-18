package com.campus.market.service;

import com.campus.market.entity.Banner;
import com.campus.market.entity.Notice;
import com.campus.market.vo.DashboardVO;
import com.campus.market.security.LoginUser;

import java.util.List;

/**
 * 运维管理服务（PRD ADM-07 轮播/公告 + ADM-08 数据大屏聚合，SUPER+OPERATOR；大屏查看三角色）。
 */
public interface AdminOpsService {

    // ==================== 轮播 ====================

    List<Banner> bannerList();

    Banner createBanner(String title, String imageUrl, String linkUrl, Integer sort);

    void updateBanner(Long id, String title, String imageUrl, String linkUrl, Integer sort, Integer status);

    void deleteBanner(Long id);

    // ==================== 公告 ====================

    List<Notice> noticeList();

    /** 新建公告（草稿态） */
    Notice createNotice(String title, String content, LoginUser operator);

    /** 编辑公告（已发布也可改内容，刷新 updated_at） */
    void updateNotice(Long id, String title, String content);

    /** 发布：0 草稿 → 1 发布（记录 publisher/published_at） */
    void publishNotice(Long id, LoginUser operator);

    /** 下线：1 发布 → 2 下线 */
    void offlineNotice(Long id, LoginUser operator);

    void deleteNotice(Long id);

    // ==================== 数据大屏（ADM-08，只读聚合） ====================

    DashboardVO dashboard();
}
