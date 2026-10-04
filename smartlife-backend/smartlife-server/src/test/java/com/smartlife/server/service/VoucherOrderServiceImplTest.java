package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.RedisIdWorker;
import com.smartlife.pojo.dto.SeckillMessage;
import com.smartlife.pojo.entity.SeckillVoucher;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.pojo.vo.VoucherOrderVO;
import com.smartlife.server.mapper.SeckillVoucherMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * 领券两条链路：普通券同步领取（含 Set 回滚）、秒杀 Lua 预检与消费者落库（含漂移回补）。
 * RabbitTemplate 打桩：测试上下文连的是真 broker，成功路径不能把消息真发出去。
 * DB 改动由事务回滚；Redis 的 Set/stock 副作用在 AfterEach 手工清。
 */
@SpringBootTest
@Transactional
@DisplayName("券订单：领取/秒杀/异步落库")
class VoucherOrderServiceImplTest {

    private static final Long USER_ID = 48L;
//     private static final Long OTHER_USER_ID = 49L;
    private static final Long SHOP_ID = 1L;

    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private RedisIdWorker idWorker;
    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    /** mock 的 convertAndSend 不会完成 future，不打桩会等满 3 秒超时；这里模拟 broker 回 ack */
    @BeforeEach
    void stubPublisherConfirm() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(),
                any(SeckillMessage.class), any(CorrelationData.class));
    }

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    private void loginAsUser() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
    }

