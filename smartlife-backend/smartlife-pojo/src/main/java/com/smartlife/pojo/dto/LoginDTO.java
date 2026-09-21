package com.smartlife.pojo.dto;

import com.smartlife.common.constant.RegexPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录入参（短信验证码登录，见《重构计划》§5.1.3）。
 */
@Data
@Schema(description = "登录请求")
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "手机号", example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = RegexPatterns.PHONE_REGEX, message = "手机号格式不正确")
    private String phone;

    @Schema(description = "短信验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = RegexPatterns.SMS_CODE_REGEX, message = "验证码应为 6 位数字")
    private String code;
}
