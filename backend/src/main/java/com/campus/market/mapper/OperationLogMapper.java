package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper（ADM-09）
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
