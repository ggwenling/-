package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.AdminUserVO;

/**
 * 管理后台用户服务（PRD ADM-05 用户管理：查询/封禁/解封/信用调整，§4.2=SUPER+AUDITOR，调分 SUPER 专属）。
 * 封禁走条件 UPDATE（幂等）+ 通知；信用调整复用 CreditService（ADMIN_ADJUST，不走幂等键）。
 * 查询返回 AdminUserVO，不暴露 password 哈希（验收 P1）。
 */
public interface AdminUserService {

    /** 用户分页（username 模糊可选） */
    PageResult<AdminUserVO> page(String username, long pageNum, long pageSize);

    /** 用户详情 */
    AdminUserVO detail(Long userId);

    /**
     * 封禁用户（PRD ADM-05）：reason 必填；durationDays 非空则限期封禁，空则永久；
     * 条件 UPDATE（已封禁 rows=0 报 40919），同事务通知用户。
     */
    void ban(Long userId, String reason, Integer durationDays, LoginUser operator);

    /** 解封用户：清除封禁字段 + 通知；未封禁幂等跳过 */
    void unban(Long userId, LoginUser operator);

    /** 手动调分（PRD §5.7 ADMIN_ADJUST）：change 正加负减，remark 必填 */
    void adjustCredit(Long userId, int change, String remark, LoginUser operator);
}
