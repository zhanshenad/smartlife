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
 * 菜品。每家店铺各自维护自己的菜品，用 shopId 隔离。
 */
@Data
@TableName("tb_dish")
public class Dish implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属店铺，商家端所有写操作都要校验这个字段 */
    private Long shopId;

    private String name;

    /** 关联 tb_category.id（type=1 的菜品分类） */
    private Long categoryId;

    /**  单位分，不用 DECIMAL/FLOAT，避免浮点误差（§6.2 第 3 条） */
    private Integer price;

    private String image;

    private String description;

    /** 0 停售 1 起售 */
    private Integer status;

    /** 库存，下单时 UPDATE ... WHERE stock >= n 原子扣减 */
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
