package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.ReportCreateDTO;
import com.campus.market.entity.Report;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.ReportVO;

/**
 * 举报服务（PRD RPT-01 提交举报 / RPT-02 我的举报进度；数据库设计文档 §3.21）。
 * <p>
 * 职责划分：前台提交与进度查询归 M7（本接口）；后台工单处置归 M6。为 M6 复用，
 * 本接口暴露 {@link #requireById(Long)} 与 {@link #markHandled(Long, Long, String, int)}，
 * 但 <b>不</b>在此写 operation_log——后台操作日志由 M6 自行记录（PRD ADM-09）。
 * <p>
 * 校验口径：举报仅需登录且为前台用户主体，<b>不要求</b>校园认证、<b>不检查</b>信用受限
 * （PRD §3.1 未将举报列入受限禁止项；游客不可提交）。多态 target 的存在性校验见实现类。
 */
public interface ReportService {

    /**
     * RPT-01 提交举报。校验：目标存在且可举报、不能举报自己（含自己发布的内容）、
     * 同一用户对同一 (targetType,targetId) 不得重复提交待处理工单。
     *
     * @return 新建工单 ID
     */
    Long create(ReportCreateDTO dto, LoginUser user);

    /**
     * RPT-02 我的举报进度分页（created_at 倒序；status 可选 0/1/2，非法值抛 PARAM_ERROR）。
     * targetTitle 走目标表 selectById 直查，已软删目标仍可回溯标题。
     */
    PageResult<ReportVO> pageMyReports(Long userId, Integer status, long pageNum, long pageSize);

    /**
     * <b>供 M6 后台处置复用</b>：按 ID 取工单，不存在抛 NOT_FOUND（ERRORCODE REPORT_NOT_FOUND）。
     */
    Report requireById(Long reportId);

    /**
     * <b>供 M6 后台处置复用</b>：写入处置结果——更新 status（仅允许 1 已处置 / 2 已驳回，
     * 否则抛 PARAM_ERROR）/ result / handler_id / handled_at。
     * 不写 operation_log（后台操作日志由 M6 记录）。
     */
    void markHandled(Long reportId, Long handlerId, String result, int status);
}
