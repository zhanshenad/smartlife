package com.smartlife.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 下单成功出参。
 */
@Data
public class OrderSubmitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String number;

    /** 订单原价，单位分 */
    private Integer amount;

    /** 实付，单位分（无券时等于 amount） */
    private Integer payAmount;
}
