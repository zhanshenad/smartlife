package com.smartlife.server.controller.user;

import com.smartlife.common.constant.RegexPatterns;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.LoginDTO;
import com.smartlife.pojo.vo.LoginVO;
import com.smartlife.server.service.IUserService;
import com.smartlife.server.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证入口：发码、登录、登出。整个 /auth 路径在免登录白名单里。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/auth")
@Tag(name = "认证接口")
public class AuthController {

    private final IUserService userService;
    private final SessionService sessionService;

    public AuthController(IUserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    @Operation(summary = "发送短信验证码")
    @PostMapping("/code")
    public Result<Void> sendCode(
            @RequestParam("phone")
            @Pattern(regexp = RegexPatterns.PHONE_REGEX, message = "手机号格式不正确")
            String phone) {
        userService.sendCode(phone);
        return Result.ok();
    }

    @Operation(summary = "验证码登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @Operation(summary = "登出，仅踢当前设备")
    @PostMapping("/logout")
    public Result<Void> logout() {
        sessionService.logoutCurrent();
        return Result.ok();
    }
}
