package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 套餐-菜品关联。
 * name / price 是冗余快照：套餐卖出后菜品可能改名改价，
 * 但历史订单的构成不该跟着变。查套餐详情时直接用这里的冗余值，不必回表。
 */
@Data
@TableName("tb_setmeal_dish")
public class SetmealDish implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long setmealId;

    private Long dishId;

    /** 冗余：菜品名称快照 */
    private String name;

    /** 冗余：菜品单价快照，单位分 */
    private Integer price;

    /** 份数 */
    private Integer copies;
}
