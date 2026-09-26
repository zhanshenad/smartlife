package com.smartlife.server.service;

import com.smartlife.pojo.vo.ReportOverviewVO;
import com.smartlife.pojo.vo.ReportTopVO;
import com.smartlife.pojo.vo.ReportTrendVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 经营看板：平台与商家同一套口径，shopId 为 null 即平台大盘。
 * 营业额 = 已完成订单实付合计（§十六 商家看板决策）。
 */
public interface IReportService {

    ReportOverviewVO overview(LocalDate begin, LocalDate end, Long shopId);

    ReportTrendVO trend(LocalDate begin, LocalDate end, Long shopId);

    List<ReportTopVO> top10(LocalDate begin, LocalDate end, Long shopId);
}
