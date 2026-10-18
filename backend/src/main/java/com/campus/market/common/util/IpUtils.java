package com.campus.market.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取（PRD ADM-09 操作日志 / §9.2 限流），兼容 IPv6（≤45 字符）。
 * 安全口径（验收 P2①）：本平台为单机直连部署、无可信反向代理，
 * 一律取 TCP 连接源地址（getRemoteAddr）——X-Forwarded-For 等请求头可被客户端任意伪造，
 * 作为限流/审计键会给攻击者"轮换 IP 绕过限流"的面。
 * 未来若部署 Nginx 等反代，需改为"可信代理白名单 + 从 XFF 末位向前取第一个非可信 IP"，不可直接信任请求头。
 */
public final class IpUtils {

    private IpUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
