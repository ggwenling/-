package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应表 order_info（PRD ORD-01~07 / 数据库设计文档 §3.11）。
 * 出售/求购/交换三种业务共用状态机（PRD §5.2）：
 * WAIT_CONFIRM -> SCHEDULED -> COMPLETED；WAIT_CONFIRM/SCHEDULED -> CANCELLED。
 * SWAP 为双方参与者模型（T7），buyer/seller 仅作兼容展示角色，双方独立确认后才完成。
 * 状态迁移一律用条件更新防重复（PRD §8.3）；order_no 由 order_no_seq 每日序列表事务生成（T11）。
 */
@Getter
@Setter
@TableName("order_info")
public class OrderInfo {

    /** 订单类型（PRD ORD-07：三类型共用状态机） */
    public static final String TYPE_SALE = "SALE";
    public static final String TYPE_PURCHASE = "PURCHASE";
    public static final String TYPE_SWAP = "SWAP";

    /** 订单状态机（PRD §5.2） */
    public static final String STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 取消方（cancelled_by） */
    public static final String CANCELLED_BY_BUYER = "BUYER";
    public static final String CANCELLED_BY_SELLER = "SELLER";
    public static final String CANCELLED_BY_TIMEOUT = "TIMEOUT";

    /** 待确认超时窗口：48 小时（PRD ORD-03） */
    public static final int WAIT_CONFIRM_TIMEOUT_HOURS = 48;
    /** 待面交超时窗口：15 天（PRD ORD-03） */
    public static final int SCHEDULED_TIMEOUT_DAYS = 15;

    /** 业务单号前缀：SH+yyyyMMdd+6 位序号，超 999999 自动扩 7 位（T11） */
    public static final String ORDER_NO_PREFIX = "SH";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务单号，唯一（T11 两步式取号） */
    private String orderNo;

    /** SALE / PURCHASE / SWAP */
    private String type;

    /** WAIT_CONFIRM / SCHEDULED / COMPLETED / CANCELLED */
    private String status;

    /** 关联商品 ID（FK goods.id，SALE 必填，PURCHASE/SWAP 可空） */
    private Long goodsId;

    /** 展示角色买方：SALE=下单者 / PURCHASE=求购者 / SWAP=发起交换方（T7） */
    private Long buyerId;

    /** 展示角色卖方：SALE=商品发布者 / PURCHASE=应约者 / SWAP=帖主（T7） */
    private Long sellerId;

    /** 金额：SALE=商品价 / PURCHASE=应约报价 / SWAP=0 或差价（平台不经手） */
    private BigDecimal amount;

    /** 卖家确认时间（进入待面交；SWAP 创建即 SCHEDULED，此字段=创建时间） */
    private LocalDateTime confirmedAt;

    /** 买方完成确认时间（T7） */
    private LocalDateTime buyerConfirmedAt;

    /** 卖方完成确认时间（T7）：SALE/PURCHASE 写入即完成 */
    private LocalDateTime sellerConfirmedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** BUYER / SELLER / TIMEOUT */
    private String cancelledBy;

    /** 取消理由 */
    private String cancelReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
