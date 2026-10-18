package com.campus.market.integration;

import com.campus.market.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试基建冒烟（C1 验收）：容器启动、schema.sql 灌库、MyBatis-Plus 真实查询全链可用。
 * 种子口径：一级分类 10 + 二级 19 = 29 条（schema.sql 种子段）。
 */
@Transactional
class SchemaSmokeIntegrationTest extends AbstractMysqlIntegrationTest {

    @Autowired
    private CategoryMapper categoryMapper;

    @Test
    void schemaLoadedAndSeedCategoriesPresent() {
        Long total = categoryMapper.selectCount(null);
        assertThat(total).isEqualTo(29L);
    }

    @Test
    void topLevelCategoriesAreTen() {
        Long topLevel = categoryMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.campus.market.entity.Category>()
                        .eq(com.campus.market.entity.Category::getParentId, 0L));
        assertThat(topLevel).isEqualTo(10L);
    }
}
