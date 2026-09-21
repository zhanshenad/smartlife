package com.smartlife.common.constant;

/**
 * 用户角色，对应 tb_user.role。一个账号一个角色，避免同一 token 跨端乱用。
 */
public class RoleConstants {

    private RoleConstants() {
    }

    /** 用户端：浏览、下单、领券、探店 */
    public static final int USER = 1;

    /** 商家端：经营与履约（菜品/套餐/接单/券） */
    public static final int MERCHANT = 2;

    /** 管理端：平台治理（入驻审核、账号治理、看板） */
    public static final int ADMIN = 3;
}
