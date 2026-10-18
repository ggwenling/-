package com.campus.market.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 认证参数（PRD USR-02/03、§5.1）：验证码时效/限频、登录锁定、校园邮箱白名单。
 * application.yml app.auth 可覆盖。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 校园邮箱域名白名单（PRD §12.1），后缀匹配 */
    private List<String> emailWhitelist = List.of("@stu.example.edu.cn");

    /** 验证码有效期（分钟） */
    private int codeTtlMinutes = 10;

    /** 同一邮箱两次发送的最小间隔（秒） */
    private int codeSendIntervalSeconds = 60;

    /** 同一邮箱每日发送上限 */
    private int codeDailyLimit = 10;

    /** 登录失败 N 次后锁定 */
    private int loginFailLimit = 5;

    /** 登录锁定时长（分钟） */
    private int loginLockMinutes = 10;

    /** 同一 IP 登录接口限流阈值：窗口内超过 N 次拒绝（PRD §7/§9.2，M7 收尾补齐） */
    private int loginIpLimit = 5;

    /** 同一 IP 登录限流窗口（秒） */
    private int loginIpWindowSeconds = 60;
}
