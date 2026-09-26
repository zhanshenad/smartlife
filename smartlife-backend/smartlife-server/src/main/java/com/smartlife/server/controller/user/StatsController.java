package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.server.service.IStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UV 统计（HyperLogLog）查询。
 */
@RestController
@RequestMapping("/stats")
@Tag(name = "用户端-UV统计")
public class StatsController {

    private final IStatsService statsService;

    public StatsController(IStatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "店铺某天 UV（登录用户口径）")
    @GetMapping("/uv")
    public Result<Long> shopUv(@RequestParam("shopId") Long shopId,
                               @RequestParam(required = false) String date) {
        return Result.ok(statsService.shopUv(shopId, date));
    }
}
