package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.SwapRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 交换请求 Mapper（PRD SWP-02/03）。
 * rejectOtherPending 用于同意某请求时原子关闭该帖其余待处理请求（T8）。
 */
@Mapper
public interface SwapRequestMapper extends BaseMapper<SwapRequest> {

    /** 事务内加行锁读取交换请求（防止并发重复同意/拒绝） */
    @Select("SELECT * FROM swap_request WHERE id = #{id} FOR UPDATE")
    SwapRequest selectByIdForUpdate(@Param("id") Long id);

    /** 该交换帖下除 keepId 外的待处理请求全部置为已拒绝（T8） */
    @Update("UPDATE swap_request SET status = " + SwapRequest.STATUS_REJECTED
            + " WHERE swap_post_id = #{swapPostId} AND status = " + SwapRequest.STATUS_PENDING
            + " AND id <> #{keepId}")
    int rejectOtherPending(@Param("swapPostId") Long swapPostId, @Param("keepId") Long keepId);
}
