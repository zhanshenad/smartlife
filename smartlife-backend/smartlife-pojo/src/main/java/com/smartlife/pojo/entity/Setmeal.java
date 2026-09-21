package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 套餐（多个菜品的组合）。与 Dish 同构，价格独立定价。
 */
@Data
@TableName("tb_setmeal")
public class Setmeal implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    /** 关联 tb_category.id（type=2 的套餐分类） */
    private Long categoryId;

    private String name;

    /**  单位分 */
    private Integer price;

    /** 0 停售 1 起售 */
    private Integer status;

    private String description;

    private String image;

    /** 库存，语义同 Dish#getStock() */
    private Integer stock;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createUser;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;
}
