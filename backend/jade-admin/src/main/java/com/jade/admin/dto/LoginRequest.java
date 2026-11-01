package com.jade.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码凭据（失败超次或开关开启时必填） */
    private String uuid;

    /** 图形验证码答案 */
    private String code;
}
