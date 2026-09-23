package com.smartlife.pojo.dto;

import com.smartlife.pojo.entity.DishFlavor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 菜品新增/修改入参。shopId 不收——服务端从登录商家定位，杜绝越权；
 * status 不收——新增默认停售，起售走独立接口。
 */
@Data
public class DishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 修改时必传，新增不传 */
    private Long id;

    @NotBlank(message = "菜品名称不能为空")
    private String name;

    @NotNull(message = "必须选择分类")
    private Long categoryId;

    /** 单位分 */
    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格不能为负")
    private Integer price;

    private String image;

    private String description;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    private List<DishFlavor> flavors;
}
