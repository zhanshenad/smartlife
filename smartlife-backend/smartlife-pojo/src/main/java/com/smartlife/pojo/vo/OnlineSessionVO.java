package com.smartlife.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 在线会话条目（全站列表用） */
@Data
@Schema(description = "在线会话")
public class OnlineSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话标识")
    private String jti;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "1 用户端 2 商家端 3 管理端")
    private Integer role;

    @Schema(description = "剩余存活秒数")
    private Long ttlSeconds;
}
