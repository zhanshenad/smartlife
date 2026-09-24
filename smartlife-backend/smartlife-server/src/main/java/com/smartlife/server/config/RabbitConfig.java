package com.smartlife.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartlife.common.constant.MQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（§5.2.6）。队列名带版本后缀：改 TTL/死信参数须同步 +1 版本号，
 * 否则对已存在队列启动报 PRECONDITION_FAILED。
 */
@Configuration
public class RabbitConfig {

    /** 订单超时时长（分钟）。改它必须同步换延迟队列版本号，原因见类注释 */
    @Value("${smartlife.order.timeout-minutes:15}")
    private long orderTimeoutMinutes;

    /** 统一 JSON 序列化，LocalDateTime 需要 JavaTimeModule 才不炸 */
    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    // ==================== 秒杀订单：正常队列 + 死信队列 ====================

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MQConstants.SECKILL_EXCHANGE);
    }

    /** 消费异常 nack(requeue=false) 时消息转投死信交换机 */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(MQConstants.SECKILL_ORDER_QUEUE)
                .deadLetterExchange(MQConstants.SECKILL_DEAD_EXCHANGE)
                .deadLetterRoutingKey(MQConstants.SECKILL_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillExchange()).with(MQConstants.SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillDeadExchange() {
        return new DirectExchange(MQConstants.SECKILL_DEAD_EXCHANGE);
    }

    /** 死信队列是链路终点，只做"重试一次落库"（§5.2.6.2） */
    @Bean
    public Queue seckillDeadQueue() {
        return QueueBuilder.durable(MQConstants.SECKILL_DEAD_QUEUE).build();
    }

    @Bean
    public Binding seckillDeadBinding() {
        return BindingBuilder.bind(seckillDeadQueue())
                .to(seckillDeadExchange()).with(MQConstants.SECKILL_DEAD_ROUTING_KEY);
    }

    // ==================== 订单超时：延迟队列（TTL + DLX） ====================

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(MQConstants.ORDER_EXCHANGE);
    }

    /**
     * 延迟队列：不挂消费者，消息 TTL 到期后经死信交换机转到超时队列。
     * 用队列级 TTL：按队头顺序过期；所有订单超时时长一致，不存在队头阻塞。
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(MQConstants.ORDER_DELAY_QUEUE)
                .ttl((int) (orderTimeoutMinutes * 60 * 1000))
                .deadLetterExchange(MQConstants.ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(MQConstants.ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange()).with(MQConstants.ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(MQConstants.ORDER_DLX_EXCHANGE);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(MQConstants.ORDER_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderDlxExchange()).with(MQConstants.ORDER_TIMEOUT_ROUTING_KEY);
    }
}
