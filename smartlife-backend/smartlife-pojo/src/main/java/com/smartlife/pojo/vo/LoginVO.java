package com.smartlife.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录出参。
 * 不返回 userId：前端不需要它——身份由 token 携带，服务端从会话里取，
 * 让前端自己传 userId 反而是个越权隐患。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录结果")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "JWT，后续请求放在 authorization 头里")
    private String token;

    @Schema(description = "角色：1 用户端 2 商家端 3 管理端。前端据此决定跳到哪个首页")
    private Integer role;

    @Schema(description = "登录后的默认首页路径")
    private String homePath;
}
