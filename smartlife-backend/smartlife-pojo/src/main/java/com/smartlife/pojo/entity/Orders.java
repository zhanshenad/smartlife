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
 * 订单主表。状态机：1 待支付 → 2 待接单 → 3 已接单 → 4 派送中 → 5 已完成；6 已取消可从中途任一状态进入。
 * 所有金额字段单位均为分。
 */
@Data
@TableName("tb_orders")
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单号，由 RedisIdWorker 生成（趋势递增的 long）。
     * 唯一索引 uk_number：这是防重复下单的最后一道硬约束，
     * 不管上游漏了什么检查，重复号都插不进来。
     */
    private String number;

    /** 1 待支付 2 待接单 3 已接单 4 派送中 5 已完成 6 已取消，见 StatusConstants.Order */
    private Integer status;

    private Long userId;

    /** 订单归属店铺。券核销时要比对 tb_voucher.shop_id，防 A 店券在 B 店抵扣 */
    private Long shopId;

    /** 下单时间（业务时间，统计报表按它分组） */
    private LocalDateTime orderTime;

    /** 结账时间 */
    private LocalDateTime checkoutTime;

    /** 1 微信 2 支付宝。本项目为模拟支付，仅作字段保留 */
    private Integer payMethod;

    /** 0 未支付 1 已支付 2 退款，见 StatusConstants.Pay */
    private Integer payStatus;

    /** 订单原价（菜品/套餐小计之和），单位分 */
    private Integer amount;

    /** 券抵扣金额，单位分。无券时为 0 */
    private Integer discountAmount;

    /** 实付 = amount - discountAmount，下限 0。单独存一列而不是每次算，便于对账与统计 */
    private Integer payAmount;

    /** 下单核销的券订单 id。取消/拒单时按它退券，不存则此单未用券 */
    private Long voucherOrderId;

    private String remark;

    // ---- 以下为下单时的收货信息快照，刻意冗余，不受用户后续改地址影响 ----

    private String phone;

    private String address;

    private String userName;

    private String consignee;

    private String cancelReason;

    private String rejectionReason;

    private LocalDateTime cancelTime;

    /** 预计送达时间 */
    private LocalDateTime estimatedDeliveryTime;

    /** 1 立即送出 0 选择具体时间 */
    private Integer deliveryStatus;

    /** 实际送达时间 */
    private LocalDateTime deliveryTime;

    /** 打包费，单位分 */
    private Integer packAmount;

    private Integer tablewareNumber;

    /** 1 按餐量提供 0 选择具体数量 */
    private Integer tablewareStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
