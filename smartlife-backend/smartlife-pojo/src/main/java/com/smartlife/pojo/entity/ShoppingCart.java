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
 * 购物车。纯 MySQL，不加缓存（理由见《重构计划》§6.2 第 7 条）。
 * 加购必须用 DB 端原子自增（UPDATE ... SET number = number + 1），
 * 不要"查出来 +1 再写回"——并发会丢更新。
 */
@Data
@TableName("tb_shopping_cart")
public class ShoppingCart implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String image;

    /** 二选一 */
    private Long dishId;

    private Long setmealId;

    private String dishFlavor;

    private Integer number;

    /** 单价，单位分 */
    private Integer amount;

    /**
     * 必须带 fill 注解。
     * MyMetaObjectHandler 用的是 strictInsertFill，它会按字段上的
     * FieldFill 配置过滤——没标注解的字段不会被填充，
     * 表现为 create_time 静默地永远是 NULL，不报任何错。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
