package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单号每日序列实体，对应表 order_no_seq（数据库设计文档 §3.27，T11 修订）。
 * 主键为业务日期 seq_date（无自增列），取号必须用两步式 SQL（见 {@link com.campus.market.mapper.OrderNoSeqMapper}），
 * 与订单插入同事务，行锁串行化当日取号。
 */
@Getter
@Setter
@TableName("order_no_seq")
public class OrderNoSeq {

    /** 业务日期，主键（无自增列，取号用 LAST_INSERT_ID 两步式） */
    @TableId(type = IdType.INPUT)
    private LocalDate seqDate;

    /** 当日已用序号 */
    private Long seqVal;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
