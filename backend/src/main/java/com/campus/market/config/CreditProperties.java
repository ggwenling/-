package com.campus.market.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 信用分规则常量（PRD §5.7 / §12.1）：集中一处、开发期可调，默认值与 PRD 一致。
 * application.yml 中 app.credit 可覆盖。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.credit")
public class CreditProperties {

    /** 校园认证通过后的初始信用分 */
    private int initialScore = 100;
    /** 信用分上限 */
    private int maxScore = 150;
    /** 优秀等级阈值 */
    private int excellentThreshold = 120;
    /** 良好等级阈值 */
    private int goodThreshold = 80;
    /** 受限阈值：低于该分禁止发布商品/求购/交换帖与下单 */
    private int restrictedThreshold = 60;
    /** 订单完成后双方各加分数 */
    private int orderCompleteBonus = 2;
    /** 举报属实（发布违规内容）扣分数 */
    private int violationReportPenalty = 10;
    /** 待确认订单 48h 超时（卖家责任）扣分数 */
    private int orderTimeoutPenalty = 2;
    /** 恶意举报扣分数 */
    private int maliciousReportPenalty = 5;
    /** 累计举报属实达到该次数自动封号 */
    private int banViolationCount = 3;
}
