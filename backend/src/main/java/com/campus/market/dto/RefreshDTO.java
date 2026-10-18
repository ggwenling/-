package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 刷新令牌请求（PRD USR-02）：refreshToken 有效期 7 天，不能访问业务接口。
 */
@Getter
@Setter
public class RefreshDTO {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
