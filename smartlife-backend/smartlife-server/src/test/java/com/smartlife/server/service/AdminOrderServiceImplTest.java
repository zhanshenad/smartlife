package com.smartlife.server.service;

import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.vo.OrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单巡检测试：管理端旁路归属 + 代处置状态机。
 * 订单数据挂在真实测试店（shopId=1）下，事务回滚自清。
 */
@SpringBootTest
@Transactional
@DisplayName("订单巡检：分页/代接单/代完成/代取消")
class AdminOrderServiceImplTest {

    @Autowired
    private IOrderService orderService;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    private Orders newOrder(int status) {
        Orders order = new Orders();
        order.setNumber("ADM-TEST-" + System.nanoTime());
        order.setStatus(status);
        order.setPayStatus(StatusConstants.Pay.PAID);
        order.setUserId(48L);
        order.setShopId(1L);
        order.setOrderTime(LocalDateTime.now());
        order.setPayMethod(1);
        order.setAmount(1000);
        order.setDiscountAmount(0);
        order.setPayAmount(1000);
        orderService.save(order);
        return order;
    }

    private void loginAsAdmin() {
        BaseContext.set(new LoginUser(994L, RoleConstants.ADMIN, "巡检管理员"));
    }

    @Test
    @DisplayName("全平台分页：按状态过滤能命中任意用户的订单")
    void pageAllFiltersByStatus() {
        Orders order = newOrder(StatusConstants.Order.TO_BE_CONFIRMED);

        PageResult<OrderVO> page = orderService.pageAll(StatusConstants.Order.TO_BE_CONFIRMED,
                null, 1, 10);
        assertTrue(page.getRecords().stream().anyMatch(vo -> order.getId().equals(vo.getId())));
        assertTrue(page.getRecords().stream().allMatch(vo -> vo.getShopName() != null),
                "巡检列表应带店铺名");
    }

    @Test
    @DisplayName("代接单：待接单 → 已接单")
    void adminAcceptMovesForward() {
        loginAsAdmin();
        Orders order = newOrder(StatusConstants.Order.TO_BE_CONFIRMED);

        orderService.adminAccept(order.getId());

        assertEquals(StatusConstants.Order.CONFIRMED,
                orderService.getById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("代完成：仅派送中可以，已接单拒绝")
    void adminCompleteGuardsStatus() {
        loginAsAdmin();
        Orders order = newOrder(StatusConstants.Order.CONFIRMED);

        BusinessException e = assertThrows(BusinessException.class,
                () -> orderService.adminComplete(order.getId()));
        assertTrue(e.getMessage().contains("派送中"));

        orderService.adminCancel(order.getId(), null);
    }

    @Test
    @DisplayName("代取消：已支付订单 → 取消 + 退款态 + 审计原因落库")
    void adminCancelRefunds() {
        loginAsAdmin();
        Orders order = newOrder(StatusConstants.Order.CONFIRMED);

        orderService.adminCancel(order.getId(), "商家失联");

        Orders after = orderService.getById(order.getId());
        assertEquals(StatusConstants.Order.CANCELLED, after.getStatus());
        assertEquals(StatusConstants.Pay.REFUND, after.getPayStatus());
        assertTrue(after.getCancelReason().contains("管理员代取消"));
        assertTrue(after.getCancelReason().contains("商家失联"));
    }

    @Test
    @DisplayName("代取消护栏：待支付与已完成都拒绝")
    void adminCancelGuards() {
        loginAsAdmin();
        Orders pending = newOrder(StatusConstants.Order.PENDING_PAYMENT);
        Orders done = newOrder(StatusConstants.Order.COMPLETED);

        assertThrows(BusinessException.class, () -> orderService.adminCancel(pending.getId(), null));
        assertThrows(BusinessException.class, () -> orderService.adminCancel(done.getId(), null));
    }
}
