package com.smartlife.common.model;

import com.smartlife.common.constant.RoleConstants;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 当前登录用户，由拦截器放入 BaseContext。
 * 只带 id / role / nickname / jti：其余资料由各业务自行查库，避免上下文里的信息越用越旧。
 */
@Data
@NoArgsConstructor
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 见 RoleConstants */
    private Integer role;

    private String nickname;

    /** 本次登录的会话标识，登出时按它删掉自己的会话 */
    private String jti;

    public LoginUser(Long id, Integer role, String nickname) {
        this.id = id;
        this.role = role;
        this.nickname = nickname;
    }

    public boolean isAdmin() {
        return role != null && role == RoleConstants.ADMIN;
    }

    public boolean isMerchant() {
        return role != null && role == RoleConstants.MERCHANT;
    }

    /** 商家端接口的准入判断：商家本人或平台管理员 */
    public boolean canOperateShop() {
        return isMerchant() || isAdmin();
    }
}
