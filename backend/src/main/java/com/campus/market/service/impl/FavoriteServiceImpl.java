package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.entity.Favorite;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.GoodsTag;
import com.campus.market.entity.Tag;
import com.campus.market.entity.UserBehavior;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.TagMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.campus.market.service.FavoriteService;
import com.campus.market.vo.FavoriteVO;
import com.campus.market.vo.GoodsCardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏服务实现（PRD GDS-06 / 数据库设计文档 §3.17、T5）。
 * 一致性要点：收藏插入/取消删除与 goods.favorite_count ±1 同事务；uk_user_goods 唯一约束 +
 * DuplicateKeyException 静默兜底保证重复收藏幂等；GREATEST 防计数变负。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsImageMapper goodsImageMapper;
    private final GoodsTagMapper goodsTagMapper;
    private final TagMapper tagMapper;
    private final UserBehaviorMapper userBehaviorMapper;

    @Override
    @Transactional
    public void add(Long userId, Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        // 幂等：已收藏直接成功（PRD §8.3 重复收藏幂等）
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getGoodsId, goodsId));
        if (exists != null && exists > 0) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setGoodsId(goodsId);
        try {
            int rows = favoriteMapper.insert(favorite);
            if (rows > 0) {
                goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, goodsId)
                        .setSql("favorite_count = favorite_count + 1"));
                // 推荐输入埋点（PRD §6.6：收藏权重 3），仅在实际新增收藏时写入
                recordFavoriteBehavior(userId, goodsId);
            }
        } catch (DuplicateKeyException e) {
            // 并发重复收藏：唯一约束兜底，视为已收藏
            log.debug("重复收藏并发冲突，幂等处理：userId={}, goodsId={}", userId, goodsId);
        }
    }

    /** 收藏行为埋点（REC-01 输入）：user_behavior(FAVORITE, 当日) */
    private void recordFavoriteBehavior(Long userId, Long goodsId) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setGoodsId(goodsId);
        behavior.setBehavior(UserBehavior.BEHAVIOR_FAVORITE);
        behavior.setBehaviorDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        userBehaviorMapper.insert(behavior);
    }

    @Override
    @Transactional
    public void remove(Long userId, Long goodsId) {
        int rows = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getGoodsId, goodsId));
        if (rows > 0) {
            // 仅在实际删除时回退计数；GREATEST 防止事实/计数不一致时变负（T5）
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, goodsId)
                    .setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
        }
    }

    @Override
    public PageResult<FavoriteVO> pageMy(Long userId, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        Page<Favorite> page = new Page<>(pageNum, pageSize);
        IPage<Favorite> result = favoriteMapper.selectPage(page, new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreatedAt)
                .orderByDesc(Favorite::getId));

        List<Favorite> records = result.getRecords();
        Map<Long, Goods> goodsMap = records.isEmpty() ? Map.of()
                : goodsMapper.selectBatchIds(records.stream().map(Favorite::getGoodsId).toList()).stream()
                        .collect(Collectors.toMap(Goods::getId, Function.identity()));
        Map<Long, String> coverMap = records.isEmpty() ? Map.of() : loadCovers(records);
        Map<Long, List<String>> tagMap = records.isEmpty() ? Map.of() : loadTagNames(records);

        List<FavoriteVO> voList = records.stream()
                .map(favorite -> {
                    FavoriteVO vo = new FavoriteVO();
                    vo.setId(favorite.getId());
                    vo.setGoodsId(favorite.getGoodsId());
                    vo.setCreatedAt(favorite.getCreatedAt());
                    Goods goods = goodsMap.get(favorite.getGoodsId());
                    if (goods != null) {
                        // 商品可能已被下架/删除：携带最新状态，前端灰态展示
                        vo.setGoods(toCard(goods, coverMap.get(goods.getId()), tagMap.get(goods.getId())));
                    }
                    return vo;
                }).toList();

        PageResult<FavoriteVO> voPage = new PageResult<>();
        voPage.setList(voList);
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    private GoodsCardVO toCard(Goods goods, String coverUrl, List<String> tags) {
        GoodsCardVO card = new GoodsCardVO();
        card.setId(goods.getId());
        card.setTitle(goods.getTitle());
        card.setPrice(goods.getPrice());
        card.setConditionLevel(goods.getConditionLevel());
        card.setCoverUrl(coverUrl);
        card.setTags(tags == null ? List.of() : tags);
        card.setStatus(goods.getStatus());
        card.setViewCount(goods.getViewCount());
        card.setWantCount(goods.getWantCount());
        card.setFavoriteCount(goods.getFavoriteCount());
        card.setCreatedAt(goods.getCreatedAt());
        return card;
    }

    /** goods_id -> sort 最小的缩略图（封面） */
    private Map<Long, String> loadCovers(List<Favorite> records) {
        List<Long> goodsIds = records.stream().map(Favorite::getGoodsId).toList();
        return goodsImageMapper.selectList(new LambdaQueryWrapper<GoodsImage>()
                        .in(GoodsImage::getGoodsId, goodsIds)
                        .orderByAsc(GoodsImage::getSort))
                .stream()
                .collect(Collectors.toMap(GoodsImage::getGoodsId, GoodsImage::getThumbUrl, (a, b) -> a));
    }

    /** goods_id -> 标签名列表（一次批量查询，避免 N+1） */
    private Map<Long, List<String>> loadTagNames(List<Favorite> records) {
        List<Long> goodsIds = records.stream().map(Favorite::getGoodsId).toList();
        List<GoodsTag> relations = goodsTagMapper.selectList(new LambdaQueryWrapper<GoodsTag>()
                .in(GoodsTag::getGoodsId, goodsIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = relations.stream().map(GoodsTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> tagNames = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        return relations.stream()
                .filter(rel -> tagNames.containsKey(rel.getTagId()))
                .collect(Collectors.groupingBy(GoodsTag::getGoodsId,
                        Collectors.mapping(rel -> tagNames.get(rel.getTagId()), Collectors.toList())));
    }
}
