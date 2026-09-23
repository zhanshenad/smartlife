package com.smartlife.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 加购/减购入参：菜品与套餐二选一（注解表达不了"二选一"约束，服务层校验），每次数量变动 1。
 * name/image/amount 快照不收，服务端按 id 回查商品表。
 */
@Data
public class ShoppingCartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long dishId;

    private Long setmealId;

    private String dishFlavor;
}
