package com.campus.market.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关：订单 48h/15 天超时扫描（PRD ORD-03）、推荐离线计算（PRD REC-01）等任务在此启用
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
