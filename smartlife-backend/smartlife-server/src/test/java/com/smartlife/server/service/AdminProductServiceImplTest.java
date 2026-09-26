package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.entity.Voucher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 商品治理测试：违规下架旁路归属生效 + 列表缓存联动清理 */
@SpringBootTest
@Transactional
@DisplayName("商品治理：违规下架/恢复")
class AdminProductServiceImplTest {

    @Autowired
    private IAdminProductService adminProductService;
    @Autowired
    private IDishService dishService;
    @Autowired
    private ISetmealService setmealService;
    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    @Test
    @DisplayName("菜品下架：状态置 0 且店铺列表缓存被清")
    void dishOffShelfEvictsCache() {
        BaseContext.set(new LoginUser(993L, RoleConstants.ADMIN, "治理管理员"));
        Dish dish = new Dish();
        dish.setShopId(1L);
        dish.setName("违规菜品" + System.nanoTime() % 1000);
        dish.setCategoryId(1L);
        dish.setPrice(1000);
        dish.setStatus(StatusConstants.Common.ENABLED);
        dishService.save(dish);
        String cacheKey = RedisConstants.CACHE_DISH_KEY + "1:1";
        redis.opsForValue().set(cacheKey, "stale");

        adminProductService.dishStartStop(dish.getId(), StatusConstants.Common.DISABLED);

        assertEquals(StatusConstants.Common.DISABLED, dishService.getById(dish.getId()).getStatus());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(cacheKey)));
    }

    @Test
    @DisplayName("套餐下架：同构")
    void setmealOffShelfEvictsCache() {
        BaseContext.set(new LoginUser(993L, RoleConstants.ADMIN, "治理管理员"));
        Setmeal setmeal = new Setmeal();
        setmeal.setShopId(1L);
        setmeal.setName("违规套餐" + System.nanoTime() % 1000);
        setmeal.setCategoryId(1L);
        setmeal.setPrice(2000);
        setmeal.setStatus(StatusConstants.Common.ENABLED);
        setmealService.save(setmeal);
        String cacheKey = RedisConstants.CACHE_SETMEAL_KEY + "1:1";
        redis.opsForValue().set(cacheKey, "stale");

        adminProductService.setmealStartStop(setmeal.getId(), StatusConstants.Common.DISABLED);

        assertEquals(StatusConstants.Common.DISABLED, setmealService.getById(setmeal.getId()).getStatus());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(cacheKey)));
    }

    @Test
    @DisplayName("违规券强制下架（管理端旁路，状态域 1/2）")
    void voucherForcedOffShelf() {
        BaseContext.set(new LoginUser(993L, RoleConstants.ADMIN, "治理管理员"));
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("违规券" + System.nanoTime() % 1000);
        voucher.setThreshold(0);
        voucher.setActualValue(500);
        voucher.setType(StatusConstants.VoucherType.NORMAL);
        voucher.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(voucher);
        try {
            adminProductService.voucherStartStop(voucher.getId(), StatusConstants.Voucher.OFF_SHELF);

            assertEquals(StatusConstants.Voucher.OFF_SHELF,
                    voucherService.getById(voucher.getId()).getStatus());

            adminProductService.voucherStartStop(voucher.getId(), StatusConstants.Voucher.ON_SHELF);
            assertEquals(StatusConstants.Voucher.ON_SHELF,
                    voucherService.getById(voucher.getId()).getStatus());
        } finally {
            voucherService.removeById(voucher.getId());
        }
    }
}
