package com.smartlife.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀订单 MQ 消息体：Lua 预检通过后发出，消费者据此异步落库。
 * orderId 在发消息前就已生成，保证"消息里的 id"与"库里的主键"一致（消费端幂等判重依据）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long voucherId;

    private Long orderId;
}
