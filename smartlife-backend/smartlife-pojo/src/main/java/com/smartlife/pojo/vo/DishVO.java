package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.DishFlavor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 菜品出参：列表带分类名，详情带口味。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DishVO extends Dish {

    private List<DishFlavor> flavors;

    private String categoryName;
}
