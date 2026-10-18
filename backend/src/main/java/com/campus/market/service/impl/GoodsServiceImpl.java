package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.GoodsImageDTO;
import com.campus.market.dto.GoodsListQuery;
import com.campus.market.dto.GoodsPublishDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.GoodsTag;
import com.campus.market.entity.Tag;
import com.campus.market.entity.UserBehavior;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsTagMapper;
import com.campus.market.mapper.TagMapper;
import com.campus.market.mapper.UserBehaviorMapper;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserAccessGuard;
import com.campus.market.service.GoodsService;
import com.campus.market.service.RecommendService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.GoodsCardVO;
import com.campus.market.vo.GoodsDetailVO;
import org.springframework.util.StringUtils;
import com.campus.market.vo.GoodsImageVO;
import com.campus.market.vo.GoodsTagVO;
import com.campus.market.vo.SellerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品服务实现（PRD GDS-01~05、GDS-07）。
 * 一致性要点：图片/标签随发布编辑事务内全量重建；浏览埋点（T9）与 view_count+1 同事务，
 * 同人同商品同日由 uk_behavior_view_day 唯一约束兜底、冲突静默忽略；游客浏览不计浏览量（口径见 detail）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsServiceImpl implements GoodsService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final GoodsMapper goodsMapper;
    private final GoodsImageMapper goodsImageMapper;
    private final GoodsTagMapper goodsTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final FavoriteMapper favoriteMapper;
    private final SensitiveWordService sensitiveWordService;
    private final RecommendService recommendService;
    private final UserAccessGuard userAccessGuard;

    // ==================== GDS-01 发布 ====================

    @Override
    @Transactional
    public Long publish(GoodsPublishDTO dto, LoginUser user) {
        // PRD §3.1 / CRD-02：受限用户（creditScore < 受限阈值）禁止发布商品；认证+受限校验统一由 UserAccessGuard 承担
        userAccessGuard.requireInteractive(user);
        validateCommon(dto);
        Category category = requireValidCategory(dto.getCategoryId());
        checkSensitive(dto.getTitle(), dto.getDescription());

        Goods goods = new Goods();
        copyCommonFields(dto, goods);
        goods.setUserId(user.getUserId());
        goods.setCategoryId(category.getId());
        goods.setStatus(Goods.STATUS_ON_SALE);
        goods.setViewCount(0L);
        goods.setWantCount(0L);
        goods.setFavoriteCount(0L);
        goods.setHeatScore(0);
        goodsMapper.insert(goods);
        rebuildImagesAndTags(goods.getId(), dto.getImages(), dto.getTagIds());
        return goods.getId();
    }

    // ==================== GDS-02 卖家管理 ====================

    @Override
    @Transactional
    public Long update(Long id, GoodsPublishDTO dto, LoginUser user) {
        // PRD §3.1 / CRD-02：编辑与发布口径一致（同样要求已认证且未受限），避免受限用户先发布再绕过编辑
        userAccessGuard.requireInteractive(user);
        Goods goods = requireExisting(id);
        requireOwner(goods, user.getUserId());
        // PRD §6.2：编辑必须校验操作者、状态和活动订单；交易中/已售出的事实不可被编辑
        if (Goods.STATUS_IN_TRANSACTION.equals(goods.getStatus()) || Goods.STATUS_SOLD.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE, "交易中或已售出的商品不能编辑");
        }
        validateCommon(dto);
        Category category = requireValidCategory(dto.getCategoryId());
        checkSensitive(dto.getTitle(), dto.getDescription());

        Goods update = new Goods();
        update.setId(goods.getId());
        copyCommonFields(dto, update);
        update.setCategoryId(category.getId());
        goodsMapper.updateById(update);
        // 图片与标签全量重建（数据库设计文档 §3.8：事务内先删后插）
        goodsImageMapper.delete(new LambdaQueryWrapper<GoodsImage>().eq(GoodsImage::getGoodsId, id));
        goodsTagMapper.delete(new LambdaQueryWrapper<GoodsTag>().eq(GoodsTag::getGoodsId, id));
        rebuildImagesAndTags(id, dto.getImages(), dto.getTagIds());
        return id;
    }

    @Override
    @Transactional
    public void deleteByOwner(Long id, LoginUser user) {
        Goods goods = requireExisting(id);
        requireOwner(goods, user.getUserId());
        // T4 删除规则表：IN_TRANSACTION 一律拒绝删除
        if (Goods.STATUS_IN_TRANSACTION.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE, "交易中的商品不能删除");
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, Goods.STATUS_DELETED)
                .set(Goods::getDeletedAt, now)
                .set(Goods::getDeletedByType, Goods.DELETED_BY_USER)
                .set(Goods::getDeletedBy, user.getUserId())
                .set(Goods::getDeleteReason, Goods.USER_DELETE_REASON));
    }

    @Override
    @Transactional
    public void offSale(Long id, LoginUser user) {
        Goods goods = requireExisting(id);
        requireOwner(goods, user.getUserId());
        if (!Goods.STATUS_ON_SALE.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE, "仅在售商品可以下架");
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, Goods.STATUS_OFF_SALE));
    }

    @Override
    @Transactional
    public void onSale(Long id, LoginUser user) {
        Goods goods = requireExisting(id);
        requireOwner(goods, user.getUserId());
        if (!Goods.STATUS_OFF_SALE.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE, "仅已下架商品可以重新上架");
        }
        // 管理员下架（off_sale_reason 必填，ADM-02）不允许卖家自行上架，需管理员恢复（M6）
        if (StringUtils.hasText(goods.getOffSaleReason())) {
            throw new BusinessException(ErrorCode.GOODS_FORBIDDEN, "该商品因违规被平台下架，请联系管理员处理");
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, Goods.STATUS_ON_SALE)
                .set(Goods::getOffSaleReason, null));
    }

    @Override
    @Transactional
    public void restore(Long id, LoginUser user) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || !Goods.STATUS_DELETED.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        // T4 恢复规则：仅卖家自行删除（deleted_by_type=USER）且 30 天内可自行恢复；管理员删除只能管理员恢复（M6）
        if (!Goods.DELETED_BY_USER.equals(goods.getDeletedByType())
                || !Objects.equals(goods.getDeletedBy(), user.getUserId())) {
            throw new BusinessException(ErrorCode.GOODS_FORBIDDEN, "只能恢复自己删除的商品");
        }
        LocalDateTime deletedAt = goods.getDeletedAt();
        if (deletedAt == null || deletedAt.isBefore(LocalDateTime.now(BUSINESS_ZONE).minusDays(Goods.USER_RESTORE_DAYS))) {
            throw new BusinessException(ErrorCode.GOODS_FORBIDDEN, "已删除超过 30 天，无法自行恢复");
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, Goods.STATUS_OFF_SALE)
                .set(Goods::getDeletedAt, null)
                .set(Goods::getDeletedByType, null)
                .set(Goods::getDeletedBy, null)
                .set(Goods::getDeleteReason, null));
    }

    // ==================== GDS-03/04/07 列表与搜索 ====================

    @Override
    public PageResult<GoodsCardVO> pageList(GoodsListQuery query) {
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        long pageNum = Math.max(query.getPageNum(), 1);
        String sort = normalizeSort(query.getSort());

        Integer conditionLevel = query.getConditionLevel();
        if (conditionLevel != null && (conditionLevel < 1 || conditionLevel > 4)) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "成色取值须为 1~4");
        }
        BigDecimal minPrice = query.getMinPrice();
        BigDecimal maxPrice = query.getMaxPrice();
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "最低价格不能为负数");
        }
        if (maxPrice != null && minPrice != null && maxPrice.compareTo(minPrice) < 0) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "最高价格不能低于最低价格");
        }
        List<Long> categoryIds = resolveCategoryIds(query.getCategoryId());

        Page<Goods> page = new Page<>(pageNum, pageSize);
        IPage<Goods> result = goodsMapper.selectGoodsPage(page, blankToNull(query.getQ()), categoryIds,
                conditionLevel, minPrice, maxPrice,
                blankToNull(query.getCourseName()), blankToNull(query.getIsbn()), sort);
        PageResult<GoodsCardVO> voPage = new PageResult<>();
        voPage.setList(buildCards(result.getRecords()));
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    // ==================== GDS-05 详情与浏览埋点 ====================

    @Override
    @Transactional
    public GoodsDetailVO detail(Long id, LoginUser viewer) {
        Goods goods = goodsMapper.selectById(id);
        boolean isOwner = viewer != null && goods != null && Objects.equals(goods.getUserId(), viewer.getUserId());
        // T4 历史查询规则：DELETED 不进任何公开视图，仅卖家本人可见自己的已删商品
        if (goods == null || (Goods.STATUS_DELETED.equals(goods.getStatus()) && !isOwner)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        // 浏览埋点：仅登录用户计入浏览量（口径：埋点 user_id NOT NULL，游客无身份不可写入，两种口径不混用）
        if (viewer != null) {
            recordView(viewer.getUserId(), id);
        }

        GoodsDetailVO vo = new GoodsDetailVO();
        vo.setId(goods.getId());
        vo.setTitle(goods.getTitle());
        vo.setDescription(goods.getDescription());
        vo.setPrice(goods.getPrice());
        vo.setConditionLevel(goods.getConditionLevel());
        vo.setCourseName(goods.getCourseName());
        vo.setIsbn(goods.getIsbn());
        vo.setTradeLocation(goods.getTradeLocation());
        vo.setStatus(goods.getStatus());
        vo.setViewCount(goods.getViewCount());
        vo.setWantCount(goods.getWantCount());
        vo.setFavoriteCount(goods.getFavoriteCount());
        vo.setHeatScore(goods.getHeatScore());
        vo.setCreatedAt(goods.getCreatedAt());
        vo.setUpdatedAt(goods.getUpdatedAt());
        fillCategory(vo, goods.getCategoryId());
        vo.setImages(goodsImageMapper.selectList(new LambdaQueryWrapper<GoodsImage>()
                        .eq(GoodsImage::getGoodsId, id)
                        .orderByAsc(GoodsImage::getSort))
                .stream().map(img -> {
                    GoodsImageVO imageVO = new GoodsImageVO();
                    imageVO.setUrl(img.getUrl());
                    imageVO.setThumbUrl(img.getThumbUrl());
                    imageVO.setSort(img.getSort());
                    return imageVO;
                }).toList());
        vo.setTags(loadTagVos(id));
        SellerVO seller = goodsMapper.selectSellerSummary(goods.getUserId());
        vo.setSeller(seller);
        if (viewer != null) {
            Long favorited = favoriteMapper.selectCount(new LambdaQueryWrapper<com.campus.market.entity.Favorite>()
                    .eq(com.campus.market.entity.Favorite::getUserId, viewer.getUserId())
                    .eq(com.campus.market.entity.Favorite::getGoodsId, id));
            vo.setFavorited(favorited != null && favorited > 0);
        }
        // 相似推荐位（PRD REC-03 / GDS-05）：同分类 + 共享标签，Redis 缓存优先（M5 接线）
        vo.setSimilarGoods(cardsByIds(recommendService.similarGoodsIds(id, 6)));
        return vo;
    }

    // ==================== 推荐位批量卡片装配（REC-01~03） ====================

    @Override
    public List<GoodsCardVO> cardsByIds(List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = goodsIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Goods> goodsMap = goodsMapper.selectBatchIds(distinctIds).stream()
                .filter(goods -> !Goods.STATUS_DELETED.equals(goods.getStatus()))
                .collect(Collectors.toMap(Goods::getId, Function.identity(), (a, b) -> a));
        // 保持推荐算法给出的排序：按入参顺序装配
        List<Goods> ordered = distinctIds.stream().map(goodsMap::get).filter(Objects::nonNull).toList();
        return buildCards(ordered);
    }

    /**
     * 浏览埋点（T9）：写 user_behavior(VIEW, 当日 behavior_date)，与 goods.view_count+1 同事务；
     * 同人同商品同日重复浏览命中 uk_behavior_view_day 冲突 → 静默忽略且不重复计数。
     */
    private void recordView(Long userId, Long goodsId) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setGoodsId(goodsId);
        behavior.setBehavior(UserBehavior.BEHAVIOR_VIEW);
        behavior.setBehaviorDate(LocalDate.now(BUSINESS_ZONE));
        try {
            int rows = userBehaviorMapper.insert(behavior);
            if (rows > 0) {
                goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, goodsId)
                        .setSql("view_count = view_count + 1"));
            }
        } catch (DuplicateKeyException e) {
            log.debug("浏览埋点当日已存在，静默忽略：userId={}, goodsId={}", userId, goodsId);
        }
    }

    // ==================== 私有辅助 ====================

    /** 复制发布/编辑公共字段（user_id/status/派生计数由调用方控制） */
    private void copyCommonFields(GoodsPublishDTO dto, Goods goods) {
        goods.setTitle(dto.getTitle().trim());
        goods.setDescription(dto.getDescription().trim());
        goods.setPrice(dto.getPrice());
        goods.setConditionLevel(dto.getConditionLevel());
        goods.setCourseName(blankToNull(dto.getCourseName()));
        goods.setIsbn(blankToNull(dto.getIsbn()));
        goods.setTradeLocation(blankToNull(dto.getTradeLocation()));
    }

    /** 校验价格/成色等基础业务约束（DTO 注解已校验长度与非空；图片/标签约束在 rebuild 时校验） */
    private void validateCommon(GoodsPublishDTO dto) {
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "价格不能为负数");
        }
        if (dto.getConditionLevel() == null || dto.getConditionLevel() < 1 || dto.getConditionLevel() > 4) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "成色取值须为 1~4");
        }
    }

    /** 图片 1~9（sort=下标 0~8）与标签 0~5（须为启用中的标签）的事务内重建 */
    private void rebuildImagesAndTags(Long goodsId, List<GoodsImageDTO> images, List<Long> tagIds) {
        if (images == null || images.isEmpty() || images.size() > GoodsImage.MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.GOODS_IMAGE_LIMIT);
        }
        if (images.stream().anyMatch(img -> img == null || isBlank(img.getUrl()) || isBlank(img.getThumbUrl()))) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "图片地址不完整，请重新上传");
        }

        List<GoodsImage> imageRows = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            GoodsImage image = new GoodsImage();
            image.setGoodsId(goodsId);
            image.setUrl(images.get(i).getUrl());
            image.setThumbUrl(images.get(i).getThumbUrl());
            image.setSort(i);
            imageRows.add(image);
        }
        imageRows.forEach(goodsImageMapper::insert);

        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> distinctTagIds = new HashSet<>(tagIds);
        if (tagIds.stream().anyMatch(Objects::isNull) || distinctTagIds.size() != tagIds.size()) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "标签重复或非法");
        }
        if (tagIds.size() > GoodsTag.MAX_TAG_COUNT) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "标签最多选择 " + GoodsTag.MAX_TAG_COUNT + " 个");
        }
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        if (tags.size() != distinctTagIds.size()
                || tags.stream().anyMatch(t -> t.getStatus() != Tag.STATUS_ENABLED)) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "包含不存在或已停用的标签");
        }
        tagIds.forEach(tagId -> {
            GoodsTag goodsTag = new GoodsTag();
            goodsTag.setGoodsId(goodsId);
            goodsTag.setTagId(tagId);
            goodsTagMapper.insert(goodsTag);
        });
    }

    /** 二级分类校验：存在、启用、parent_id != 0 */
    private Category requireValidCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "请选择二级分类");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getParentId() == null
                || category.getParentId() == Category.ROOT_PARENT_ID
                || category.getStatus() != Category.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.GOODS_PARAM_INVALID, "分类不存在或不是启用中的二级分类");
        }
        return category;
    }

    /** 敏感词检查：标题与描述分别校验，message 指明字段与命中词（前端字段下方红字提示） */
    private void checkSensitive(String title, String description) {
        List<String> titleHits = sensitiveWordService.findHits(title);
        if (!titleHits.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_SENSITIVE, "标题包含敏感词：" + String.join("、", titleHits));
        }
        List<String> descHits = sensitiveWordService.findHits(description);
        if (!descHits.isEmpty()) {
            throw new BusinessException(ErrorCode.GOODS_SENSITIVE, "描述包含敏感词：" + String.join("、", descHits));
        }
    }

    private Goods requireExisting(Long id) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        return goods;
    }

    private void requireOwner(Goods goods, Long userId) {
        if (!Objects.equals(goods.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.GOODS_FORBIDDEN);
        }
    }

    /** 分类筛选展开：一级分类取其启用中的二级子类；二级分类精确匹配；非法 ID 直接空结果 */
    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return Collections.singletonList(-1L);
        }
        if (category.getParentId() != null && category.getParentId() == Category.ROOT_PARENT_ID) {
            List<Long> childIds = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                            .eq(Category::getParentId, categoryId)
                            .eq(Category::getStatus, Category.STATUS_ENABLED))
                    .stream().map(Category::getId).toList();
            return childIds.isEmpty() ? Collections.singletonList(-1L) : childIds;
        }
        return Collections.singletonList(categoryId);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return GoodsListQuery.SORT_LATEST;
        }
        return switch (sort) {
            case GoodsListQuery.SORT_PRICE_ASC, GoodsListQuery.SORT_PRICE_DESC, GoodsListQuery.SORT_HOT -> sort;
            default -> GoodsListQuery.SORT_LATEST;
        };
    }

    @Override
    public PageResult<GoodsCardVO> pageMine(Long userId, long pageNum, long pageSize) {
        Page<Goods> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        Page<Goods> result = goodsMapper.selectPage(page, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getUserId, userId)
                .ne(Goods::getStatus, Goods.STATUS_DELETED)
                .orderByDesc(Goods::getUpdatedAt));
        PageResult<GoodsCardVO> voPage = new PageResult<>();
        voPage.setList(buildCards(result.getRecords()));
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    /** 列表/收藏页卡片装配：批量补封面与标签，避免 N+1 */
    private List<GoodsCardVO> buildCards(List<Goods> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return List.of();
        }
        List<Long> ids = goodsList.stream().map(Goods::getId).toList();
        Map<Long, String> coverMap = goodsImageMapper.selectList(new LambdaQueryWrapper<GoodsImage>()
                        .in(GoodsImage::getGoodsId, ids)
                        .orderByAsc(GoodsImage::getSort))
                .stream()
                .collect(Collectors.toMap(GoodsImage::getGoodsId, GoodsImage::getThumbUrl, (a, b) -> a));

        Map<Long, List<String>> tagMap = loadTagNames(ids);

        return goodsList.stream().map(goods -> {
            GoodsCardVO vo = new GoodsCardVO();
            vo.setId(goods.getId());
            vo.setTitle(goods.getTitle());
            vo.setPrice(goods.getPrice());
            vo.setConditionLevel(goods.getConditionLevel());
            vo.setCoverUrl(coverMap.get(goods.getId()));
            vo.setTags(tagMap.getOrDefault(goods.getId(), List.of()));
            vo.setStatus(goods.getStatus());
            vo.setViewCount(goods.getViewCount());
            vo.setWantCount(goods.getWantCount());
            vo.setFavoriteCount(goods.getFavoriteCount());
            vo.setCreatedAt(goods.getCreatedAt());
            return vo;
        }).toList();
    }

    private Map<Long, List<String>> loadTagNames(List<Long> goodsIds) {
        List<com.campus.market.entity.GoodsTag> relations = goodsTagMapper.selectList(
                new LambdaQueryWrapper<com.campus.market.entity.GoodsTag>()
                        .in(com.campus.market.entity.GoodsTag::getGoodsId, goodsIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = relations.stream().map(com.campus.market.entity.GoodsTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> tagNames = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        return relations.stream()
                .filter(rel -> tagNames.containsKey(rel.getTagId()))
                .collect(Collectors.groupingBy(com.campus.market.entity.GoodsTag::getGoodsId,
                        Collectors.mapping(rel -> tagNames.get(rel.getTagId()), Collectors.toList())));
    }

    private List<GoodsTagVO> loadTagVos(Long goodsId) {
        List<com.campus.market.entity.GoodsTag> relations = goodsTagMapper.selectList(
                new LambdaQueryWrapper<com.campus.market.entity.GoodsTag>()
                        .eq(com.campus.market.entity.GoodsTag::getGoodsId, goodsId));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> tagIds = relations.stream().map(com.campus.market.entity.GoodsTag::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tags = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        return relations.stream()
                .filter(rel -> tags.containsKey(rel.getTagId()))
                .map(rel -> {
                    GoodsTagVO vo = new GoodsTagVO();
                    vo.setId(rel.getTagId());
                    vo.setName(tags.get(rel.getTagId()).getName());
                    return vo;
                }).toList();
    }

    private void fillCategory(GoodsDetailVO vo, Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return;
        }
        vo.setCategoryId(category.getId());
        vo.setCategoryName(category.getName());
        if (category.getParentId() != null && category.getParentId() != Category.ROOT_PARENT_ID) {
            Category parent = categoryMapper.selectById(category.getParentId());
            if (parent != null) {
                vo.setParentCategoryId(parent.getId());
                vo.setParentCategoryName(parent.getName());
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
