package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.vo.FavoriteVO;

/**
 * 收藏服务（PRD GDS-06）：收藏/取消与 goods.favorite_count ±1 同事务，重复请求幂等（PRD §8.3）
 */
public interface FavoriteService {

    /** 收藏商品（幂等：已收藏直接返回成功，不重复计数） */
    void add(Long userId, Long goodsId);

    /** 取消收藏（幂等：未收藏直接返回成功） */
    void remove(Long userId, Long goodsId);

    /** 我的收藏分页（含商品卡片信息与最新状态） */
    PageResult<FavoriteVO> pageMy(Long userId, long pageNum, long pageSize);
}
