package com.smartlife.server.mq;

import com.rabbitmq.client.Channel;
import com.smartlife.common.constant.MQConstants;
import com.smartlife.pojo.dto.OrderTimeoutMessage;
import com.smartlife.server.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时消费者：延迟队列到期消息经死信交换机转到这里。
 * timeoutCancel 对已支付/已取消静默跳过（正常路径），失败消息 nack 丢弃——
 * 超时队列不配死信，漏掉的由 OrderTimeoutTask 扫表兜底。
 */
@Slf4j
@Component
public class OrderTimeoutListener {

    private final IOrderService orderService;

    public OrderTimeoutListener(IOrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = MQConstants.ORDER_TIMEOUT_QUEUE)
    public void onTimeout(OrderTimeoutMessage msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            orderService.timeoutCancel(msg.getOrderId());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("订单超时取消失败，消息丢弃，等兜底任务补扫：orderId={}", msg.getOrderId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
