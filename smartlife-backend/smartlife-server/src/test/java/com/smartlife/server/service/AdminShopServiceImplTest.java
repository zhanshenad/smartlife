package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.entity.Shop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 店铺治理测试：停业整顿删缓存、删除的未完结订单护栏、GEO 摘除。
 * DB 走事务回滚；缓存/GEO 的 Redis 副作用在用例内自清。
 */
@SpringBootTest
@Transactional
@DisplayName("店铺治理：停业/恢复/删除")
class AdminShopServiceImplTest {

    @Autowired
    private IAdminShopService adminShopService;
    @Autowired
    private IShopService shopService;
    @Autowired
    private IOrderService orderService;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    private Shop newShop() {
        Shop shop = new Shop();
        shop.setMerchantId(9_000_000_000L + System.nanoTime() % 100_000_000L);
        shop.setName("治理测试店");
        shop.setTypeId(1L);
        shop.setArea("辽宁省沈阳市");
        shop.setAddress("浑南区创新路195号");
        shop.setX(123.43);
        shop.setY(41.80);
        shop.setStatus(StatusConstants.Common.ENABLED);
        shopService.save(shop);
        return shop;
    }

    private Orders newOrder(Shop shop, int status) {
        Orders order = new Orders();
        order.setNumber("GOV-TEST-" + System.nanoTime());
        order.setStatus(status);
        order.setPayStatus(StatusConstants.Pay.UN_PAID);
        order.setUserId(1L);
        order.setShopId(shop.getId());
        order.setOrderTime(LocalDateTime.now());
        order.setPayMethod(1);
        order.setAmount(0);
        order.setDiscountAmount(0);
        order.setPayAmount(0);
        orderService.save(order);
        return order;
    }

    @Test
    @DisplayName("停业整顿：DB 置 0 且详情缓存被删（Cache Aside）")
    void suspendDeletesShopCache() {
        BaseContext.set(new LoginUser(995L, RoleConstants.ADMIN, "测试管理员"));
        Shop shop = newShop();
        redis.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + shop.getId(), "stale");

        adminShopService.changeStatus(shop.getId(), StatusConstants.Common.DISABLED);

        assertEquals(StatusConstants.Common.DISABLED,
                shopService.getById(shop.getId()).getStatus());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.CACHE_SHOP_KEY + shop.getId())));
    }

    @Test
    @DisplayName("删除护栏：有未完结订单拒绝")
    void removeRejectsWithInFlightOrders() {
        BaseContext.set(new LoginUser(995L, RoleConstants.ADMIN, "测试管理员"));
        Shop shop = newShop();
        newOrder(shop, StatusConstants.Order.DELIVERY_IN_PROGRESS);

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminShopService.remove(shop.getId()));
        assertTrue(e.getMessage().contains("未完结"));
    }

    @Test
    @DisplayName("删除：店与 GEO 成员一并摘除")
    void removeDeletesShopAndGeoMember() {
        BaseContext.set(new LoginUser(995L, RoleConstants.ADMIN, "测试管理员"));
        Shop shop = newShop();
        newOrder(shop, StatusConstants.Order.COMPLETED);
        redis.opsForZSet().add(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(), shop.getId().toString(), 1.0);

        try {
            adminShopService.remove(shop.getId());

            assertNull(shopService.getById(shop.getId()));
            assertNull(redis.opsForZSet().score(
                    RedisConstants.SHOP_GEO_KEY + shop.getTypeId(), shop.getId().toString()));
        } finally {
            // 事务回滚会还回 DB 记录，Redis 成员再兜底摘一次
            redis.opsForZSet().remove(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(),
                    shop.getId().toString());
        }
    }
}
