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
 * 优惠券（普通券与秒杀券共用本表，靠 type 区分）。
 * 本项目是「领券模型」不是「买券模型」（§5.2.2）：
 * 黑马点评原表的 pay_value（买券花的钱）已删除，改为 threshold（使用门槛）。
 * 秒杀券的时间窗与库存在 SeckillVoucher 里，本表只放两种券共有的属性。
 */
@Data
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属店铺。
     * 必须有：下单核销时要与 tb_orders.shop_id 比对，否则 A 店领的券能在 B 店抵扣。
     */
    private Long shopId;

    private String title;

    private String subTitle;

    private String rules;

    /** 使用门槛：订单满多少分可用，0 表示无门槛（§5.2.2 新增字段） */
    private Integer threshold;

    /** 抵扣金额，单位分 */
    private Integer actualValue;

    /**
     * 0 普通券 1 秒杀券，见 StatusConstants.VoucherType。
     * 只用来对齐黑马点评原表。判断"是不是秒杀券"要看有没有配套的 tb_seckill_voucher 记录（§5.2.1），
     * 不要 if (type == 1)。
     */
    private Integer type;

    /** 1 上架 2 下架 3 过期，见 StatusConstants.Voucher */
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
