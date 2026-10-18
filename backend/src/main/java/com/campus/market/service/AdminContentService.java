package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.AdminContentVO;

/**
 * 内容巡查处置服务（PRD ADM-02，SUPER+AUDITOR）：三类内容（GOODS/WANT/SWAP）统一
 * 下架/恢复/软删，全部条件 UPDATE + 审计四元组 + 操作日志。
 */
public interface AdminContentService {

    /** 巡查分页：targetType=GOODS|WANT|SWAP；status/keyword 可选 */
    PageResult<AdminContentVO> page(String targetType, String status, String keyword,
                                    long pageNum, long pageSize);

    /**
     * 平台下架：goods ON_SALE→OFF_SALE（off_sale_reason 必填，卖家不可自行恢复）；
     * want/swap 帖 OPEN→CLOSED。重复下架幂等报错（40901/40909 语义段）。
     */
    void takeDown(String targetType, Long id, String reason, LoginUser operator);

    /** 恢复：goods 管理下架→ON_SALE；帖子 CLOSED→OPEN；软删的必须先恢复删除 */
    void restore(String targetType, Long id, LoginUser operator);

    /** 软删（审计四元组 T4，ADMIN 类型）：goods IN_TRANSACTION 拒绝 */
    void deleteContent(String targetType, Long id, String reason, LoginUser operator);
}
