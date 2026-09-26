package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.dto.SetmealDTO;
import com.smartlife.pojo.dto.VoucherDTO;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.SetmealDish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6 收尾回归：锁住 v6/v7 的规则外决策（套餐快照防篡改、新增默认停售、
 * 起售校验、删除护栏、秒杀建券 afterCommit 预热），防后续改动悄悄退化。
 */
@SpringBootTest
@DisplayName("回归：商品/套餐/发券的关键决策")
class ProductRegressionTest {

    private static final Long MERCHANT_ID = 47L;

    @Autowired
    private IDishService dishService;
    @Autowired
    private ISetmealService setmealService;
    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private StringRedisTemplate redis;

    private Dish dish;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        if (dish != null && dish.getId() != null) {
            dishService.removeById(dish.getId());
            dish = null;
        }
    }

    private void loginAsMerchant() {
        BaseContext.set(new LoginUser(MERCHANT_ID, RoleConstants.MERCHANT, "测试商家"));
    }

    /** 造一个本店（店1）菜品，默认可指定状态 */
    private Dish newDish(int status) {
        Dish d = new Dish();
        d.setShopId(1L);
        d.setName("回归菜品" + System.nanoTime() % 1000);
        d.setCategoryId(1L);
        d.setPrice(1200);
        d.setStatus(status);
        dishService.save(d);
        dish = d;
        return d;
    }

    @Test
    @Transactional
    @DisplayName("套餐快照防篡改：前端伪造价 1 落库为真实价")
    void setmealSnapshotTrustedServerSide() {
        loginAsMerchant();
        Dish d = newDish(StatusConstants.Common.ENABLED);
        SetmealDTO dto = new SetmealDTO();
        dto.setName("回归套餐");
        dto.setCategoryId(1L);
        dto.setPrice(3000);
        SetmealDish sd = new SetmealDish();
        sd.setDishId(d.getId());
        sd.setCopies(1);
        sd.setName("伪造名字");
        sd.setPrice(1);
        dto.setSetmealDishes(List.of(sd));

        setmealService.saveWithDish(dto);

        var saved = setmealService.lambdaQuery()
                .eq(com.smartlife.pojo.entity.Setmeal::getName, "回归套餐").one();
        try {
            // 从 DB 重读关联，断言快照被服务端回填
            List<SetmealDish> sds = setmealService.getByIdWithDish(saved.getId()).getSetmealDishes();
            assertEquals(d.getName(), sds.get(0).getName(), "名称应为服务端回查值");
            assertEquals(1200, sds.get(0).getPrice(), "价格应为商品表现价，不是前端传的 1");
        } finally {
            setmealService.removeById(saved.getId());
        }
    }

    @Test
    @Transactional
    @DisplayName("套餐新增默认停售；关联菜品停售后套餐禁起售")
    void newProductsDefaultOffShelf() {
        loginAsMerchant();
        Dish d = newDish(StatusConstants.Common.ENABLED);

        // 套餐新增默认停售（v6 决策 7；菜品 saveWithFlavor 同款默认值）
        SetmealDTO dto = new SetmealDTO();
        dto.setName("停售校验套餐");
        dto.setCategoryId(1L);
        dto.setPrice(2000);
        SetmealDish sd = new SetmealDish();
        sd.setDishId(d.getId());
        sd.setCopies(1);
        dto.setSetmealDishes(List.of(sd));
        setmealService.saveWithDish(dto);
        var setmeal = setmealService.lambdaQuery()
                .eq(com.smartlife.pojo.entity.Setmeal::getName, "停售校验套餐").one();
        try {
            assertEquals(StatusConstants.Common.DISABLED, setmeal.getStatus(),
                    "套餐新增默认停售（v6 决策 7）");

            // 套餐关联的菜品停售后，套餐禁起售（v6 决策 8）
            dishService.startStop(d.getId(), StatusConstants.Common.DISABLED);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> setmealService.startStop(setmeal.getId(), StatusConstants.Common.ENABLED));
            assertTrue(e.getMessage().contains("停售菜品"));
        } finally {
            setmealService.removeById(setmeal.getId());
        }
    }

    @Test
    @Transactional
    @DisplayName("删除护栏：起售中的菜品不能删")
    void dishDeleteGuard() {
        loginAsMerchant();
        Dish d = newDish(StatusConstants.Common.ENABLED);

        BusinessException e = assertThrows(BusinessException.class,
                () -> dishService.deleteByIds(List.of(d.getId())));
        assertTrue(e.getMessage().contains("不能删除"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("秒杀建券：事务提交后预热 Redis 库存（v7 决策 4）")
    void seckillVoucherWarmedAfterCommit() {
        loginAsMerchant();
        VoucherDTO dto = new VoucherDTO();
        dto.setTitle("回归秒杀券" + System.nanoTime() % 1000);
        dto.setThreshold(0);
        dto.setActualValue(500);
        dto.setType(StatusConstants.VoucherType.SECKILL);
        dto.setStock(7);
        dto.setBeginTime(LocalDateTime.now().minusMinutes(5));
        dto.setEndTime(LocalDateTime.now().plusHours(2));
        voucherService.addVoucher(dto);

        var voucher = voucherService.lambdaQuery()
                .eq(com.smartlife.pojo.entity.Voucher::getTitle, dto.getTitle()).one();
        try {
            assertNotNull(voucher);
            String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucher.getId();
            assertEquals("7", redis.opsForValue().get(stockKey),
                    "事务提交后应预热 Redis 库存");
        } finally {
            if (voucher != null) {
                redis.delete(RedisConstants.SECKILL_STOCK_KEY + voucher.getId());
                redis.delete(RedisConstants.SECKILL_ORDER_KEY + voucher.getId());
                voucherService.lambdaUpdate()
                        .eq(com.smartlife.pojo.entity.Voucher::getId, voucher.getId()).remove();
            }
        }
    }
}
