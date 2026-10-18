package com.campus.market.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * JWT 配置：accessToken 2 小时 + refreshToken 7 天（PRD USR-02）。
 * 密钥不入库不入 Git（PRD §9.2）：dev 用默认演示密钥，prod 强制从环境变量 JWT_SECRET 读取。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 签名密钥，长度不得少于 32 字符 */
    private String secret;
    /** accessToken 有效期（分钟） */
    private long accessTokenMinutes = 120;
    /** refreshToken 有效期（天） */
    private long refreshTokenDays = 7;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 未配置或长度不足 32 字符");
        }
    }
}
