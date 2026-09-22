package com.smartlife.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 商家端更新店铺的入参。只放可改字段，merchantId / sold / status 不在此列，
 * 从根上杜绝越权改归属和销量的可能。
 */
@Data
public class ShopDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "店铺id不能为空")
    private Long id;

    @NotBlank(message = "店铺名称不能为空")
    private String name;

    @NotNull(message = "店铺类型不能为空")
    private Long typeId;

    private String images;

    private String area;

    @NotBlank(message = "店铺地址不能为空")
    private String address;

    private Double x;

    private Double y;

    /** 人均消费，单位分 */
    private Integer avgPrice;

    private String openHours;
}
