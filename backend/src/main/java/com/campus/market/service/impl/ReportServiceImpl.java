package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.ReportCreateDTO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Report;
import com.campus.market.entity.SwapPost;
import com.campus.market.entity.User;
import com.campus.market.entity.WantPost;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.SwapPostMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.mapper.WantPostMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.service.ReportService;
import com.campus.market.vo.ReportVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 举报服务实现（PRD RPT-01 提交举报 / RPT-02 我的举报进度；数据库设计文档 §3.21 / §7 多态关联）。
 * <p>
 * 多态目标校验：GOODS/WANT/SWAP 排除 status='DELETED'，USER 判存在性；不存在/已删统一
 * REPORT_TARGET_MISSING（message 明确"举报对象不存在或已删除"）。自举报与重复待处理工单
 * 分别抛 FORBIDDEN / CONFLICT。images 以 Jackson 序列化为 JSON 文本落库（读出反序列化失败
 * 降级为空列表，不 500）。返回类型选 Long：前台提交后仅需回执，进度查询由 RPT-02 承载，
 * 避免提交路径重复组装 VO（PRD §6.5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final long MAX_PAGE_SIZE = 100L;

    private final ReportMapper reportMapper;
    private final GoodsMapper goodsMapper;
    private final WantPostMapper wantPostMapper;
    private final SwapPostMapper swapPostMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    // ==================== RPT-01 提交举报 ====================

    @Override
    @Transactional
    public Long create(ReportCreateDTO dto, LoginUser user) {
        Long reporterId = user.getUserId();
        String targetType = dto.getTargetType();
        requireTargetType(targetType);
        requireReportType(dto.getReportType());

        // ① 目标存在且可举报（多态逻辑外键，T3）
        Long ownerId = requireReportableTarget(targetType, dto.getTargetId());
        // ② 不能举报自己 / 自己发布的内容
        if (ownerId != null && Objects.equals(ownerId, reporterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    Report.TARGET_USER.equals(targetType) ? "不能举报自己" : "不能举报自己发布的内容");
        }
        // ③ 同一用户对同一对象仅一条待处理工单（先查后插；report 表无唯一约束，为既定口径）
        Long duplicate = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, dto.getTargetId())
                .eq(Report::getStatus, Report.STATUS_PENDING));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "您已举报过该对象，请等待处理");
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(dto.getTargetId());
        report.setReportType(dto.getReportType());
        report.setDescription(normalizeText(dto.getDescription()));
        report.setImages(serializeImages(dto.getImages()));
        report.setStatus(Report.STATUS_PENDING);
        reportMapper.insert(report);
        log.info("举报提交：reportId={}, reporterId={}, target={}#{}",
                report.getId(), reporterId, targetType, dto.getTargetId());
        return report.getId();
    }

    // ==================== RPT-02 我的举报进度 ====================

    @Override
    public PageResult<ReportVO> pageMyReports(Long userId, Integer status, long pageNum, long pageSize) {
        if (status != null && status != Report.STATUS_PENDING
                && status != Report.STATUS_HANDLED && status != Report.STATUS_REJECTED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "举报状态仅支持 0/1/2");
        }
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .eq(status != null, Report::getStatus, status)
                .orderByDesc(Report::getCreatedAt)
                .orderByDesc(Report::getId);
        IPage<Report> result = reportMapper.selectPage(new Page<>(current, size), wrapper);
        List<Report> records = result.getRecords();

        List<ReportVO> voList;
        if (records.isEmpty()) {
            voList = List.of();
        } else {
            // 批量预取目标标题（一次查询/一类），避免流内逐条查库
            Map<String, String> titleMap = loadTargetTitles(records);
            voList = records.stream().map(report -> toVO(report, titleMap)).toList();
        }

        PageResult<ReportVO> page = new PageResult<>();
        page.setList(new ArrayList<>(voList));
        page.setTotal(result.getTotal());
        page.setPageNum(result.getCurrent());
        page.setPageSize(result.getSize());
        return page;
    }

    // ==================== 供 M6 后台处置复用 ====================

    @Override
    public Report requireById(Long reportId) {
        Report report = reportId == null ? null : reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        return report;
    }

    @Override
    @Transactional
    public void markHandled(Long reportId, Long handlerId, String result, int status) {
        if (status != Report.STATUS_HANDLED && status != Report.STATUS_REJECTED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "处置结果仅允许 1(已处置)或 2(已驳回)");
        }
        requireById(reportId);
        // 仅待处理工单可被处置：并发下二次处置 rows=0（验收 P2④）
        int rows = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, reportId)
                .eq(Report::getStatus, Report.STATUS_PENDING)
                .set(Report::getStatus, status)
                .set(Report::getResult, normalizeText(result))
                .set(Report::getHandlerId, handlerId)
                .set(Report::getHandledAt, LocalDateTime.now(BUSINESS_ZONE)));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED);
        }
    }

    // ==================== 私有辅助 ====================

    private void requireTargetType(String targetType) {
        if (targetType == null || !isTargetType(targetType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "举报对象类型不合法");
        }
    }

    private boolean isTargetType(String targetType) {
        return switch (targetType) {
            case Report.TARGET_GOODS, Report.TARGET_WANT,
                 Report.TARGET_SWAP, Report.TARGET_USER -> true;
            default -> false;
        };
    }

    private void requireReportType(String reportType) {
        boolean valid = reportType != null && switch (reportType) {
            case Report.TYPE_VIOLATION, Report.TYPE_FRAUD,
                 Report.TYPE_COUNTERFEIT, Report.TYPE_OTHER -> true;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "举报类型不合法");
        }
    }

    /**
     * 校验多态目标存在且可举报，返回目标所有者 ID（用于自举报判断；USER 目标返回其自身 id）。
     * 不存在或已软删统一 REPORT_TARGET_MISSING。
     */
    private Long requireReportableTarget(String targetType, Long targetId) {
        switch (targetType) {
            case Report.TARGET_GOODS -> {
                Goods goods = goodsMapper.selectById(targetId);
                if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
                    throw targetMissing();
                }
                return goods.getUserId();
            }
            case Report.TARGET_WANT -> {
                WantPost post = wantPostMapper.selectById(targetId);
                if (post == null || WantPost.STATUS_DELETED.equals(post.getStatus())) {
                    throw targetMissing();
                }
                return post.getUserId();
            }
            case Report.TARGET_SWAP -> {
                SwapPost post = swapPostMapper.selectById(targetId);
                if (post == null || SwapPost.STATUS_DELETED.equals(post.getStatus())) {
                    throw targetMissing();
                }
                return post.getUserId();
            }
            default -> {
                User target = userMapper.selectById(targetId);
                if (target == null) {
                    throw targetMissing();
                }
                return target.getId();
            }
        }
    }

    private BusinessException targetMissing() {
        return new BusinessException(ErrorCode.REPORT_TARGET_MISSING, "举报对象不存在或已删除");
    }

    /** 批量加载目标标题（一次查询/一类），selectBatchIds 不过滤 DELETED，保证已软删目标仍可回溯快照 */
    private Map<String, String> loadTargetTitles(List<Report> records) {
        Set<Long> goodsIds = new HashSet<>();
        Set<Long> wantIds = new HashSet<>();
        Set<Long> swapIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Report report : records) {
            if (report.getTargetId() == null) {
                continue;
            }
            switch (report.getTargetType()) {
                case Report.TARGET_GOODS -> goodsIds.add(report.getTargetId());
                case Report.TARGET_WANT -> wantIds.add(report.getTargetId());
                case Report.TARGET_SWAP -> swapIds.add(report.getTargetId());
                case Report.TARGET_USER -> userIds.add(report.getTargetId());
                default -> { /* 非白名单类型忽略 */ }
            }
        }
        Map<String, String> titleMap = new HashMap<>();
        if (!goodsIds.isEmpty()) {
            goodsMapper.selectBatchIds(goodsIds)
                    .forEach(g -> titleMap.put(targetKey(Report.TARGET_GOODS, g.getId()), g.getTitle()));
        }
        if (!wantIds.isEmpty()) {
            wantPostMapper.selectBatchIds(wantIds)
                    .forEach(p -> titleMap.put(targetKey(Report.TARGET_WANT, p.getId()), p.getTitle()));
        }
        if (!swapIds.isEmpty()) {
            swapPostMapper.selectBatchIds(swapIds)
                    .forEach(p -> titleMap.put(targetKey(Report.TARGET_SWAP, p.getId()), p.getTitle()));
        }
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds)
                    .forEach(u -> titleMap.put(targetKey(Report.TARGET_USER, u.getId()), u.getNickname()));
        }
        return titleMap;
    }

    private String targetKey(String targetType, Long targetId) {
        return targetType + "#" + targetId;
    }

    private ReportVO toVO(Report report, Map<String, String> titleMap) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setTargetType(report.getTargetType());
        vo.setTargetId(report.getTargetId());
        vo.setTargetTitle(titleMap.get(targetKey(report.getTargetType(), report.getTargetId())));
        vo.setReportType(report.getReportType());
        vo.setDescription(report.getDescription());
        vo.setImages(parseImages(report.getImages()));
        vo.setStatus(report.getStatus());
        vo.setStatusText(statusText(report.getStatus()));
        vo.setResult(report.getResult());
        vo.setHandledAt(report.getHandledAt());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case Report.STATUS_PENDING -> "待处理";
            case Report.STATUS_HANDLED -> "已处置";
            case Report.STATUS_REJECTED -> "已驳回";
            default -> "未知";
        };
    }

    /** List&lt;String&gt; → JSON 文本；空列表落 NULL；序列化异常降级为 NULL（不阻断提交） */
    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            log.warn("举报截图序列化失败，忽略截图：{}", e.getMessage());
            return null;
        }
    }

    /** JSON 文本 → List&lt;String&gt;；为空或反序列化失败一律返回空列表（不 500） */
    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("举报截图反序列化失败，降级为空列表：{}", e.getMessage());
            return List.of();
        }
    }

    private String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
