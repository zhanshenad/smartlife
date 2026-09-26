package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.SignVO;
import com.smartlife.server.service.ISignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户签到（BitMap）：签到与本月/连续统计。
 */
@RestController
@RequestMapping("/user/sign")
@Tag(name = "用户端-签到")
public class SignController {

    private final ISignService signService;

    public SignController(ISignService signService) {
        this.signService = signService;
    }

    @Operation(summary = "今日签到")
    @PostMapping
    public Result<SignVO> sign() {
        return Result.ok(signService.sign());
    }

    @Operation(summary = "我的签到统计")
    @GetMapping
    public Result<SignVO> mySign() {
        return Result.ok(signService.mySign());
    }
}
