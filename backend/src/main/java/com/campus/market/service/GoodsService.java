package com.campus.market.service;

import com.campus.market.common.api.PageResult;
import com.campus.market.dto.GoodsListQuery;
import com.campus.market.dto.GoodsPublishDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.GoodsCardVO;
import com.campus.market.vo.GoodsDetailVO;

import java.util.List;

/**
 * 商品服务（PRD GDS-01 发布 / GDS-02 卖家管理 / GDS-03 列表 / GDS-04 搜索 / GDS-05 详情与浏览埋点）
 */
public interface GoodsService {

    /** 发布商品（需登录 + 已校园认证），返回商品 ID */
    Long publish(GoodsPublishDTO dto, LoginUser user);

    /** 编辑商品（仅本人；交易中/已售出拒绝），返回商品 ID */
    Long update(Long id, GoodsPublishDTO dto, LoginUser user);

    /** 卖家软删自己的商品（T4：写 deleted_* 审计四元组；交易中拒绝） */
    void deleteByOwner(Long id, LoginUser user);

    /** 卖家下架自己的在售商品（ON_SALE → OFF_SALE） */
    void offSale(Long id, LoginUser user);

    /** 卖家重新上架自己下架的商品（OFF_SALE → ON_SALE，PRD §5.3 状态机；管理员下架的除外） */
    void onSale(Long id, LoginUser user);

    /** 卖家恢复自己删除的商品（仅 deleted_by_type=USER 且 30 天内，恢复为 OFF_SALE） */
    void restore(Long id, LoginUser user);

    /** 在售商品分页多条件查询（含 q 全文搜索与教材 courseName/isbn 检索） */
    PageResult<GoodsCardVO> pageList(GoodsListQuery query);

    /** 我发布的商品（个人中心 Tab，除已删除外全部状态，PRD USR-06） */
    PageResult<GoodsCardVO> pageMine(Long userId, long pageNum, long pageSize);

    /** 商品详情（含图集/标签/卖家摘要/当前用户收藏态），并写浏览埋点（登录用户口径） */
    GoodsDetailVO detail(Long id, LoginUser viewer);

    /**
     * 按 ID 列表批量装配商品卡片（推荐位/相似推荐用），保持入参顺序、自动跳过不存在或已删除的商品。
     *
     * @param goodsIds 商品 ID 列表（去重后查询；空列表返回空）
     */
    List<GoodsCardVO> cardsByIds(List<Long> goodsIds);
}
