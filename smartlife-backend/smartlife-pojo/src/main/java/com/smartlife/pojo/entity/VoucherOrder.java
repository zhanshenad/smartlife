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
 * 券订单 —— 语义是「用户领到的券」，不是支付单（§5.2.2 领券模型）。
 * 状态只有两个：1 未使用 / 2 已使用，见 StatusConstants.VoucherOrder。
 */
@Data
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键由 RedisIdWorker 生成后显式赋值，故为 INPUT 而非自增。
     * 秒杀链路上这个 id 在 Lua 执行之前就要生成：先拿到 orderId 才能把它作为
     * 消息内容发给 MQ，消费者落库时直接用它当主键（这样"消息里的 id"和"库里的 id"天然一致）。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long voucherId;

    /** 1 未使用 2 已使用 */
    private Integer status;

    /** 领取时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 核销时间 */
    private LocalDateTime useTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
