package com.smartlife.common.context;

import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;

/**
 * 当前请求的用户上下文，基于 ThreadLocal。
 * 请求结束必须 #remove()：Tomcat 复用线程，不清理会让下一个请求读到上一个用户的身份。
 */
public class BaseContext {

    private BaseContext() {
    }

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    /** 可能为 null（未登录请求、白名单路径） */
    public static LoginUser get() {
        return CONTEXT.get();
    }

    /** 需要"必定已登录"的场景用这个，省掉调用方的判空 */
    public static LoginUser require() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        return user;
    }

    public static Long getUserId() {
        return require().getId();
    }

    public static void remove() {
        CONTEXT.remove();
    }
}
