package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单明细：订单里包含的每一道菜 / 每一个套餐。
 * name / image / amount 都是下单时刻的快照——
 * 菜品之后改名涨价，历史订单展示的仍是当时的样子。
 */
@Data
@TableName("tb_order_detail")
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 菜品快照 */
    private String name;

    private String image;

    /** 二选一：菜品 id 或套餐 id */
    private Long dishId;

    private Long setmealId;

    /** 口味，如"微辣,不要香菜" */
    private String dishFlavor;

    private Integer number;

    /** 该明细小计金额，单位分 */
    private Integer amount;
}
