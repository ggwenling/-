package com.campus.market.security;

import com.campus.market.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析（jjwt 0.13，HS256）。
 * claims 载荷约定：sub=用户ID，username，type(USER|ADMIN)，role(管理员角色)，tt(access|refresh)
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;

    private SecretKey key;

    private SecretKey key() {
        if (key == null) {
            key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        }
        return key;
    }

    public String createAccessToken(LoginUser user) {
        return create(user, Duration.ofMinutes(properties.getAccessTokenMinutes()), TYPE_ACCESS);
    }

    public String createRefreshToken(LoginUser user) {
        return create(user, Duration.ofDays(properties.getRefreshTokenDays()), TYPE_REFRESH);
    }

    private String create(LoginUser user, Duration ttl, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("type", user.getUserType().name())
                .claim("role", user.getAdminRole())
                .claim("tt", tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key())
                .compact();
    }

    /** 解析并验签；非法或过期 token 抛出 io.jsonwebtoken.JwtException */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        user.setUserType(LoginUser.UserType.valueOf(claims.get("type", String.class)));
        user.setAdminRole(claims.get("role", String.class));
        user.setTokenType(claims.get("tt", String.class));
        return user;
    }
}
