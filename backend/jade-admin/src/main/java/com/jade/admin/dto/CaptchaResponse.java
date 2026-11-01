package com.jade.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图形验证码（SVG data URI，前端直接作为 img src）
 */
@Data
@AllArgsConstructor
public class CaptchaResponse {
    /** 一次性凭据，登录时随 uuid/code 回传 */
    private String uuid;
    /** 图片（data:image/svg+xml;base64,...） */
    private String image;
}
