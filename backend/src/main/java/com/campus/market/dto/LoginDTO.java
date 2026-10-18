package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求（PRD USR-02）。失败 5 次锁定 10 分钟（Redis 计数）。
 */
@Getter
@Setter
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
