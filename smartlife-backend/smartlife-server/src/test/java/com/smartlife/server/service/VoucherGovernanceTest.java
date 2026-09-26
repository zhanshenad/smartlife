package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.util.RedisIdWorker;
import com.smartlife.pojo.dto.VoucherDTO;
import com.smartlife.pojo.entity.SeckillVoucher;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.server.mapper.SeckillVoucherMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券治理测试：改券（秒杀限改）、上下架、删券、到店核销。
 * 商家身份固定用 47 号测试商家（1 号店，P2 造的种子）。
 * 删券清 Redis 走 afterCommit，用 NOT_SUPPORTED 让事务真提交。
 */
@SpringBootTest
@DisplayName("券治理：改券/上下架/删除/到店核销")
class VoucherGovernanceTest {

    /** 47 号测试商家拥有 1 号店 */
    private static final Long MERCHANT_ID = 47L;
    private static final Long SHOP_ID = 1L;
    private static final Long CUSTOMER_ID = 48L;

    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private RedisIdWorker idWorker;
    @Autowired
    private StringRedisTemplate redis;

    private Voucher created;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        if (created != null && created.getId() != null) {
            seckillVoucherMapper.deleteById(created.getId());
            voucherService.removeById(created.getId());
            redis.delete(RedisConstants.SECKILL_STOCK_KEY + created.getId());
            redis.delete(RedisConstants.SECKILL_ORDER_KEY + created.getId());
            redis.delete(RedisConstants.VOUCHER_ORDER_KEY + created.getId());
            if (CUSTOMER_ID != null) {
                voucherOrderService.lambdaUpdate()
                        .eq(VoucherOrder::getVoucherId, created.getId()).remove();
            }
            created = null;
        }
    }

    private void loginAsMerchant() {
        BaseContext.set(new LoginUser(MERCHANT_ID, RoleConstants.MERCHANT, "测试商家"));
    }

    private Voucher newNormalVoucher() {
        Voucher v = new Voucher();
        v.setShopId(SHOP_ID);
        v.setTitle("治理测试普通券");
        v.setThreshold(0);
        v.setActualValue(500);
        v.setType(StatusConstants.VoucherType.NORMAL);
        v.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(v);
        created = v;
        return v;
    }

    private VoucherDTO dto(Integer stock) {
        VoucherDTO dto = new VoucherDTO();
        dto.setTitle("改后标题");
        dto.setThreshold(1000);
        dto.setActualValue(800);
        dto.setType(StatusConstants.VoucherType.SECKILL);
        dto.setStock(stock);
        dto.setBeginTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(2));
        return dto;
    }

    @Test
    @Transactional
    @DisplayName("改券：秒杀券库存与时间窗不动，基础字段生效")
    void updateVoucherLeavesSeckillFields() {
        loginAsMerchant();
        // 造秒杀型券（类型与 DTO 一致，才会走进"限改基础字段"分支）
        Voucher v = new Voucher();
        v.setShopId(SHOP_ID);
        v.setTitle("治理测试秒杀券");
        v.setThreshold(0);
        v.setActualValue(500);
        v.setType(StatusConstants.VoucherType.SECKILL);
        v.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(v);
        created = v;
        SeckillVoucher sv = new SeckillVoucher();
        sv.setVoucherId(v.getId());
        sv.setStock(10);
        // 造数截到整秒：DB datetime 精度 0 会把纳秒四舍五入，避免读回值差 1 秒的歧义
        sv.setBeginTime(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        sv.setEndTime(sv.getBeginTime().plusHours(2));
        seckillVoucherMapper.insert(sv);

        voucherService.updateVoucher(v.getId(), dto(999));

        Voucher after = voucherService.getById(v.getId());
        assertEquals("改后标题", after.getTitle());
        assertEquals(800, after.getActualValue());
        SeckillVoucher svAfter = seckillVoucherMapper.selectById(v.getId());
        assertEquals(10, svAfter.getStock(), "秒杀券库存不允许改");
        assertEquals(sv.getBeginTime(), svAfter.getBeginTime(), "时间窗不允许改（原值保留）");
    }

    @Test
    @Transactional
    @DisplayName("上下架：下架后领取拒绝")
    void offShelfBlocksGrabbing() {
        Voucher v = newNormalVoucher();
        // 下架是商家操作，领券才是顾客视角
        loginAsMerchant();
        voucherService.startStop(v.getId(), StatusConstants.Voucher.OFF_SHELF);

        BaseContext.set(new LoginUser(CUSTOMER_ID, RoleConstants.USER, "顾客"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherOrderService.grabVoucher(v.getId()));
        assertTrue(e.getMessage().contains("下架"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("删券：无领取记录可删，连带秒杀附加表与 Redis 三键")
    void removeVoucherCleansAll() {
        loginAsMerchant();
        Voucher v = newNormalVoucher();
        SeckillVoucher sv = new SeckillVoucher();
        sv.setVoucherId(v.getId());
        sv.setStock(5);
        sv.setBeginTime(LocalDateTime.now());
        sv.setEndTime(LocalDateTime.now().plusHours(2));
        seckillVoucherMapper.insert(sv);
        redis.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + v.getId(), "5");
        redis.opsForSet().add(RedisConstants.SECKILL_ORDER_KEY + v.getId(), "1");
        redis.opsForSet().add(RedisConstants.VOUCHER_ORDER_KEY + v.getId(), "1");

        voucherService.removeVoucher(v.getId());

        assertNull(voucherService.getById(v.getId()));
        assertNull(seckillVoucherMapper.selectById(v.getId()));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.SECKILL_STOCK_KEY + v.getId())));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.SECKILL_ORDER_KEY + v.getId())));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.VOUCHER_ORDER_KEY + v.getId())));
        created = null; // 已删，AfterEach 不再清
    }

    @Test
    @Transactional
    @DisplayName("删券护栏：有领取记录拒绝")
    void removeVoucherRejectsWhenClaimed() {
        loginAsMerchant();
        Voucher v = newNormalVoucher();
        VoucherOrder order = new VoucherOrder();
        order.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
        order.setUserId(CUSTOMER_ID);
        order.setVoucherId(v.getId());
        order.setStatus(StatusConstants.VoucherOrder.UNUSED);
        voucherOrderService.save(order);

        BusinessException e = assertThrows(BusinessException.class,
                () -> voucherService.removeVoucher(v.getId()));
        assertTrue(e.getMessage().contains("只能下架"));
    }

    @Test
    @Transactional
    @DisplayName("到店核销：全额抵扣；跨店券拒绝")
    void redeemOnSite() {
        loginAsMerchant();
        Voucher v = newNormalVoucher();
        VoucherOrder order = new VoucherOrder();
        order.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
        order.setUserId(CUSTOMER_ID);
        order.setVoucherId(v.getId());
        order.setStatus(StatusConstants.VoucherOrder.UNUSED);
        voucherOrderService.save(order);

        int discount = voucherOrderService.redeemOnSite(order.getId());
        assertEquals(500, discount, "到店无订单金额，抵扣额应等于券面值");
        assertEquals(StatusConstants.VoucherOrder.USED,
                voucherOrderService.getById(order.getId()).getStatus());

        // 满减券（门槛 2000 > 面值 300）：到店核销不走门槛校验，必须能核（review 修复点）
        Voucher thresholdVoucher = new Voucher();
        thresholdVoucher.setShopId(SHOP_ID);
        thresholdVoucher.setTitle("满减到店券");
        thresholdVoucher.setThreshold(2000);
        thresholdVoucher.setActualValue(300);
        thresholdVoucher.setType(StatusConstants.VoucherType.NORMAL);
        thresholdVoucher.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(thresholdVoucher);
        try {
            VoucherOrder order2 = new VoucherOrder();
            order2.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
            order2.setUserId(CUSTOMER_ID);
            order2.setVoucherId(thresholdVoucher.getId());
            order2.setStatus(StatusConstants.VoucherOrder.UNUSED);
            voucherOrderService.save(order2);

            assertEquals(300, voucherOrderService.redeemOnSite(order2.getId()),
                    "满减券到店核销不做门槛校验，全额抵扣");
        } finally {
            voucherOrderService.lambdaUpdate()
                    .eq(VoucherOrder::getVoucherId, thresholdVoucher.getId()).remove();
            voucherService.removeById(thresholdVoucher.getId());
        }

        // 跨店券：把券挂到别家店再试（重新造一张）
        Voucher others = new Voucher();
        others.setShopId(2L);
        others.setTitle("别家店的券");
        others.setThreshold(0);
        others.setActualValue(300);
        others.setType(StatusConstants.VoucherType.NORMAL);
        others.setStatus(StatusConstants.Voucher.ON_SHELF);
        voucherService.save(others);
        try {
            VoucherOrder order2 = new VoucherOrder();
            order2.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
            order2.setUserId(CUSTOMER_ID);
            order2.setVoucherId(others.getId());
            order2.setStatus(StatusConstants.VoucherOrder.UNUSED);
            voucherOrderService.save(order2);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> voucherOrderService.redeemOnSite(order2.getId()));
            assertTrue(e.getMessage().contains("不适用"));
        } finally {
            voucherOrderService.lambdaUpdate()
                    .eq(VoucherOrder::getVoucherId, others.getId()).remove();
            voucherService.removeById(others.getId());
        }
    }
}
