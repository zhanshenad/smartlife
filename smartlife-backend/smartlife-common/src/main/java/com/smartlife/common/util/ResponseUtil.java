package com.smartlife.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 拦截器里直接写响应用。拦截器在 Controller 之前执行，@RestControllerAdvice 还没机会介入。
 */
@Slf4j
public class ResponseUtil {

    private ResponseUtil() {
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 写入 401。状态码与响应体同时置位，前端按哪种方式判断都不会漏 */
    public static void writeUnauthorized(HttpServletResponse response, String msg) {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.fail(msg));
    }

    public static void write(HttpServletResponse response, int status, Result<?> result) {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
        } catch (IOException e) {
            log.warn("写响应失败：{}", e.getMessage());
        }
    }
}
