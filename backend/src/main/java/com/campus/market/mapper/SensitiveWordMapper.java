package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper（数据库设计文档 §3.22）
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
