package com.smartlife.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 热销商品 Top N 条目（按已完成订单明细销量） */
@Data
@Schema(description = "热销条目")
public class ReportTopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "商品名（下单时快照）")
    private String name;

    @Schema(description = "销量（份）")
    private Long count;
}
