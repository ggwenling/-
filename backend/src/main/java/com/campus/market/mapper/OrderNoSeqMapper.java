package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.OrderNoSeq;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 订单号每日序列 Mapper（数据库设计文档 §3.27，T11 修订）。
 * 两步式取号（实测修正：本表无自增列，单语句 upsert 当天首次取号时 LAST_INSERT_ID() 返回 0）：
 * <pre>
 *   INSERT IGNORE INTO order_no_seq (seq_date, seq_val) VALUES (?, 0);
 *   UPDATE order_no_seq SET seq_val = LAST_INSERT_ID(seq_val + 1) WHERE seq_date = ?;
 *   SELECT LAST_INSERT_ID();
 * </pre>
 * 三条语句必须在同一事务内执行（同连接，LAST_INSERT_ID 为连接级变量），与订单插入同事务。
 */
@Mapper
public interface OrderNoSeqMapper extends BaseMapper<OrderNoSeq> {

    /** 占位行：当天首次取号前插入 seq_val=0，已存在则忽略 */
    @Insert("INSERT IGNORE INTO order_no_seq (seq_date, seq_val) VALUES (#{seqDate}, 0)")
    int insertIgnore(@Param("seqDate") LocalDate seqDate);

    /** 序号自增并写入连接级 LAST_INSERT_ID（行锁串行化当日取号） */
    @Update("UPDATE order_no_seq SET seq_val = LAST_INSERT_ID(seq_val + 1) WHERE seq_date = #{seqDate}")
    int incrementAndGet(@Param("seqDate") LocalDate seqDate);

    /** 读取连接级 LAST_INSERT_ID（须与 {@link #incrementAndGet} 同事务同连接） */
    @Select("SELECT LAST_INSERT_ID()")
    long selectLastInsertId();
}
