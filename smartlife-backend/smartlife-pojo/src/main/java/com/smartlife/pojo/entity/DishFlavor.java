package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜品口味，如「辣度 → ["不辣","微辣","中辣","重辣"]」。
 * value 存 JSON 数组字符串，前端直接反序列化成选项列表。
 * 没有做额外的口味表——选项是自由文本，不需要独立主数据。
 */
@Data
@TableName("tb_dish_flavor")
public class DishFlavor implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long dishId;

    /** 口味名称，如"辣度" */
    private String name;

    /** 口味选项，JSON 数组字符串 */
    private String value;
}
