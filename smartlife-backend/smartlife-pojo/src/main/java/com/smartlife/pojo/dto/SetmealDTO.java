package com.smartlife.pojo.dto;

import com.smartlife.pojo.entity.SetmealDish;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 套餐新增/修改入参。关联菜品的 name/price 快照不信任前端，服务端按 dishId 回查回填。
 */
@Data
public class SetmealDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 修改时必传 */
    private Long id;

    @NotBlank(message = "套餐名称不能为空")
    private String name;

    @NotNull(message = "必须选择分类")
    private Long categoryId;

    /** 单位分 */
    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格不能为负")
    private Integer price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    private String image;

    private String description;

    @NotNull(message = "套餐内至少要有一道菜品")
    private List<SetmealDish> setmealDishes;
}
