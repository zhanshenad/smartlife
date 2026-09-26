package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.MQConstants;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.util.RedisIdWorker;
import com.smartlife.pojo.dto.SeckillMessage;
import com.smartlife.pojo.entity.SeckillVoucher;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.pojo.vo.VoucherOrderVO;
import com.smartlife.server.mapper.SeckillVoucherMapper;
import com.smartlife.server.mapper.VoucherOrderMapper;
import com.smartlife.server.service.IVoucherOrderService;
import com.smartlife.server.service.IVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 券订单：普通券同步领取、秒杀券 Lua 预检 + MQ 异步落库（§5.2）。
 * 消费者用 TransactionTemplate 精确控制"订单落库 + 扣 DB 库存"的原子边界。
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    /** 普通券预检脚本。正文见 resources/lua/grab.lua */
    private static final DefaultRedisScript<Long> GRAB_SCRIPT;

    /** 秒杀预检脚本。正文见 resources/lua/seckill.lua */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        GRAB_SCRIPT = new DefaultRedisScript<>();
        GRAB_SCRIPT.setLocation(new ClassPathResource("lua/grab.lua"));
        GRAB_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final IVoucherService voucherService;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final RedisIdWorker idWorker;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;

    public VoucherOrderServiceImpl(StringRedisTemplate redis, IVoucherService voucherService,
                                   SeckillVoucherMapper seckillVoucherMapper, RedisIdWorker idWorker,
                                   TransactionTemplate transactionTemplate, RabbitTemplate rabbitTemplate) {
        this.redis = redis;
        this.voucherService = voucherService;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.idWorker = idWorker;
        this.transactionTemplate = transactionTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== 普通券：同步链路（§5.2.5） ====================

    @Override
    public void grabVoucher(Long voucherId) {
        Long userId = BaseContext.require().getId();
        requireOnShelfVoucher(voucherId);
        // 是不是秒杀券看"有没有配套 tb_seckill_voucher 记录"，不看 type 字段（§5.2.1）
        if (seckillVoucherMapper.selectById(voucherId) != null) {
            throw new BusinessException("秒杀券请从秒杀入口抢购");
        }

        // ① Set 预检挡住绝大多数重复请求，不碰 DB
        Long r = redis.execute(GRAB_SCRIPT, List.of(), voucherId.toString(), userId.toString());
        if (r != null && r == StatusConstants.SeckillCode.REPEAT_ORDER) {
            throw new BusinessException("您已领取过该券");
        }

        // ② 同步落库，唯一索引是最终防线。主键非自增，同样走 RedisIdWorker
        VoucherOrder order = new VoucherOrder();
        order.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        try {
            save(order);
        } catch (DuplicateKeyException e) {
            // ③ 落库失败要把 Set 里的用户摘掉，否则会卡在"已领取过"但券包里没有
            redis.opsForSet().remove(RedisConstants.VOUCHER_ORDER_KEY + voucherId, userId.toString());
            throw new BusinessException("您已领取过该券");
        }
    }

    // ==================== 秒杀券：异步链路（§5.2.6） ====================

    @Override
    public Long seckillVoucher(Long voucherId) {
        Long userId = BaseContext.require().getId();
        requireOnShelfVoucher(voucherId);
        SeckillVoucher sv = seckillVoucherMapper.selectById(voucherId);
        if (sv == null) {
            throw new BusinessException("该券不是秒杀券");
        }

        // orderId 先于 Lua 生成：消息体里带上它，消费者落库直接用作主键（幂等判重依据）
        long orderId = idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY);
        Long r = redis.execute(SECKILL_SCRIPT, List.of(),
                voucherId.toString(), userId.toString(),
                String.valueOf(System.currentTimeMillis()),
                toEpochMillis(sv.getBeginTime()), toEpochMillis(sv.getEndTime()));
        if (r == null || r != StatusConstants.SeckillCode.SUCCESS) {
            throw new BusinessException(seckillFailMessage(r));
        }

        rabbitTemplate.convertAndSend(MQConstants.SECKILL_EXCHANGE, MQConstants.SECKILL_ORDER_ROUTING_KEY,
                new SeckillMessage(userId, voucherId, orderId));
        // 返回时订单尚未落库——这就是异步链路"快"的来源（§5.2.9）
        return orderId;
    }

    @Override
    public void handleSeckillMessage(SeckillMessage msg) {
        boolean[] stockOk = {true};
        Boolean duplicated = transactionTemplate.execute(status -> {
            if (getById(msg.getOrderId()) != null) {
                return true;
            }
            VoucherOrder order = new VoucherOrder();
            order.setId(msg.getOrderId());
            order.setUserId(msg.getUserId());
            order.setVoucherId(msg.getVoucherId());
            order.setStatus(StatusConstants.VoucherOrder.UNUSED);
            try {
                save(order);
            } catch (DuplicateKeyException e) {
                // 幂等第三重：uk_user_voucher 兜住极端并发下的重复落库（§5.2.6 设计点 3）
                return true;
            }
            if (seckillVoucherMapper.deductStock(msg.getVoucherId()) == 0) {
                // Redis 与 DB 漂移的报警信号：回滚订单并回补，不能只 warn 后照常提交（§5.2.6.1）
                status.setRollbackOnly();
                stockOk[0] = false;
            }
            return false;
        });

        if (Boolean.TRUE.equals(duplicated)) {
            log.info("重复消费，跳过落库：orderId={}", msg.getOrderId());
        } else if (!stockOk[0]) {
            // 事务已回滚，把名额还给 Redis 让用户可重新抢；error 级留痕
            redis.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + msg.getVoucherId());
            redis.opsForSet().remove(RedisConstants.SECKILL_ORDER_KEY + msg.getVoucherId(),
                    msg.getUserId().toString());
            log.error("DB 库存扣减失败，已回滚订单并回补 Redis，两者可能存在漂移：voucherId={}, userId={}",
                    msg.getVoucherId(), msg.getUserId());
        }
    }

    // ==================== 核销 / 退券 ====================

    @Override
    public int redeem(Long voucherOrderId, Long userId, Long shopId, int amount) {
        VoucherOrder vo = getById(voucherOrderId);
        if (vo == null || !vo.getUserId().equals(userId)) {
            throw new BusinessException("券不可用");
        }
        if (vo.getStatus() == null || vo.getStatus() != StatusConstants.VoucherOrder.UNUSED) {
            throw new BusinessException("券已被使用");
        }
        Voucher voucher = voucherService.getById(vo.getVoucherId());
        if (voucher == null || !voucher.getShopId().equals(shopId)) {
            throw new BusinessException("该券不适用于本店铺");
        }
        if (amount < voucher.getThreshold()) {
            throw new BusinessException("订单金额未满足券的使用门槛");
        }
        if (baseMapper.casUse(voucherOrderId, userId) == 0) {
            throw new BusinessException("券核销失败，请刷新后重试");
        }
        return Math.min(voucher.getActualValue(), amount);
    }

    @Override
    public void restore(Long voucherOrderId) {
        if (voucherOrderId != null) {
            baseMapper.casRestore(voucherOrderId);
        }
    }

    // ==================== 券包 ====================

    @Override
    public List<VoucherOrderVO> listMy() {
        Long userId = BaseContext.require().getId();
        List<VoucherOrder> orders = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .orderByDesc(VoucherOrder::getCreateTime)
                .list();
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> voucherIds = orders.stream().map(VoucherOrder::getVoucherId).collect(Collectors.toSet());
        Map<Long, Voucher> voucherMap = voucherService.listByIds(voucherIds).stream()
                .collect(Collectors.toMap(Voucher::getId, Function.identity()));
        return orders.stream().map(o -> {
            VoucherOrderVO vo = new VoucherOrderVO();
            vo.setId(o.getId());
            vo.setVoucherId(o.getVoucherId());
            vo.setStatus(o.getStatus());
            vo.setCreateTime(o.getCreateTime());
            Voucher v = voucherMap.get(o.getVoucherId());
            if (v != null) {
                vo.setTitle(v.getTitle());
                vo.setSubTitle(v.getSubTitle());
                vo.setThreshold(v.getThreshold());
                vo.setActualValue(v.getActualValue());
                vo.setType(v.getType());
                vo.setShopId(v.getShopId());
            }
            return vo;
        }).toList();
    }

    // ==================== 私有辅助 ====================

    private Voucher requireOnShelfVoucher(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            throw new BusinessException("券不存在");
        }
        if (voucher.getStatus() == null || voucher.getStatus() != StatusConstants.Voucher.ON_SHELF) {
            throw new BusinessException("该券已下架");
        }
        return voucher;
    }

    private String toEpochMillis(java.time.LocalDateTime time) {
        return String.valueOf(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private String seckillFailMessage(Long code) {
        if (code == null) {
            return "系统繁忙，请稍后重试";
        }
        if (code == StatusConstants.SeckillCode.STOCK_NOT_READY) {
            return "库存未预热，请稍后重试";
        }
        if (code == StatusConstants.SeckillCode.OUT_OF_STOCK) {
            return "该券已抢光";
        }
        if (code == StatusConstants.SeckillCode.REPEAT_ORDER) {
            return "您已抢过该券";
        }
        if (code == StatusConstants.SeckillCode.NOT_STARTED) {
            return "秒杀尚未开始";
        }
        if (code == StatusConstants.SeckillCode.ENDED) {
            return "秒杀已结束";
        }
        return "秒杀失败";
    }
}
