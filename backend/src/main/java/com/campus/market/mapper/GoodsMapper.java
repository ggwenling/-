package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.entity.Goods;
import com.campus.market.vo.SellerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 Mapper（PRD GDS-01~08）。
 * 列表/搜索动态 SQL 见 resources/mapper/GoodsMapper.xml（ngram 全文 MATCH...AGAINST，GDS-04）。
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    /**
     * 在售商品分页多条件查询（GDS-03/04/07）：仅 status=ON_SALE；
     * q 走 ngram 全文索引，其余条件为动态过滤；排序 latest/priceAsc/priceDesc/hot 在 XML 内选择。
     */
    IPage<Goods> selectGoodsPage(Page<Goods> page,
                                 @Param("q") String q,
                                 @Param("categoryIds") List<Long> categoryIds,
                                 @Param("conditionLevel") Integer conditionLevel,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("courseName") String courseName,
                                 @Param("isbn") String isbn,
                                 @Param("sort") String sort);

    /**
     * 卖家摘要只读投影（PRD GDS-05 卖家卡片：昵称/头像/信用分/认证状态）。
     * user 实体归 M1 所有，M2 不创建 User 实体/Importer，仅以 SQL 投影读取展示字段，避免文件冲突。
     */
    SellerVO selectSellerSummary(@Param("userId") Long userId);
}
