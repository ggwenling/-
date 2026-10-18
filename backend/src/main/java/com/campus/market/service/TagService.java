package com.campus.market.service;

import com.campus.market.vo.TagVO;

import java.util.List;

/**
 * 标签服务（PRD GDS-08：GET /api/tags 启用中标签，供发布表单多选 0~5）
 */
public interface TagService {

    /** 启用中的标签，按 sort 升序 */
    List<TagVO> listEnabled();
}
