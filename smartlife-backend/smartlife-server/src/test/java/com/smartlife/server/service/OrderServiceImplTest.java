package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.RedisIdWorker;
import com.smartlife.pojo.dto.OrdersSubmitDTO;
import com.smartlife.pojo.dto.ShoppingCartDTO;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.pojo.vo.OrderSubmitVO;
import com.smartlife.pojo.vo.OrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private RedisIdWorker idWorker;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.ORDER_SUBMIT_TOKEN_KEY + USER_ID);
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

    // ==================== 券核销（P4-5） ====================

    /** 造一张本店满减券并给当前用户领上，返回券订单 */
    private VoucherOrder claimVoucher(int threshold, int actualValue, Long shopId) {
        Voucher v = new Voucher();
        v.setShopId(shopId);
        v.setTitle("测试券");
        v.setThreshold(threshold);
        v.setActualValue(actualValue);
        v.setType(StatusConstants.VoucherType.NORMAL);
        v.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(v);
        VoucherOrder order = new VoucherOrder();
        order.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
        order.setUserId(USER_ID);
        order.setVoucherId(v.getId());
        voucherOrderService.save(order);
        return order;
    }

    @Test
    @DisplayName("下单核销：折扣金额、实付、券状态与订单关联全部落库")
    void submitRedeemsVoucher() {
        loginAsUser();
        VoucherOrder claim = claimVoucher(1000, 500, SHOP_ID);
        addDish(RICE);
        OrdersSubmitDTO dto = submitDto();
        dto.setVoucherOrderId(claim.getId());
        OrderSubmitVO vo = orderService.submit(dto);

        int amount = dishService.getById(RICE).getPrice();
        assertEquals(amount - 500, vo.getPayAmount(), "实付 = 原价 - 抵扣");
        Orders order = orderService.getById(vo.getId());
        assertEquals(500, order.getDiscountAmount());
        assertEquals(claim.getId(), order.getVoucherOrderId());
        assertEquals(StatusConstants.VoucherOrder.USED,
                voucherOrderService.getById(claim.getId()).getStatus());
    }

    @Test
    @DisplayName("跨店券：拒绝在 B 店核销 A 店的券")
    void submitRejectsCrossShopVoucher() {
        loginAsUser();
        VoucherOrder claim = claimVoucher(0, 500, 999L);
        addDish(RICE);
        OrdersSubmitDTO dto = submitDto();
        dto.setVoucherOrderId(claim.getId());
        BusinessException e = assertThrows(BusinessException.class, () -> orderService.submit(dto));
        assertTrue(e.getMessage().contains("不适用"));
    }

    @Test
    @DisplayName("低于门槛：拒绝核销")
    void submitRejectsBelowThreshold() {
        loginAsUser();
        VoucherOrder claim = claimVoucher(99999, 500, SHOP_ID);
        addDish(RICE);
        OrdersSubmitDTO dto = submitDto();
        dto.setVoucherOrderId(claim.getId());
        BusinessException e = assertThrows(BusinessException.class, () -> orderService.submit(dto));
        assertTrue(e.getMessage().contains("门槛"));
    }

    @Test
    @DisplayName("取消退券：券回到未使用、use_time 清空")
    void cancelRestoresVoucher() {
        loginAsUser();
        VoucherOrder claim = claimVoucher(1000, 500, SHOP_ID);
        addDish(RICE);
        OrdersSubmitDTO dto = submitDto();
        dto.setVoucherOrderId(claim.getId());
        OrderSubmitVO vo = orderService.submit(dto);
        orderService.cancel(vo.getId());

        VoucherOrder restored = voucherOrderService.getById(claim.getId());
        assertEquals(StatusConstants.VoucherOrder.UNUSED, restored.getStatus());
        assertEquals(null, restored.getUseTime());
    }

    @Test
    @DisplayName("超时取消：待支付订单自动取消并退券；已支付订单跳过")
    void timeoutCancelBehaves() {
        loginAsUser();
        VoucherOrder claim = claimVoucher(1000, 500, SHOP_ID);
        addDish(RICE);
        OrdersSubmitDTO dto = submitDto();
        dto.setVoucherOrderId(claim.getId());
        OrderSubmitVO vo = orderService.submit(dto);

        orderService.timeoutCancel(vo.getId());
        assertEquals(StatusConstants.Order.CANCELLED, orderService.getById(vo.getId()).getStatus());
        assertEquals(StatusConstants.VoucherOrder.UNUSED,
                voucherOrderService.getById(claim.getId()).getStatus());

        // 已支付订单再超时取消：静默跳过。
        // 两次 submit 模拟两次独立的用户操作，中间清掉防重复提交 token——
        // 真实场景下两次下单相隔远不止 5 秒，不清理会被自己的防抖逻辑挡住
        redis.delete(RedisConstants.ORDER_SUBMIT_TOKEN_KEY + USER_ID);
        addDish(RICE);
        OrderSubmitVO vo2 = orderService.submit(dto);
        orderService.pay(vo2.getId());
        orderService.timeoutCancel(vo2.getId());
        assertEquals(StatusConstants.Order.TO_BE_CONFIRMED, orderService.getById(vo2.getId()).getStatus());
    }

    @Test
    @DisplayName("防重复提交：TTL 窗口内第二次提交被拒")
    void submitRejectsDuplicateWithinWindow() {
        loginAsUser();
        addDish(RICE);
        assertNotNull(orderService.submit(submitDto()));

        addDish(RICE);
        BusinessException e = assertThrows(BusinessException.class, () -> orderService.submit(submitDto()));
        assertTrue(e.getMessage().contains("请勿重复提交"));
    }

    /** 用 NOT_SUPPORTED 让 submit 走自己的事务，否则回滚钩子要等测试结束才触发、断言不到释放 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("防重复提交：业务失败释放 token，用户可立刻重试")
    void submitReleasesTokenOnFailure() {
        loginAsUser();
        BusinessException first = assertThrows(BusinessException.class, () -> orderService.submit(submitDto()));
        assertTrue(first.getMessage().contains("购物车为空"));

        // token 已随事务回滚释放：第二次仍是"购物车为空"，而不是被防抖挡成"请勿重复提交"
        BusinessException second = assertThrows(BusinessException.class, () -> orderService.submit(submitDto()));
        assertTrue(second.getMessage().contains("购物车为空"),
                "业务失败后 token 应被释放，用户不必等 5 秒 TTL");
    }
}
