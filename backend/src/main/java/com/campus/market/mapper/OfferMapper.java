package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Offer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 求购应约 Mapper（PRD REQ-03）。
 * rejectOtherPending 用于接受应约时原子关闭该帖其余待处理应约（T8 第④步）。
 */
@Mapper
public interface OfferMapper extends BaseMapper<Offer> {

    /** 事务内加行锁读取应约（防止并发重复接受/拒绝） */
    @Select("SELECT * FROM offer WHERE id = #{id} FOR UPDATE")
    Offer selectByIdForUpdate(@Param("id") Long id);

    /** 该求购帖下除 keepId 外的待处理应约全部置为已拒绝（T8：接受时原子关帖） */
    @Update("UPDATE offer SET status = " + Offer.STATUS_REJECTED
            + " WHERE want_post_id = #{wantPostId} AND status = " + Offer.STATUS_PENDING
            + " AND id <> #{keepId}")
    int rejectOtherPending(@Param("wantPostId") Long wantPostId, @Param("keepId") Long keepId);
}
