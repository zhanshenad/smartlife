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
 * 秒杀券的附加信息，与 Voucher 一对一（主键即 voucher_id）。
 * stock 是权威库存：Redis 里的 seckill:stock:{voucherId} 只是它的加速副本，
 * 消费者落库时会再扣一次 DB 库存并校验影响行数（§5.2.6.1）。
 */
@Data
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 与 tb_voucher.id 同值，由业务指定而非自增 */
    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;

    private Integer stock;

    /** 生效时间，Lua 时间窗校验的上界依据 */
    private LocalDateTime beginTime;

    /** 失效时间 */
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
