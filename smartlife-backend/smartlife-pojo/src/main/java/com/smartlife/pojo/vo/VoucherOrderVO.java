package com.smartlife.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 券包出参：券订单 + 所属券的资料快照（下单核销时要看门槛与抵扣额）。
 */
@Data
public class VoucherOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long voucherId;

    /** 1 未使用 2 已使用 */
    private Integer status;

    /** 领取时间 */
    private LocalDateTime createTime;

    private String title;

    private String subTitle;

    /** 使用门槛（分），0 = 无门槛 */
    private Integer threshold;

    /** 抵扣金额（分） */
    private Integer actualValue;

    /** 0 普通券 1 秒杀券 */
    private Integer type;

    private Long shopId;
}
