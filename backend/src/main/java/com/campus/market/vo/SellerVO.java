package com.campus.market.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 商品卖家摘要视图（PRD GDS-05 卖家卡片：昵称/头像/信用分/认证状态联查）。
 * 仅暴露展示字段，不含学号、邮箱等敏感信息（PRD §6.1 脱敏口径）。
 */
@Getter
@Setter
public class SellerVO {

    /** 卖家用户 ID */
    private Long id;

    private String nickname;

    /** 头像 URL，空=前端默认头像 */
    private String avatar;

    /** 信用分 0~150 */
    private Integer creditScore;

    /** 0=未认证 1=已认证 */
    private Integer authStatus;
}
