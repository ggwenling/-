package com.campus.market.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 薄封装：只用 GET/SET/DEL/EXPIRE/INCR 等基础命令，
 * 兼容本机旧版 Redis 3.0（选型决策）。验证码、缓存、限流计数统一走此入口。
 * key 命名遵循 docs/命名规范.md：campus:market:&lt;模块&gt;:&lt;业务&gt;:&lt;标识&gt;
 */
@Component
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /** 写入并设置过期时间 */
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /** 自增并返回新值（限流计数用） */
    public long increment(String key) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    /** 为已有 key 设置过期时间（限流计数：increment 后仅首次设置，避免 SET 覆盖造成的并发少计，验收 P2②） */
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, ttl));
    }
}
