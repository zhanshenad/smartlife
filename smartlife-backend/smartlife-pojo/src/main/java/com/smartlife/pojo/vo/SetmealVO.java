package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.entity.SetmealDish;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 套餐出参：列表带分类名，详情带关联菜品。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SetmealVO extends Setmeal {

    private List<SetmealDish> setmealDishes;

    private String categoryName;
}
