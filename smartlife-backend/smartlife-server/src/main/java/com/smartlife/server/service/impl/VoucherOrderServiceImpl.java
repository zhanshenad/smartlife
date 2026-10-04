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
import com.smartlife.server.mq.SeckillSlotRefund;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.service.IVoucherOrderService;
import com.smartlife.server.service.IVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.TimeUnit;
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

    /** 等待 broker 确认的超时秒数。超时属于"消息状态未知"，见 seckillWithLua 的分支说明 */
    private static final long MQ_CONFIRM_TIMEOUT_SECONDS = 3L;

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
    private final IShopService shopService;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final RedisIdWorker idWorker;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RedissonClient redissonClient;
    private final SeckillSlotRefund seckillSlotRefund;

    public VoucherOrderServiceImpl(StringRedisTemplate redis, IVoucherService voucherService,
                                   IShopService shopService, SeckillVoucherMapper seckillVoucherMapper,
                                   RedisIdWorker idWorker, TransactionTemplate transactionTemplate,
                                   RabbitTemplate rabbitTemplate, RedissonClient redissonClient,
                                   SeckillSlotRefund seckillSlotRefund) {
        this.redis = redis;
        this.voucherService = voucherService;
        this.shopService = shopService;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.idWorker = idWorker;
        this.transactionTemplate = transactionTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.redissonClient = redissonClient;
        this.seckillSlotRefund = seckillSlotRefund;
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

        // 重复提交 token：这里的收益比下单那处小——下面的 SISMEMBER 已经原子挡掉了同券重复领，
        // 双击本来就会拿到返回码 2。加它是补"落库失败回滚 Set 之后的重试窗口"这段 Lua 不管的缝。
        // key 用 用户:券 组合，否则"领完 A 券立刻领 B 券"这种合法操作会被误伤。
        String grabTokenKey = RedisConstants.VOUCHER_GRAB_TOKEN_KEY + userId + ":" + voucherId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(grabTokenKey, "1",
                RedisConstants.VOUCHER_GRAB_TOKEN_TTL_SECONDS, TimeUnit.SECONDS))) {
            throw new BusinessException("请勿重复提交");
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
            // ③ 落库失败要把 Set 里的用户摘掉，否则会卡在"已领取过"但券包里没有；
            // token 一并释放，让用户能立刻重试而不是白等 5 秒
            redis.opsForSet().remove(RedisConstants.VOUCHER_ORDER_KEY + voucherId, userId.toString());
            redis.delete(grabTokenKey);
            throw new BusinessException("您已领取过该券");
        }
    }

    // ==================== 秒杀券：异步链路（§5.2.6） ====================

    /**
     * 压测对照组开关（§5.2.10）：true 走 Lua 预检 + MQ 异步（主方案），
     * false 走 Redisson 锁同步链路。两套链路压测对比吞吐与长尾。
     */
    @Value("${smartlife.seckill.lua-enabled:true}")
    private boolean luaEnabled;

    @Override
    public Long seckillVoucher(Long voucherId) {
        return luaEnabled ? seckillWithLua(voucherId) : seckillWithLock(voucherId);
    }

    private Long seckillWithLua(Long voucherId) {
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

        // 带 CorrelationData 发送并同步等 broker 确认，避免"库存已扣、消息没进队列"卡死用户
        CorrelationData correlationData = new CorrelationData(String.valueOf(orderId));
        rabbitTemplate.convertAndSend(MQConstants.SECKILL_EXCHANGE, MQConstants.SECKILL_ORDER_ROUTING_KEY,
                new SeckillMessage(userId, voucherId, orderId), correlationData);

        boolean delivered = false;
        try {
            CorrelationData.Confirm confirm =
                    correlationData.getFuture().get(MQ_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // getReturned() 能同步读到，依赖 RabbitMQ「basic.return 先于 basic.ack」的顺序保证：
            // 路由不到队列时 broker 仍会回 ack，只能靠 returned 是否为 null 区分
            delivered = confirm.isAck() && correlationData.getReturned() == null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待 broker 确认被中断，消息状态未知，需人工核对：orderId={}", orderId, e);
            throw new BusinessException("系统繁忙，请稍后重试");
        } catch (Exception e) {
            // 超时：消息可能其实已投递、消费者会照常建单，这时补偿就等于超卖，所以只报不动
            log.error("等待 broker 确认失败，消息状态未知（可能已投递），不补偿以免超卖，需人工核对：orderId={}",
                    orderId, e);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        if (!delivered) {
            // 明确 nack 或路由失败：消息确定没进队列，不会产生订单，把预扣的名额还回去让用户能重试
            seckillSlotRefund.refund(voucherId, userId);
            log.error("秒杀消息未投递成功，已回补名额：orderId={}, voucherId={}, userId={}",
                    orderId, voucherId, userId);
            throw new BusinessException("系统繁忙，请稍后重试");
        }
        // 两条边界：不做 Outbox（那堵的是"Lua 成功后、发送之前进程崩溃"，与此处不重叠，属演进项）；
        // 消费者侧不动（手动 ACK 加三重幂等防线已完备）
        // 返回时订单尚未落库——这就是异步链路"快"的来源（§5.2.9）
        return orderId;
    }

    /**
     * 对照组：Redisson 锁同步链路。锁粒度是用户不是券——一人一单只需同用户串行，
     * 不同用户并行；库存超卖由 DB 乐观锁（stock > 0）兜底。
     * 返回时订单已落库，与异步链路形成"同步事务在响应里 vs 异步落库"的对照。
     */
    private Long seckillWithLock(Long voucherId) {
        Long userId = BaseContext.require().getId();
        requireOnShelfVoucher(voucherId);
        SeckillVoucher sv = seckillVoucherMapper.selectById(voucherId);
        if (sv == null) {
            throw new BusinessException("该券不是秒杀券");
        }
        long now = System.currentTimeMillis();
        if (now < sv.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
            throw new BusinessException("秒杀尚未开始");
        }
        if (now > sv.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
            throw new BusinessException("秒杀已结束");
        }

        RLock lock = redissonClient.getLock(RedisConstants.SECKILL_LOCK_KEY + userId);
        if (!lock.tryLock()) {
            throw new BusinessException("操作过于频繁，请稍后重试");
        }
        try {
            Long orderId = transactionTemplate.execute(status -> {
                if (lambdaQuery().eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId).exists()) {
                    throw new BusinessException("您已抢过该券");
                }
                long id = idWorker.nextId(RedisConstants.VOUCHER_ORDER_ID_KEY);
                VoucherOrder order = new VoucherOrder();
                order.setId(id);
                order.setUserId(userId);
                order.setVoucherId(voucherId);
                order.setStatus(StatusConstants.VoucherOrder.UNUSED);
                save(order);
                if (seckillVoucherMapper.deductStock(voucherId) == 0) {
                    status.setRollbackOnly();
                    throw new BusinessException("该券已抢光");
                }
                return id;
            });
            return orderId;
        } finally {
            lock.unlock();
        }
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
        Voucher voucher = redeemCore(voucherOrderId, userId, shopId);
        if (amount < voucher.getThreshold()) {
            throw new BusinessException("订单金额未满足券的使用门槛");
        }
        return Math.min(voucher.getActualValue(), amount);
    }

    @Override
    public void restore(Long voucherOrderId) {
        if (voucherOrderId != null) {
            baseMapper.casRestore(voucherOrderId);
        }
    }

    @Override
    public int redeemOnSite(Long voucherOrderId) {
        Long shopId = shopService.requireMyShopId();
        VoucherOrder vo = getById(voucherOrderId);
        if (vo == null) {
            throw new BusinessException("券码无效");
        }
        // 到店场景没有订单金额，不做门槛校验、全额抵扣：
        // 满减券的门槛由商家现场判断消费是否达标（走 redeem 的门槛校验会因 面值<门槛 永远失败）
        return redeemCore(voucherOrderId, vo.getUserId(), shopId).getActualValue();
    }

    /** 核销公共内核：归属/状态/归属店铺/CAS。门槛校验只属于有订单金额的场景 */
    private Voucher redeemCore(Long voucherOrderId, Long userId, Long shopId) {
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
        if (baseMapper.casUse(voucherOrderId, userId) == 0) {
            throw new BusinessException("券核销失败，请刷新后重试");
        }
        return voucher;
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
