package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.PageResult;
import com.campus.market.entity.OperationLog;
import com.campus.market.mapper.OperationLogMapper;
import com.campus.market.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志服务实现（PRD ADM-09 / 数据库设计文档 §3.24）：审计只写不改；
 * 记录失败不阻断主业务（日志降级为 warn），查询为 SUPER 专属能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public void record(Long adminId, String adminUsername, String action, String targetType,
                       Long targetId, String detail, String ip) {
        try {
            // 调用方未传 IP 时自动从当前请求补齐（验收 P3：审计 IP 多为 null）
            String resolvedIp = StringUtils.hasText(ip) ? ip : currentRequestIp();
            OperationLog log2 = new OperationLog();
            log2.setAdminId(adminId);
            log2.setAdminUsername(adminUsername);
            log2.setAction(action);
            log2.setTargetType(targetType);
            log2.setTargetId(targetId);
            log2.setDetail(truncate(detail));
            log2.setIp(StringUtils.hasText(resolvedIp) ? resolvedIp : "unknown");
            operationLogMapper.insert(log2);
        } catch (Exception e) {
            log.warn("操作日志写入失败（不阻断业务）：adminId={}, action={}, err={}", adminId, action, e.getMessage());
        }
    }

    /** 从当前请求线程取客户端 IP；无请求上下文（定时任务等）返回 null */
    private String currentRequestIp() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra
                ? com.campus.market.common.util.IpUtils.clientIp(sra.getRequest())
                : null;
    }

    @Override
    public PageResult<OperationLog> page(Long adminId, String action, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(adminId != null, OperationLog::getAdminId, adminId)
                .eq(StringUtils.hasText(action), OperationLog::getAction, action)
                .orderByDesc(OperationLog::getCreatedAt)
                .orderByDesc(OperationLog::getId);
        IPage<OperationLog> result = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OperationLog> records = result.getRecords();
        PageResult<OperationLog> page = new PageResult<>();
        page.setList(records);
        page.setTotal(result.getTotal());
        page.setPageNum(result.getCurrent());
        page.setPageSize(result.getSize());
        return page;
    }

    /** detail 限长 500（表定义），超长截断 */
    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() > 500 ? detail.substring(0, 500) : detail;
    }
}
