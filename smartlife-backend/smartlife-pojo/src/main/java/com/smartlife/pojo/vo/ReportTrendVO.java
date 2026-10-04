package com.smartlife.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 看板按日趋势，平行数组（与 ECharts x/y 轴直接对应） */
@Data
@Schema(description = "按日趋势")
public class ReportTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日期轴 yyyy-MM-dd")
    private List<String> dateList;

    @Schema(description = "每日营业额（分）")
    private List<Long> turnoverList;

    @Schema(description = "每日订单数")
    private List<Long> orderList;

    @Schema(description = "每日新增用户（仅平台看板，商家端为空）")
    private List<Long> userList;
}
