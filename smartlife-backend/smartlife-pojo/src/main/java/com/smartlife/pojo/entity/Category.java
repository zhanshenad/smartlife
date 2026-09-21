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
 * 菜品 / 套餐分类。
 * 平台级字典，不是店铺私有：由管理端统一维护（《重构计划》§4.3 第 30 条"类目字典"），
 * 各商家在发布商品时选用。这样分类口径全平台一致，看板统计才有可比性。
 */
@Data
@TableName("tb_category")
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 1 菜品分类 2 套餐分类 */
    private Integer type;

    private String name;

    private Integer sort;

    /** 0 禁用 1 启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createUser;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;
}
