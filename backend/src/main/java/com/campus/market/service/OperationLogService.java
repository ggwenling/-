package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.entity.OperationLog;

/**
 * 操作日志服务（PRD ADM-09）：AOP 记录 + SUPER 查询。只写不改（审计）。
 */
public interface OperationLogService {

    /** 写入一条操作日志（adminId/username 来自当前 UserContext；ip 取请求侧） */
    void record(Long adminId, String adminUsername, String action, String targetType,
                Long targetId, String detail, String ip);

    /** 分页查询（ADM-09，SUPER 专属）：adminId/action 可选过滤 */
    PageResult<OperationLog> page(Long adminId, String action, long pageNum, long pageSize);
}
