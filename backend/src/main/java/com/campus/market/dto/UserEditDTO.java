package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 资料编辑请求（PRD USR-05）：bio 入库前 XSS 转义（PRD §9.2）。
 */
@Getter
@Setter
public class UserEditDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    @Size(max = 100, message = "学院最长 100 个字符")
    private String college;

    @Size(max = 200, message = "简介最长 200 个字符")
    private String bio;
}
