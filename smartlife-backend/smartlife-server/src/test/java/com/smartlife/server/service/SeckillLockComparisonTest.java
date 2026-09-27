package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.SeckillVoucher;
import com.smartlife.pojo.entity.Voucher;
// import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.server.mapper.SeckillVoucherMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
// import org.springframework.test.context.TestPropertySource;
// import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对照组（Redisson 锁同步链路）测试：用测试属性把开关切到 false，
 * 断言与异步链路同语义的四分支。锁副作用（SECKILL_LOCK_KEY）AfterEach 清。
 */
@SpringBootTest(properties = "smartlife.seckill.lua-enabled=false")
@DisplayName("秒杀对照组：Redisson 锁同步链路")
class SeckillLockComparisonTest {

    private static final Long USER_ID = 48L;
    private static final Long SHOP_ID = 1L;

    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.SECKILL_LOCK_KEY + USER_ID);
    }

    private Voucher newSeckillVoucher(int dbStock) {
        Voucher v = new Voucher();
        v.setShopId(SHOP_ID);
        v.setTitle("对照组秒杀券" + System.nanoTime() % 1000);
        v.setThreshold(0);
        v.setActualValue(500);
        v.setType(StatusConstants.VoucherType.SECKILL);
        v.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(v);
        SeckillVoucher sv = new SeckillVoucher();
        sv.setVoucherId(v.getId());
        sv.setStock(dbStock);
        sv.setBeginTime(LocalDateTime.now().minusMinutes(5));
        sv.setEndTime(LocalDateTime.now().plusHours(2));
        seckillVoucherMapper.insert(sv);
        return v;
    }

    @Test
    @Transactional
    @DisplayName("同步链路成功：返回 orderId 且订单已落库、DB 库存已扣")
    void lockPathPersists() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
        Voucher v = newSeckillVoucher(10);

        Long orderId = voucherOrderService.seckillVoucher(v.getId());

        assertNotNull(orderId);
        assertEquals(StatusConstants.VoucherOrder.UNUSED,
                voucherOrderService.getById(orderId).getStatus(), "同步链路返回时订单应已落库");
        assertEquals(9, seckillVoucherMapper.selectById(v.getId()).getStock());
    }

    @Test
    @Transactional
    @DisplayName("同一用户重复抢：DB 判重拒绝")
    void lockPathRejectsRepeat() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
        Voucher v = newSeckillVoucher(10);
        voucherOrderService.seckillVoucher(v.getId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.seckillVoucher(v.getId()));
        assertTrue(e.getMessage().contains("已抢过"));
        assertEquals(9, seckillVoucherMapper.selectById(v.getId()).getStock(), "重复请求不再扣库存");
    }

    @Test
    @Transactional
    @DisplayName("库存不足：事务回滚，订单不落库")
    void lockPathRollsBackWhenOutOfStock() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
        Voucher v = newSeckillVoucher(0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.seckillVoucher(v.getId()));
        assertTrue(e.getMessage().contains("抢光"));
    }

    @Test
    @Transactional
    @DisplayName("时间窗校验：未开始拒绝（与 Lua 3/4 码同语义）")
    void lockPathRejectsNotStarted() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
        Voucher v = new Voucher();
        v.setShopId(SHOP_ID);
        v.setTitle("未来券" + System.nanoTime() % 1000);
        v.setThreshold(0);
        v.setActualValue(500);
        v.setType(StatusConstants.VoucherType.SECKILL);
        v.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(v);
        SeckillVoucher sv = new SeckillVoucher();
        sv.setVoucherId(v.getId());
        sv.setStock(10);
        sv.setBeginTime(LocalDateTime.now().plusMinutes(10));
        sv.setEndTime(LocalDateTime.now().plusHours(2));
        seckillVoucherMapper.insert(sv);

        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.seckillVoucher(v.getId()));
        assertTrue(e.getMessage().contains("尚未开始"));
    }

    @Test
    @Transactional
    @DisplayName("订单域不受影响：券包仍能查到同步链路落的单")
    void lockPathVisibleInPack() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
        Voucher v = newSeckillVoucher(10);
        Long orderId = voucherOrderService.seckillVoucher(v.getId());

        assertTrue(voucherOrderService.listMy().stream()
                .anyMatch(vo -> orderId.equals(vo.getId())));
    }
}
