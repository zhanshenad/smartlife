package com.smartlife.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/** 店铺类型入参。id 仅修改时必填 */
@Data
public class ShopTypeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Positive
    private Long id;

    @NotBlank(message = "类型名称不能为空")
    private String name;

    private String icon;

    private Integer sort;
}
