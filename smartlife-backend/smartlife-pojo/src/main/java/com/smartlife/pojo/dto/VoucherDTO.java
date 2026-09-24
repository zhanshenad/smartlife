package com.smartlife.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家发券入参：type=0 普通券只填公共字段；type=1 秒杀券须再填 stock/beginTime/endTime
 * （注解表达不了"type=1 才必填"，服务层校验）。shopId 不收，服务端按登录商家取本店。
 */
@Data
public class VoucherDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "券标题不能为空")
    private String title;

    private String subTitle;

    private String rules;

    /** 使用门槛（分），不填视为无门槛 */
    @PositiveOrZero(message = "使用门槛不能为负数")
    private Integer threshold;

    @NotNull(message = "抵扣金额不能为空")
    @Positive(message = "抵扣金额必须大于 0")
    private Integer actualValue;

    @NotNull(message = "券类型不能为空")
    @Min(value = 0, message = "券类型不合法")
    @Max(value = 1, message = "券类型不合法")
    private Integer type;

    // ==================== 以下仅秒杀券填写 ====================

    @Positive(message = "库存必须大于 0")
    private Integer stock;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
