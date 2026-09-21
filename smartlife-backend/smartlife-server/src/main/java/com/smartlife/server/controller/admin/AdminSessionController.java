package com.smartlife.server.controller.admin;

import com.smartlife.common.result.Result;
import com.smartlife.server.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端会话治理：踢人下线与在线会话查询。仅 role=3 可访问（AuthInterceptor 按路径拦截）。
 */
@RestController
@RequestMapping("/admin/session")
@Tag(name = "管理端-会话治理")
public class AdminSessionController {

    private final SessionService sessionService;

    public AdminSessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "踢掉该用户全部会话（所有端）")
    @PostMapping("/{userId}/kick")
    public Result<Void> kickAll(@PathVariable("userId") Long userId) {
        sessionService.kickAll(userId);
        return Result.ok();
    }

    @Operation(summary = "踢掉指定单个会话")
    @DeleteMapping("/{jti}")
    public Result<Void> kickOne(@PathVariable("jti") String jti) {
        sessionService.kickOne(jti);
        return Result.ok();
    }

    @Operation(summary = "某用户的在线会话列表")
    @GetMapping("/online/{userId}")
    public Result<List<String>> listOnline(@PathVariable("userId") Long userId) {
        return Result.ok(sessionService.listOnlineJti(userId));
    }
}
