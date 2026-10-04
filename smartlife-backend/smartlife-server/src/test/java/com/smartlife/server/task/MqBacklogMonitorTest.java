package com.smartlife.server.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smartlife.common.constant.MQConstants;
import com.smartlife.pojo.dto.OrderTimeoutMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 队列深度读取与告警。测试环境的监听器容器不自启（surefire argLine 关了 auto-startup），
 * 队列没有消费者，投进去的消息会稳定留在里面。阈值设为 0，让任意积压都能触发告警分支。
 */
@SpringBootTest(properties = "smartlife.mq.backlog-threshold=0")
@DisplayName("MQ 队列深度巡检")
class MqBacklogMonitorTest {

    private static final String QUEUE = MQConstants.ORDER_TIMEOUT_QUEUE;
    private static final String EXCHANGE = MQConstants.ORDER_DLX_EXCHANGE;
    private static final String ROUTING_KEY = MQConstants.ORDER_TIMEOUT_ROUTING_KEY;

    @Autowired
    private MqBacklogMonitor monitor;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void cleanUp() {
        purge();
    }

    private void purge() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(QUEUE);
            return null;
        });
    }

    @Test
    @DisplayName("能读到真实积压条数，清空后归零")
    void readsQueueDepth() throws InterruptedException {
        purge();
        assertEquals(0L, awaitDepth(0L), "清空后深度应为 0");

        for (int i = 0; i < 3; i++) {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new OrderTimeoutMessage(1L));
        }
        // broker 侧计数是异步更新的，convertAndSend 返回时最后一条可能还没入队，
        // 所以轮询等它涨上来；这也说明监控读到的是滞后一拍的快照，看趋势而非精确值
        assertEquals(3L, awaitDepth(3L), "投 3 条后应读到 3");
    }

    /** 轮询到期望深度或超时，返回最后一次读数 */
    private long awaitDepth(long expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            long depth = monitor.depthOf(QUEUE);
            if (depth == expected) {
                return depth;
            }
            Thread.sleep(100);
        }
        return monitor.depthOf(QUEUE);
    }

    @Test
    @DisplayName("队列不存在时不抛异常，按 0 处理")
    void toleratesMissingQueue() {
        assertEquals(0L, monitor.depthOf("no.such.queue.v1"));
    }

    @Test
    @DisplayName("有积压时打出告警，队列空时不打")
    void alertsOnlyWhenBacklogged() throws InterruptedException {
        Logger logger = (Logger) LoggerFactory.getLogger(MqBacklogMonitor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.addAppender(appender);
        appender.start();
        try {
            purge();
            awaitDepth(0L);
            monitor.checkBacklog();
            assertFalse(loggedBacklog(appender), "队列空时不应告警");

            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new OrderTimeoutMessage(1L));
            awaitDepth(1L);
            monitor.checkBacklog();
            assertTrue(loggedBacklog(appender), "有积压时应当告警");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private boolean loggedBacklog(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("积压"));
    }
}
