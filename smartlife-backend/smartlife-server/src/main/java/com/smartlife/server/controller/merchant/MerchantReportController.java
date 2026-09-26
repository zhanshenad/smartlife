package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.ReportOverviewVO;
import com.smartlife.pojo.vo.ReportTopVO;
import com.smartlife.pojo.vo.ReportTrendVO;
import com.smartlife.server.service.IReportService;
import com.smartlife.server.service.IShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 商家经营看板：与平台看板同一套口径，仅多一层本店过滤 */
@RestController
@RequestMapping("/merchant/report")
@Validated
@Tag(name = "商家端-经营看板")
public class MerchantReportController {

    private final IReportService reportService;
    private final IShopService shopService;

    public MerchantReportController(IReportService reportService, IShopService shopService) {
        this.reportService = reportService;
        this.shopService = shopService;
    }

    @Operation(summary = "本店汇总卡片（新增用户为平台口径，本店恒 0）")
    @GetMapping("/overview")
    public Result<ReportOverviewVO> overview(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(reportService.overview(begin, end, shopService.requireMyShopId()));
    }

    @Operation(summary = "本店按日趋势")
    @GetMapping("/trend")
    public Result<ReportTrendVO> trend(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(reportService.trend(begin, end, shopService.requireMyShopId()));
    }

    @Operation(summary = "本店热销 Top10")
    @GetMapping("/top10")
    public Result<List<ReportTopVO>> top10(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(reportService.top10(begin, end, shopService.requireMyShopId()));
    }
}
