package com.campus.market.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * MyBatis-Plus 测试支撑：纯 Mockito 单测中没有 Spring/MP 启动流程，
 * LambdaQueryWrapper/LambdaUpdateWrapper 构建时解析 SFunction 列名依赖 TableInfo 缓存，
 * 需手动为涉及实体初始化，否则抛 "can not find lambda cache" 异常。
 */
public final class MpTestSupport {

    private MpTestSupport() {
    }

    /** 为给定实体初始化 TableInfo 缓存（幂等：重复初始化会被 MP 内部跳过/覆盖，无副作用） */
    @SafeVarargs
    public static void initTables(Class<?>... entities) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(MpTestSupport.class.getName());
        for (Class<?> entity : entities) {
            try {
                TableInfoHelper.initTableInfo(assistant, entity);
            } catch (Exception ignored) {
                // 已初始化过则忽略
            }
        }
    }
}
