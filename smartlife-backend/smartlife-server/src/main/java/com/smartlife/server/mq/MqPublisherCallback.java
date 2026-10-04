package com.smartlife.server.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产者确认的全局回调，只做日志留痕；补偿在请求线程里当场做，不在这里。
 */
@Component
public class MqPublisherCallback implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private static final Logger log = LoggerFactory.getLogger(MqPublisherCallback.class);

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.debug("broker 已确认消息：id={}", correlationData == null ? "null" : correlationData.getId());
        } else {
            log.error("broker 拒收消息（消息未入队）：id={}, cause={}",
                    correlationData == null ? "null" : correlationData.getId(), cause);
        }
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        log.error("消息无法路由到任何队列，拓扑可能被改坏：exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText());
    }
}
