package com.smartlife.common.constant;

/**
 * 正则集中定义，注解与工具类共用，避免同一正则散落多处改一漏一。
 */
public abstract class RegexPatterns {

    /**
     * 手机号。刻意取宽松版：号段一直在扩（如 192 广电），写死号段表会拒掉真实用户；
     * 这里只挡明显乱填，号段合法性由运营商发码时把关。
     */
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 短信验证码：6 位数字 */
    public static final String SMS_CODE_REGEX = "^\\d{6}$";
}
