package com.campus.market.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送校园认证验证码请求（PRD USR-03 / §5.1）：学号 + 校园邮箱，域名白名单校验。
 */
@Getter
@Setter
public class SendVerifyCodeDTO {

    @NotBlank(message = "学号不能为空")
    @Size(max = 30, message = "学号最长 30 个字符")
    private String studentNo;

    @NotBlank(message = "校园邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String campusEmail;
}
