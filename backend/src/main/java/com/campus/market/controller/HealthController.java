package com.campus.market.controller;

import com.campus.market.common.api.Result;
import com.campus.market.config.JacksonConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 骨架健康检查：不触碰数据库与 Redis，仅验证服务本身可启动、响应包装可用
 */
@Tag(name = "系统-健康检查")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "存活探针")
    @GetMapping
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of(
                "app", "campus-market",
                "status", "UP",
                "time", LocalDateTime.now().format(DateTimeFormatter.ofPattern(JacksonConfig.DATE_TIME_PATTERN))
        ));
    }
}
