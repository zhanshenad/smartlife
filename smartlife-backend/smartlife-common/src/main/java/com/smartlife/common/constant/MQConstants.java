package com.smartlife.common.constant;

/**
 * RabbitMQ 交换机 / 队列 / 路由键。拓扑见《重构计划》§5.2.6。
 * 队列名带版本后缀：RabbitMQ 不允许改已存在队列的参数，改配置会报 PRECONDITION_FAILED，
 * 带后缀就能换个队列名了事，不用去管理台手删。
 */
public class MQConstants {

    private MQConstants() {
    }

    // ==================== 秒杀订单 ====================

    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue.v1";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    /** 死信交换机 / 队列：消费者 nack(requeue=false) 后落到这里，再重试一次落库 */
    public static final String SECKILL_DEAD_EXCHANGE = "seckill.dead.exchange";
    public static final String SECKILL_DEAD_QUEUE = "seckill.dead.queue.v1";
    public static final String SECKILL_DEAD_ROUTING_KEY = "seckill.dead";

    // ==================== 订单超时取消 ====================

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue.v1";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    /** 延迟队列无消费者，消息到期后经此交换机转发到超时队列 */
    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue.v1";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";
}
