package com.smartlife.server.controller.admin;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.ReportOverviewVO;
import com.smartlife.pojo.vo.ReportTopVO;
import com.smartlife.pojo.vo.ReportTrendVO;
import com.smartlife.server.service.IReportService;
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

/** 平台看板：全大盘口径（shopId 传 null） */
@RestController
@RequestMapping("/admin/report")
@Validated
@Tag(name = "管理端-平台看板")
public class AdminReportController {

    private final IReportService reportService;

    public AdminReportController(IReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "汇总卡片（营业额/订单/完成率/新增用户）")
    @GetMapping("/overview")
    public Result<ReportOverviewVO> overview(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(reportService.overview(begin, end, null));
    }

    @Operation(summary = "按日趋势（日期轴补零，直接喂 ECharts）")
    @GetMapping("/trend")
    public Result<ReportTrendVO> trend(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(reportService.trend(begin, end, null));
    }

    @Operation(summary = "热销 Top10（按已完成订单明细销量）")
    @GetMapping("/top10")
    public Result<List<ReportTopVO>> top10(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(reportService.top10(begin, end, null));
    }
}