//     private void loginAsOther() {
//         BaseContext.set(new LoginUser(OTHER_USER_ID, 1, "另一个用户"));
//     }

    private Voucher newVoucher(int threshold, int actualValue) {
        Voucher v = new Voucher();
        v.setShopId(SHOP_ID);
        v.setTitle("测试券");
        v.setThreshold(threshold);
        v.setActualValue(actualValue);
        v.setType(StatusConstants.VoucherType.NORMAL);
        v.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(v);
        return v;
    }

    /** 造一张时间窗覆盖当前的秒杀券并预热 Redis */
    private Voucher newSeckillVoucher(int dbStock, int redisStock,
                                      LocalDateTime begin, LocalDateTime end) {
        Voucher v = newVoucher(0, 1000);
        v.setType(StatusConstants.VoucherType.SECKILL);
        voucherService.updateById(v);
        SeckillVoucher sv = new SeckillVoucher();
        sv.setVoucherId(v.getId());
        sv.setStock(dbStock);
        sv.setBeginTime(begin);
        sv.setEndTime(end);
        seckillVoucherMapper.insert(sv);
        redis.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + v.getId(), String.valueOf(redisStock));
        return v;
    }

    // ==================== 核销 / 退券（券域接口） ====================

    /** 造一张本人的未使用券订单 */
    private VoucherOrder myVoucherOrder(Long voucherId) {
        VoucherOrder vo = new VoucherOrder();
        vo.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
        vo.setUserId(USER_ID);
        vo.setVoucherId(voucherId);
        vo.setStatus(StatusConstants.VoucherOrder.UNUSED);
        voucherOrderService.save(vo);
        return vo;
    }

    @Test
    @DisplayName("核销：改状态并返回抵扣额")
    void redeemSucceeds() {
        Voucher v = newVoucher(1000, 500);
        VoucherOrder vo = myVoucherOrder(v.getId());

        int discount = voucherOrderService.redeem(vo.getId(), USER_ID, SHOP_ID, 2000);

        assertEquals(500, discount);
        assertEquals(StatusConstants.VoucherOrder.USED,
                voucherOrderService.getById(vo.getId()).getStatus());
    }

    @Test
    @DisplayName("核销：抵扣额以订单金额封顶")
    void redeemCapsAtAmount() {
        Voucher v = newVoucher(0, 1000);
        VoucherOrder vo = myVoucherOrder(v.getId());

        assertEquals(600, voucherOrderService.redeem(vo.getId(), USER_ID, SHOP_ID, 600));
    }

    @Test
    @DisplayName("核销：非本人的券拒绝")
    void redeemRejectsOthersVoucher() {
        Voucher v = newVoucher(0, 500);
        VoucherOrder vo = myVoucherOrder(v.getId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.redeem(vo.getId(), 999L, SHOP_ID, 1000));
        assertTrue(e.getMessage().contains("券不可用"));
    }

    @Test
    @DisplayName("核销后再核销：拒绝；退券后可再核销")
    void redeemTwiceRejectedUntilRestored() {
        Voucher v = newVoucher(0, 500);
        VoucherOrder vo = myVoucherOrder(v.getId());
        voucherOrderService.redeem(vo.getId(), USER_ID, SHOP_ID, 1000);

        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.redeem(vo.getId(), USER_ID, SHOP_ID, 1000));
        assertTrue(e.getMessage().contains("已被使用"));

        voucherOrderService.restore(vo.getId());
        assertEquals(500, voucherOrderService.redeem(vo.getId(), USER_ID, SHOP_ID, 1000));
    }

    // ==================== 普通券 ====================

    @Test
    @DisplayName("领普通券：落库入包")
    void grabPersists() {
        loginAsUser();
        Voucher v = newVoucher(3000, 500);
        voucherOrderService.grabVoucher(v.getId());
        List<VoucherOrderVO> pack = voucherOrderService.listMy();
        assertEquals(1, pack.size());
        assertEquals(v.getId(), pack.get(0).getVoucherId());
        assertEquals(StatusConstants.VoucherOrder.UNUSED, pack.get(0).getStatus());
    }

    @Test
    @DisplayName("重复领取：Redis Set 命中直接拒")
    void grabRejectsWhenSetHit() {
        loginAsUser();
        Voucher v = newVoucher(0, 500);
        redis.opsForSet().add(RedisConstants.VOUCHER_ORDER_KEY + v.getId(), USER_ID.toString());
        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.grabVoucher(v.getId()));
        assertTrue(e.getMessage().contains("已领取"));
    }

    @Test
    @DisplayName("落库撞唯一索引：Set 回滚，用户下次还能重试")
    void grabRollsBackSetOnDuplicateKey() {
        loginAsUser();
        Voucher v = newVoucher(0, 500);
        // DB 已有记录但 Set 为空（模拟 Redis 丢数据），save 必撞 uk_user_voucher
        VoucherOrder existed = new VoucherOrder();
        existed.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
        existed.setUserId(USER_ID);
        existed.setVoucherId(v.getId());
        voucherOrderService.save(existed);

        assertThrows(BusinessException.class, () -> voucherOrderService.grabVoucher(v.getId()));
        assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(
                RedisConstants.VOUCHER_ORDER_KEY + v.getId(), USER_ID.toString())),
                "落库失败后应把用户从 Set 摘掉");
    }

    @Test
    @DisplayName("用秒杀券走普通领券接口：拒绝")
    void grabRejectsSeckillVoucher() {
        loginAsUser();
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.grabVoucher(v.getId()));
        assertTrue(e.getMessage().contains("秒杀"));
    }

    // ==================== 秒杀预检 ====================

    @Test
    @DisplayName("秒杀成功：Redis 扣减 + 登记用户 + 发 MQ")
    void seckillPasses() {
        loginAsUser();
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        Long orderId = voucherOrderService.seckillVoucher(v.getId());

        assertNotNull(orderId);
        assertEquals("9", redis.opsForValue().get(RedisConstants.SECKILL_STOCK_KEY + v.getId()));
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(
                RedisConstants.SECKILL_ORDER_KEY + v.getId(), USER_ID.toString())));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(),
                any(SeckillMessage.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("秒杀未开始：拒绝且不动库存")
    void seckillRejectsNotStarted() {
        loginAsUser();
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusHours(2));
        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.seckillVoucher(v.getId()));
        assertTrue(e.getMessage().contains("尚未开始"));
        assertEquals("10", redis.opsForValue().get(RedisConstants.SECKILL_STOCK_KEY + v.getId()));
    }

    @Test
    @DisplayName("库存未预热：拒绝（-1 分支）")
    void seckillRejectsNotWarmed() {
        loginAsUser();
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        redis.delete(RedisConstants.SECKILL_STOCK_KEY + v.getId());
        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.seckillVoucher(v.getId()));
        assertTrue(e.getMessage().contains("预热"));
    }

    @Test
    @DisplayName("库存抢光：拒绝（1 分支）")
    void seckillRejectsOutOfStock() {
        loginAsUser();
        Voucher v = newSeckillVoucher(0, 0,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.seckillVoucher(v.getId()));
        assertTrue(e.getMessage().contains("抢光"));
    }

    // ==================== 消费者落库 ====================

    @Test
    @DisplayName("消费落库：订单入库 + DB 库存扣减")
    void handlePersists() {
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        voucherOrderService.handleSeckillMessage(new SeckillMessage(USER_ID, v.getId(), 9001L));

        VoucherOrder order = voucherOrderService.getById(9001L);
        assertNotNull(order);
        assertEquals(StatusConstants.VoucherOrder.UNUSED, order.getStatus());
        assertEquals(9, seckillVoucherMapper.selectById(v.getId()).getStock());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DB 库存漂移：回滚订单并回补 Redis（修黑马静默超卖）")
    void handleRollsBackOnDbDrift() {
        // 挂起测试事务：setRollbackOnly 只有在独立事务里才会真回滚（与消费者生产路径一致）
        Voucher v = newSeckillVoucher(0, 5,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        try {
            voucherOrderService.handleSeckillMessage(new SeckillMessage(USER_ID, v.getId(), 9002L));

            assertEquals(0, voucherOrderService.lambdaQuery()
                    .eq(VoucherOrder::getId, 9002L).count(), "订单应随事务回滚");
            assertEquals("6", redis.opsForValue().get(RedisConstants.SECKILL_STOCK_KEY + v.getId()),
                    "回补应把预扣的名额还回去");
        } finally {
            // 无测试事务兜底，手工清理
            voucherOrderService.removeById(9002L);
            seckillVoucherMapper.deleteById(v.getId());
            voucherService.removeById(v.getId());
            redis.delete(RedisConstants.SECKILL_STOCK_KEY + v.getId());
            redis.delete(RedisConstants.SECKILL_ORDER_KEY + v.getId());
        }
    }

    @Test
    @DisplayName("重复消费：跳过，库存只扣一次")
    void handleIdempotent() {
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        SeckillMessage msg = new SeckillMessage(USER_ID, v.getId(), 9003L);
        voucherOrderService.handleSeckillMessage(msg);
        voucherOrderService.handleSeckillMessage(msg);

        assertEquals(1, voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getId, 9003L).count());
        assertEquals(9, seckillVoucherMapper.selectById(v.getId()).getStock());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("落库撞唯一索引：应视为重复消费静默跳过，而不是炸事务")
    void handleTreatsDuplicateKeyAsRepeat() {
        // 挂起测试事务：MP 的 save 自带 @Transactional，异常会把独立事务标记 rollback-only，
        // 若实现只是 catch 而不 setRollbackOnly，execute 提交时会抛 UnexpectedRollbackException
        Voucher v = newSeckillVoucher(10, 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(2));
        try {
            VoucherOrder existed = new VoucherOrder();
            existed.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
            existed.setUserId(USER_ID);
            existed.setVoucherId(v.getId());
            voucherOrderService.save(existed);

            // 不同 orderId 但同 (userId, voucherId)，save 必撞 uk_user_voucher
            assertDoesNotThrow(() -> voucherOrderService.handleSeckillMessage(
                    new SeckillMessage(USER_ID, v.getId(), 9004L)));
            assertEquals(10, seckillVoucherMapper.selectById(v.getId()).getStock(),
                    "撞唯一索引的路径不应扣库存");
        } finally {
            voucherOrderService.lambdaUpdate()
                    .eq(VoucherOrder::getVoucherId, v.getId()).remove();
            seckillVoucherMapper.deleteById(v.getId());
            voucherService.removeById(v.getId());
            redis.delete(RedisConstants.SECKILL_STOCK_KEY + v.getId());
            redis.delete(RedisConstants.SECKILL_ORDER_KEY + v.getId());
        }
    }
}
