package com.smartlife.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 设置/修改密码入参。首次设置（库里无密码）可不传旧密码，已有密码必须验旧密码。
 */
@Data
@Schema(description = "设置密码请求")
public class PasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "旧密码（首次设置可空，已有密码必填）")
    @Size(min = 6, max = 32, message = "旧密码长度须为 6~32 位")
    private String oldPassword;

    @Schema(description = "新密码")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为 6~32 位")
    private String newPassword;
}
