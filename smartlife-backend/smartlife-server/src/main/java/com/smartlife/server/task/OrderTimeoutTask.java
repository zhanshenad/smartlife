package com.smartlife.server.task;

import com.smartlife.common.constant.StatusConstants;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.server.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单兜底扫描（§4.4 #36）：延迟消息丢失、消费失败或服务重启窗口期由它补上。
 * 与延迟队列的并发取消靠 CAS 状态流转保证只有一个赢家，扫到已流转的会静默跳过。
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    private final IOrderService orderService;

    /** 与延迟队列 TTL 同源，两边口径永远一致 */
    @Value("${smartlife.order.timeout-minutes:15}")
    private long timeoutMinutes;

    public OrderTimeoutTask(IOrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Orders> stale = orderService.lambdaQuery()
                .eq(Orders::getStatus, StatusConstants.Order.PENDING_PAYMENT)
                .lt(Orders::getOrderTime, deadline)
                .list();
        if (stale.isEmpty()) {
            return;
        }
        log.info("兜底扫描到 {} 笔超时未支付订单，开始取消", stale.size());
        for (Orders order : stale) {
            try {
                orderService.timeoutCancel(order.getId());
            } catch (Exception e) {
                log.error("兜底超时取消失败：orderId={}", order.getId(), e);
            }
        }
    }
}
