package com.smartlife.server.controller;

import com.smartlife.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。免登录（在 WebMvcConfig 白名单里）。
 * 存在的意义不只是"探活"：P0 阶段它是验证骨架确实跑通的最小证据——
 * 能返回 200 说明 Web 层、统一响应、MyBatis-Plus、Redisson 全都装配成功了。
 */
@Tag(name = "系统", description = "健康检查等基础设施接口")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "健康检查", description = "返回服务存活状态与当前时间")
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "smartlife-server");
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.ok(data);
    }
}
