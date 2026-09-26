package com.smartlife.pojo.dto;

import com.smartlife.common.constant.RegexPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/** 提交入驻申请入参 */
@Data
public class MerchantApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "店铺名称不能为空")
    private String shopName;

    @NotNull(message = "店铺类型不能为空")
    private Long shopTypeId;

    @NotBlank(message = "所在区域不能为空")
    private String area;

    @NotBlank(message = "详细地址不能为空")
    private String address;

    /** 经度。tb_shop.x 非空，无坐标无法建店，也进不了"附近店铺" */
    @NotNull(message = "经纬度不能为空")
    private Double x;

    @NotNull(message = "经纬度不能为空")
    private Double y;

    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @Pattern(regexp = RegexPatterns.PHONE_REGEX, message = "联系电话格式不正确")
    private String contactPhone;

    /** 营业执照等资质图，多张逗号分隔 */
    private String licenseImages;
}
