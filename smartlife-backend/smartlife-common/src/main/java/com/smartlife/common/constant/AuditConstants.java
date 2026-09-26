package com.smartlife.common.constant;

/**
 * 审计日志的动作与目标类型标识，落 tb_audit_log.action / target_type。
 * 动作按"动词_对象"命名，只收录已实现的接口，随批次补充。
 */
public class AuditConstants {

    private AuditConstants() {
    }

    // ==================== 动作 ====================

    public static final String ACTION_BAN_USER = "BAN_USER";
    public static final String ACTION_ENABLE_USER = "ENABLE_USER";
    public static final String ACTION_KICK_ALL = "KICK_ALL";
    public static final String ACTION_KICK_ONE = "KICK_ONE";
    public static final String ACTION_APPROVE_APPLY = "APPROVE_APPLY";
    public static final String ACTION_REJECT_APPLY = "REJECT_APPLY";
    public static final String ACTION_SHOP_SUSPEND = "SHOP_SUSPEND";
    public static final String ACTION_SHOP_RESUME = "SHOP_RESUME";
    public static final String ACTION_SHOP_DELETE = "SHOP_DELETE";
    public static final String ACTION_ORDER_ADMIN_ACCEPT = "ORDER_ADMIN_ACCEPT";
    public static final String ACTION_ORDER_ADMIN_COMPLETE = "ORDER_ADMIN_COMPLETE";
    public static final String ACTION_ORDER_ADMIN_CANCEL = "ORDER_ADMIN_CANCEL";
    public static final String ACTION_PRODUCT_OFF_SHELF = "PRODUCT_OFF_SHELF";
    public static final String ACTION_PRODUCT_ON_SHELF = "PRODUCT_ON_SHELF";

    // ==================== 目标类型 ====================

    public static final String TARGET_USER = "USER";
    public static final String TARGET_SESSION = "SESSION";
    public static final String TARGET_MERCHANT_APPLY = "MERCHANT_APPLY";
    public static final String TARGET_SHOP = "SHOP";
    public static final String TARGET_ORDER = "ORDER";
    public static final String TARGET_DISH = "DISH";
    public static final String TARGET_SETMEAL = "SETMEAL";
    public static final String TARGET_VOUCHER = "VOUCHER";
}
