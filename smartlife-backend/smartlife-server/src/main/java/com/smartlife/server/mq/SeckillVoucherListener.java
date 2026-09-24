package com.smartlife.server.mq;

import com.rabbitmq.client.Channel;
import com.smartlife.common.constant.MQConstants;
import com.smartlife.pojo.dto.SeckillMessage;
import com.smartlife.server.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀订单消费者：手动 ACK。成功 basicAck；异常 basicNack(requeue=false) 转死信，
 * 防止毒消息无限重投（§5.2.6 设计点 2）。
 * 死信消费者复用同一套落库逻辑 = "重试一次"（§5.2.6.2，与课程版行为一致，不做回补）。
 */
@Slf4j
@Component
public class SeckillVoucherListener {

    private final IVoucherOrderService voucherOrderService;

    public SeckillVoucherListener(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @RabbitListener(queues = MQConstants.SECKILL_ORDER_QUEUE)
    public void handleOrder(SeckillMessage msg, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            voucherOrderService.handleSeckillMessage(msg);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("秒杀订单消费失败，转入死信：{}", msg, e);
            channel.basicNack(tag, false, false);
        }
    }

    /** 死信是链路终点：再失败就丢弃并留 error 痕迹（§5.2.6.2 缝隙 5，本期不兜底） */
    @RabbitListener(queues = MQConstants.SECKILL_DEAD_QUEUE)
    public void handleDead(SeckillMessage msg, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            voucherOrderService.handleSeckillMessage(msg);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("死信重试落库仍失败，消息丢弃：{}", msg, e);
            channel.basicNack(tag, false, false);
        }
    }
}
