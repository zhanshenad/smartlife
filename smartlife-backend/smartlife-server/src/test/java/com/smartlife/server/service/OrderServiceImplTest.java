package com.smartlife.server.service;

import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.dto.OrdersSubmitDTO;
import com.smartlife.pojo.dto.ShoppingCartDTO;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.vo.OrderSubmitVO;
import com.smartlife.pojo.vo.OrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下单主链与订单状态机。DB 改动由事务回滚；
 * 库存断言在事务内可见（同连接），RedisIdWorker 的自增副作用无害。
 */
@SpringBootTest
@Transactional
@DisplayName("订单：下单事务与状态机")
class OrderServiceImplTest {

    private static final Long USER_ID = 48L;
    private static final Long MERCHANT_ID = 47L;
    private static final Long SHOP_ID = 1L;
    /** 蛋炒饭(id=3, 1200分)；麻婆豆腐(id=6, 1500分, 库存 3) */
    private static final Long RICE = 3L;
    private static final Long TOFU = 6L;

    @Autowired
    private IOrderService orderService;
    @Autowired
    private IShoppingCartService cartService;
    @Autowired
    private IDishService dishService;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    private void loginAsUser() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
    }

    private void loginAsMerchant() {
        BaseContext.set(new LoginUser(MERCHANT_ID, 2, "测试商家"));
    }

    private OrdersSubmitDTO submitDto() {
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setShopId(SHOP_ID);
        dto.setConsignee("张同学");
        dto.setPhone("13800000001");
        dto.setAddress("东北大学南湖校区");
        return dto;
    }

    private void addDish(Long dishId) {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);
        cartService.add(dto);
    }

    @Test
    @DisplayName("下单：金额服务端计算、库存扣减、购物车清空、明细落库")
    void submitCalculatesAndDeducts() {
        loginAsUser();
        addDish(RICE);
        addDish(RICE);
        OrderSubmitVO vo = orderService.submit(submitDto());

        int ricePrice = dishService.getById(RICE).getPrice();
        assertEquals(ricePrice * 2, vo.getAmount());
        assertEquals(vo.getAmount(), vo.getPayAmount());
        assertTrue(cartService.listMine().isEmpty(), "下单后购物车应清空");

        OrderVO detail = orderService.detailMine(vo.getId());
        assertEquals(1, detail.getDetailList().size());
        assertEquals(2, detail.getDetailList().get(0).getNumber());
    }

    @Test
    @DisplayName("库存不足：整个事务回滚，购物车保留")
    void submitRollsBackWhenStockInsufficient() {
        loginAsUser();
        // 麻婆豆腐库存压到 1，加购 2 份
        Dish tofu = dishService.getById(TOFU);
        tofu.setStock(1);
        dishService.updateById(tofu);
        addDish(TOFU);
        addDish(TOFU);

        assertThrows(BusinessException.class, () -> orderService.submit(submitDto()));
        assertEquals(1, dishService.getById(TOFU).getStock(), "库存不足应整体回滚");
        assertEquals(1, cartService.listMine().size(), "回滚后购物车不应被清空");
    }

    @Test
    @DisplayName("跨店校验：下单店铺与商品归属不符时拒绝")
    void submitRejectsCrossShop() {
        loginAsUser();
        addDish(RICE);
        OrdersSubmitDTO dto = submitDto();
        dto.setShopId(2L);
        assertThrows(BusinessException.class, () -> orderService.submit(dto));
    }

    @Test
    @DisplayName("状态机：支付→接单→派送→完成，每步只认前驱状态")
    void happyPathWalksStateMachine() {
        loginAsUser();
        addDish(RICE);
        OrderSubmitVO vo = orderService.submit(submitDto());
        orderService.pay(vo.getId());

        loginAsMerchant();
        orderService.accept(vo.getId());
        orderService.delivery(vo.getId());
        orderService.complete(vo.getId());

        loginAsUser();
        assertEquals(StatusConstants.Order.COMPLETED, orderService.detailMine(vo.getId()).getStatus());
        // 完成后再接单应拒
        loginAsMerchant();
        assertThrows(BusinessException.class, () -> orderService.accept(vo.getId()));
    }

    @Test
    @DisplayName("取消：回补库存并置退款态")
    void cancelRestoresStock() {
        loginAsUser();
        int stockBefore = dishService.getById(RICE).getStock();
        addDish(RICE);
        OrderSubmitVO vo = orderService.submit(submitDto());
        orderService.cancel(vo.getId());

        Orders order = orderService.getById(vo.getId());
        assertEquals(StatusConstants.Order.CANCELLED, order.getStatus());
        assertEquals(StatusConstants.Pay.REFUND, order.getPayStatus());
        assertEquals(stockBefore, dishService.getById(RICE).getStock());
    }

    @Test
    @DisplayName("拒单：回补库存、退款、记录拒单原因")
    void rejectRestoresAndRecords() {
        loginAsUser();
        int stockBefore = dishService.getById(RICE).getStock();
        addDish(RICE);
        OrderSubmitVO vo = orderService.submit(submitDto());
        orderService.pay(vo.getId());

        loginAsMerchant();
        orderService.reject(vo.getId(), "食材售罄");

        Orders order = orderService.getById(vo.getId());
        assertEquals(StatusConstants.Order.CANCELLED, order.getStatus());
        assertEquals("食材售罄", order.getRejectionReason());
        loginAsUser();
        assertEquals(stockBefore, dishService.getById(RICE).getStock());
    }
}
