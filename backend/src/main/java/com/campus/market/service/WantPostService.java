package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.OfferCreateDTO;
import com.campus.market.dto.WantPostListQuery;
import com.campus.market.dto.WantPostPublishDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.OfferVO;
import com.campus.market.vo.WantPostCardVO;
import com.campus.market.vo.WantPostDetailVO;

/**
 * 求购服务（PRD REQ-01 发布 / REQ-02 广场与编辑关闭 / REQ-03 应约与接受 / REQ-04 状态流转）。
 * 一致性要点（PRD §5.4 + 数据库设计文档 §3.12 T8）：
 * 接受应约在同一事务内完成「锁帖 → 帖置 DEALT → 本应约置已接受并回填 order_id →
 * 其余待处理应约全部置已拒绝 → 创建 PURCHASE 订单」，任一步失败整体回滚；
 * 一人一帖最多一条待处理应约由 uk_offer_pending 唯一约束兜底。
 */
public interface WantPostService {

    /** REQ-01 发布求购帖（需登录 + 校园认证，敏感词校验），返回帖子 ID */
    Long publish(WantPostPublishDTO dto, LoginUser user);

    /** REQ-02 编辑求购帖（仅帖主，且帖子为 OPEN） */
    Long update(Long id, WantPostPublishDTO dto, LoginUser user);

    /** REQ-02 关闭求购帖（仅帖主，OPEN → CLOSED；已成交不可关闭） */
    void close(Long id, LoginUser user, String reason);

    /** REQ-02 帖主软删（T4：写 deleted_* 审计四元组；仅 OPEN/CLOSED 可删） */
    void removeByOwner(Long id, LoginUser user);

    /** REQ-02 帖主恢复自己删除的帖子（30 天内，恢复为 CLOSED） */
    void restore(Long id, LoginUser user);

    /** REQ-02 求购广场分页（状态/分类/关键词筛选，DELETED 永不返回；mine=true 只看我发布的） */
    PageResult<WantPostCardVO> pageList(WantPostListQuery query, LoginUser viewer);

    /** REQ-02 求购详情（帖主可见应约列表，其他用户仅见自己的应约） */
    WantPostDetailVO detail(Long id, LoginUser viewer);

    /** REQ-03 提交应约（需认证未受限；不能应约自己的帖；一人一帖仅一条待处理） */
    OfferVO createOffer(Long postId, OfferCreateDTO dto, LoginUser user);

    /** REQ-03 撤回自己的待处理应约（0 → 3） */
    void withdrawOffer(Long offerId, LoginUser user);

    /**
     * REQ-03/04 接受应约（仅求购者）：原子生成 PURCHASE 订单 + 关帖 + 拒绝其余待处理应约。
     * 报价成为订单金额；订单进入 M3 状态机（WAIT_CONFIRM 待应约者确认）。
     */
    OfferVO acceptOffer(Long offerId, LoginUser user);

    /** REQ-03 拒绝应约（仅求购者，0 → 2） */
    void rejectOffer(Long offerId, LoginUser user, String reason);

    /** REQ-03 查看应约状态：我提交的应约分页（可选 status：0/1/2/3） */
    PageResult<OfferVO> pageMyOffers(Long userId, Integer status, long pageNum, long pageSize);
}
