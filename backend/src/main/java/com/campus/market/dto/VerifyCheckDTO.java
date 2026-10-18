package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 校园认证校验请求（PRD USR-03）：校验通过后写 user_auth 并升级认证状态。
 */
@Getter
@Setter
public class VerifyCheckDTO {

    @NotBlank(message = "学号不能为空")
    @Size(max = 30, message = "学号最长 30 个字符")
    private String studentNo;

    @NotBlank(message = "校园邮箱不能为空")
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String campusEmail;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
    private String code;
}
