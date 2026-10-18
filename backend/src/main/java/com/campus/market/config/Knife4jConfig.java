package com.campus.market.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档元信息：启动后访问 http://localhost:8080/doc.html（Knife4j 增强 UI）
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI campusMarketOpenApi() {
        return new OpenAPI().info(new Info()
                .title("校园二手交易系统 API")
                .description("前后台 REST 接口文档。响应结构与错误码约定见 common 包；需求依据：需求规格说明书 v1.0")
                .version("v0.1.0"));
    }
}
