package com.smartlife.pojo.dto;

import com.smartlife.common.constant.RegexPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交订单入参。金额不收——服务端按购物车快照计算，防止篡改价格。
 * 商品来自当前用户购物车，下单成功后清空。
 */
@Data
public class OrdersSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "店铺不能为空")
    @Positive(message = "店铺不能为空")
    private Long shopId;

    @NotBlank(message = "收货人不能为空")
    private String consignee;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = RegexPatterns.PHONE_REGEX, message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "收货地址不能为空")
    private String address;

    private String remark;

    /** 可选：下单要核销的券（我的券包里的券订单 id） */
    @Positive(message = "券不可用")
    private Long voucherOrderId;
}
