package com.smartlife.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 看板汇总卡片。营业额口径：区间内已完成订单实付（pay_amount）合计，退款单天然排除 */
@Data
@Schema(description = "看板汇总")
public class ReportOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "营业额（分）")
    private Long turnover;

    @Schema(description = "订单总数")
    private Long orderCount;

    @Schema(description = "有效订单数（已完成）")
    private Long validOrderCount;

    @Schema(description = "订单完成率 0~1")
    private Double orderCompletionRate;

    @Schema(description = "新增用户数（仅平台看板，商家端恒 0）")
    private Long userCount;
}
