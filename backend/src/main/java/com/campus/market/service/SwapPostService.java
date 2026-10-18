package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.SwapPostListQuery;
import com.campus.market.dto.SwapPostPublishDTO;
import com.campus.market.dto.SwapRequestCreateDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.SwapPostCardVO;
import com.campus.market.vo.SwapPostDetailVO;
import com.campus.market.vo.SwapRequestVO;

/**
 * 交换服务（PRD SWP-01 发布 / SWP-02 广场与发起交换 / SWP-03 同意或拒绝）。
 * 一致性要点（PRD §5.5 + 数据库设计文档 §3.14 T8）：同意请求在同一事务内完成
 * 「锁帖 → 帖置 DEALT → 本请求置已同意并回填 order_id → 其余待处理请求全部置已拒绝 →
 * 创建 SWAP 订单（创建即 SCHEDULED）」；一人一帖最多一条待处理请求由 uk_swap_req_pending 兜底。
 */
public interface SwapPostService {

    /** SWP-01 发布交换帖（需登录 + 校园认证；差价联动校验），返回帖子 ID */
    Long publish(SwapPostPublishDTO dto, LoginUser user);

    /** SWP-01 编辑交换帖（仅帖主且 OPEN） */
    Long update(Long id, SwapPostPublishDTO dto, LoginUser user);

    /** SWP-02 关闭交换帖（仅帖主，OPEN → CLOSED） */
    void close(Long id, LoginUser user, String reason);

    /** SWP-02 帖主软删（T4 审计四元组；仅 OPEN/CLOSED 可删） */
    void removeByOwner(Long id, LoginUser user);

    /** SWP-02 帖主恢复自己删除的帖子（30 天内，恢复为 CLOSED） */
    void restore(Long id, LoginUser user);

    /** SWP-02 交换广场分页（状态/分类/关键词筛选） */
    PageResult<SwapPostCardVO> pageList(SwapPostListQuery query, LoginUser viewer);

    /** SWP-02 交换详情（帖主可见请求列表，其他用户仅见自己的请求） */
    SwapPostDetailVO detail(Long id, LoginUser viewer);

    /** SWP-02 发起交换（需认证未受限；不能对自己发；关联商品须为本人 ON_SALE 商品） */
    SwapRequestVO createRequest(Long postId, SwapRequestCreateDTO dto, LoginUser user);

    /**
     * SWP-03 同意交换请求（仅帖主）：原子生成 SWAP 订单（创建即 SCHEDULED）+
     * 关帖 + 拒绝其余待处理请求；金额取差价（allow_diff=1）否则 0。
     */
    SwapRequestVO acceptRequest(Long requestId, LoginUser user);

    /** SWP-03 拒绝交换请求（仅帖主，0 → 2） */
    void rejectRequest(Long requestId, LoginUser user, String reason);

    /** 我发起的交换请求分页（可选 status：0/1/2） */
    PageResult<SwapRequestVO> pageMyRequests(Long userId, Integer status, long pageNum, long pageSize);
}
