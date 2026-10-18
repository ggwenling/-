package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.entity.OrderInfo;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.OrderDetailVO;
import com.campus.market.vo.OrderListVO;

import java.math.BigDecimal;

/**
 * 订单服务（PRD ORD-01~07 / §5.2 通用订单状态机 / 数据库设计文档 §3.11）。
 * 所有状态迁移使用条件更新（行锁）防重复下单/确认/完成/取消（PRD §8.3），
 * 状态变更、商品联动、信用流水、通知在同一事务内完成（PRD §5.2）。
 */
public interface OrderService {

    /**
     * ORD-01 买家下单（SALE）：认证 + 受限校验（CRD-02，读 LoginUser 快照不再查库）→
     * 商品必须 ON_SALE → 原子锁定 IN_TRANSACTION（并发下单仅一个成功）→
     * T11 两步式取号生成 order_no → goods_want 首次写入 + want_count+1（同事务，uk 冲突忽略）。
     *
     * @param buyer 下单者（拦截器已刷新 authStatus/creditScore）
     * @param dto   goodsId
     * @return 订单实体（含 orderNo）
     */
    OrderInfo createSaleOrder(LoginUser buyer, OrderCreateDTO dto);

    /**
     * ORD-07 可复用建单方法：供 M5 接受应约（PURCHASE）/ 同意交换（SWAP）时调用。
     * 签名：{@code OrderInfo createTransactionOrder(Long buyerId, Long sellerId, String type, Long goodsId, BigDecimal amount)}
     * PURCHASE → WAIT_CONFIRM（不锁商品，帖子置 DEALT 由 M5 负责）；
     * SWAP → 创建即 SCHEDULED 且 confirmed_at=创建时间（PRD §5.2，T7）。
     * 注意：调用方须自行完成发起者的认证/受限校验与本单来源回填（offer/swap_request.order_id）。
     *
     * @param buyerId  展示角色买方（PURCHASE=求购者 / SWAP=发起交换方）
     * @param sellerId 展示角色卖方（PURCHASE=应约者 / SWAP=帖主）
     * @param type     OrderInfo.TYPE_PURCHASE / TYPE_SWAP
     * @param goodsId  关联商品（可空）
     * @param amount   金额（PURCHASE=报价 / SWAP=0 或差价）
     * @return 订单实体（含 orderNo）
     */
    OrderInfo createTransactionOrder(Long buyerId, Long sellerId, String type, Long goodsId, BigDecimal amount);

    /**
     * ORD-02 卖家确认出售：WAIT_CONFIRM → SCHEDULED（写 confirmed_at），双方 push ORDER 通知。
     */
    OrderDetailVO confirm(Long orderId, LoginUser operator);

    /**
     * ORD-02 卖家拒绝：WAIT_CONFIRM → CANCELLED（cancelled_by=SELLER）+ 商品解锁回 ON_SALE + 通知。
     */
    OrderDetailVO reject(Long orderId, LoginUser operator, String reason);

    /**
     * ORD-03 买家取消：WAIT_CONFIRM/SCHEDULED → CANCELLED（cancelled_by=BUYER）+ 商品解锁 + 通知。
     */
    OrderDetailVO cancel(Long orderId, LoginUser operator, String reason);

    /**
     * ORD-04 确认完成：SALE/PURCHASE 仅卖家，seller_confirmed_at 写入即 COMPLETED；
     * SWAP 双方各自确认，buyer/seller_confirmed_at 都写入才 COMPLETED（T7）。
     * 完成时：商品 SOLD + 双方信用 +2（ORDER_COMPLETE）+ 双方通知。
     */
    OrderDetailVO complete(Long orderId, LoginUser operator);

    /**
     * ORD-05 我的订单分页：role=buyer|seller + 可选 status 过滤，created_at 倒序。
     */
    PageResult<OrderListVO> pageMyOrders(Long viewerId, String role, String status, long pageNum, long pageSize);

    /**
     * ORD-05 订单详情：仅订单双方可查看（否则 ORDER_FORBIDDEN），含 SWAP 双确认时间线与互评内容。
     */
    OrderDetailVO detail(Long orderId, Long viewerId);

    /**
     * ORD-03 超时自动取消（定时任务与 dev 手动触发共用入口）：
     * WAIT_CONFIRM 超 created_at+48h、SCHEDULED 超 confirmed_at+15d → CANCELLED(cancelled_by=TIMEOUT)
     * + 商品解锁 + 卖方信用 -2（CANCEL_TIMEOUT）+ 通知。
     *
     * @return 本次实际取消的订单数
     */
    int autoCancelTimeoutOrders();
}
