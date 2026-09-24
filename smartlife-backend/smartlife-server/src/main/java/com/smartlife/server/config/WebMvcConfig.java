package com.smartlife.server.config;

import com.smartlife.server.interceptor.AuthInterceptor;
import com.smartlife.server.interceptor.TokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册。两级拦截器的分工见《重构计划》§5.1.4。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 免登录路径。文档相关路径一个都不能漏，少一个打开 doc.html 就是白屏 401 */
    private static final String[] WHITELIST = {
            "/auth/**",
            // 浏览类接口免登录（贴近真实 App 未登录也能逛），也让 JMeter 压测免带 token
            "/shop/**",
            "/shop-type/**",
            "/dish/**",
            "/setmeal/**",
            // 只放行浏览，领券/抢券（/voucher/{id}/grab、/seckill）仍需登录
            "/voucher/list",
            "/health",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/favicon.ico",
            "/error"
    };

    private final TokenInterceptor tokenInterceptor;
    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(TokenInterceptor tokenInterceptor, AuthInterceptor authInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order 0：校验 + 填充上下文 + 续期。覆盖全部路径，且永不返回 false
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // order 1：准入判定。order 保证它一定在 TokenInterceptor 之后执行
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(WHITELIST)
                .order(1);
    }
}
