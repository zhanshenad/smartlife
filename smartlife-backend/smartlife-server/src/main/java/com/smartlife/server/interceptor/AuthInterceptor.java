package com.smartlife.server.interceptor;

import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.result.Result;
import com.smartlife.common.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 第二级拦截器：准入判定。两级分工是一个管"你是谁"、一个管"你能不能进"。
 * 免登录白名单在 WebMvcConfig 里用 excludePathPatterns 排除。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String PATH_ADMIN = "/admin/";
    private static final String PATH_MERCHANT = "/merchant/";

    private static final String MSG_NEED_LOGIN = "请先登录";
    private static final String MSG_NO_PERMISSION = "无权访问该资源";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LoginUser user = BaseContext.get();

        if (user == null) {
            // 取第一级写入的精确原因，而不是笼统地报"请先登录"——
            // 被踢下线和自然过期对用户是两件事，提示必须能区分
            Object reason = request.getAttribute(TokenInterceptor.ATTR_FAIL_REASON);
            ResponseUtil.writeUnauthorized(response,
                    reason == null ? MSG_NEED_LOGIN : String.valueOf(reason));
            return false;
        }

        String path = request.getRequestURI();

        // 管理端：仅管理员
        if (path.startsWith(PATH_ADMIN) && !user.isAdmin()) {
            ResponseUtil.write(response, HttpServletResponse.SC_FORBIDDEN, Result.fail(MSG_NO_PERMISSION));
            return false;
        }

        // 商家端：商家本人或平台管理员
        if (path.startsWith(PATH_MERCHANT) && !user.canOperateShop()) {
            ResponseUtil.write(response, HttpServletResponse.SC_FORBIDDEN, Result.fail(MSG_NO_PERMISSION));
            return false;
        }

        return true;
    }
}
