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
 * 店铺。merchant_id 唯一决定归属，经纬度供 GEO 附近搜索。
 */
@Data
@TableName("tb_shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 店主（role=2 的用户）。
     * 唯一索引 uk_merchant_id：一个商家只能有一家店，
     * 这是商家端"只能操作自己的店"做归属校验的基础。
     */
    private Long merchantId;

    private String name;

    /** 关联 tb_shop_type.id */
    private Long typeId;

    /** 多张图以英文逗号分隔 */
    private String images;

    /** 商圈，如"陆家嘴" */
    private String area;

    private String address;

    /** 经度 */
    private Double x;

    /** 纬度 */
    private Double y;

    /** 人均消费，单位分 */
    private Integer avgPrice;

    private Integer sold;

    private Integer comments;

    /** 评分，乘 10 保存避免小数，如 47 表示 4.7 分 */
    private Integer score;

    /** 营业时间，如 "10:00-22:00" */
    private String openHours;

    /** 0 停业 1 营业，见 StatusConstants.Common */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
