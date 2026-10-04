package com.smartlife.server.task;

import com.smartlife.common.constant.MQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MQ 队列深度巡检：正常队列超过阈值说明消费跟不上，死信队列只要有消息就说明落库在重试。
 * 只读 + 打日志，不动任何状态——积压是运维信号，不该由应用自动处置。
 */
@Slf4j
@Component
public class MqBacklogMonitor {

    /** 正常队列积压阈值。秒杀消费者是 prefetch=1 的匀速消费，持续积压说明它跟不上了 */
    @Value("${smartlife.mq.backlog-threshold:1000}")
    private long backlogThreshold;

    /** 正常队列：有消费者，深度会自然回落到 0。延迟队列不在此列——它无消费者，深度就是待超时订单数 */
    private static final List<String> NORMAL_QUEUES = List.of(
            MQConstants.SECKILL_ORDER_QUEUE,
            MQConstants.ORDER_TIMEOUT_QUEUE);

    /** 死信队列是链路终点，只要有消息就值得知道 */
    private static final List<String> DEAD_LETTER_QUEUES = List.of(
            MQConstants.SECKILL_DEAD_QUEUE);

    private final RabbitTemplate rabbitTemplate;

    public MqBacklogMonitor(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void checkBacklog() {
        for (String queue : NORMAL_QUEUES) {
            long depth = depthOf(queue);
            if (depth > backlogThreshold) {
                log.error("MQ 队列积压，消费可能跟不上：queue={}, depth={}, threshold={}",
                        queue, depth, backlogThreshold);
            }
        }
        for (String queue : DEAD_LETTER_QUEUES) {
            long depth = depthOf(queue);
            if (depth > 0) {
                log.error("MQ 死信队列有消息，说明有落库在重试或已失败：queue={}, depth={}", queue, depth);
            }
        }
    }

    /** passive 声明只查状态不建队列。队列不存在时会抛异常并关掉 channel，走 RabbitTemplate 每次新开，无副作用 */
    long depthOf(String queue) {
        try {
            Long depth = rabbitTemplate.execute(channel ->
                    (long) channel.queueDeclarePassive(queue).getMessageCount());
            return depth == null ? 0L : depth;
        } catch (Exception e) {
            log.warn("读取队列深度失败：queue={}", queue, e);
            return 0L;
        }
    }
}
