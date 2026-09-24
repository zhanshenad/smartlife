package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.Voucher;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户端券视图：秒杀券附库存与时间窗，普通券这三个字段为 null。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VoucherVO extends Voucher {

    private Integer stock;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
