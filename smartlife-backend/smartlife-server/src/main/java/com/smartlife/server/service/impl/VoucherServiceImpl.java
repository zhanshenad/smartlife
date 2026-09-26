package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.dto.VoucherDTO;
import com.smartlife.pojo.entity.SeckillVoucher;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.pojo.vo.VoucherVO;
import com.smartlife.server.mapper.SeckillVoucherMapper;
import com.smartlife.server.mapper.VoucherMapper;
import com.smartlife.server.mapper.VoucherOrderMapper;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.service.IVoucherService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private final IShopService shopService;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public VoucherServiceImpl(IShopService shopService, SeckillVoucherMapper seckillVoucherMapper,
                              VoucherOrderMapper voucherOrderMapper, StringRedisTemplate stringRedisTemplate) {
        this.shopService = shopService;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public void addVoucher(VoucherDTO dto) {
        Long shopId = shopService.requireMyShopId();
        Voucher voucher = new Voucher();
        BeanUtils.copyProperties(dto, voucher);
        voucher.setShopId(shopId);
        voucher.setThreshold(dto.getThreshold() == null ? 0 : dto.getThreshold());
        voucher.setStatus(StatusConstants.Voucher.ON_SHELF);
        save(voucher);

        if (dto.getType() == StatusConstants.VoucherType.SECKILL) {
            checkSeckillFields(dto);
            SeckillVoucher sv = new SeckillVoucher();
            sv.setVoucherId(voucher.getId());
            sv.setStock(dto.getStock());
            sv.setBeginTime(dto.getBeginTime());
            sv.setEndTime(dto.getEndTime());
            seckillVoucherMapper.insert(sv);
            warmUpAfterCommit(voucher.getId(), dto.getStock());
        }
    }

    @Override
    public List<VoucherVO> listByShop(Long shopId) {
        List<Voucher> vouchers = lambdaQuery()
                .eq(Voucher::getShopId, shopId)
                .eq(Voucher::getStatus, StatusConstants.Voucher.ON_SHELF)
                .list();
        return attachSeckill(vouchers);
    }

    @Override
    public List<Voucher> listMyShop() {
        return lambdaQuery()
                .eq(Voucher::getShopId, shopService.requireMyShopId())
                .orderByDesc(Voucher::getCreateTime)
                .list();
    }

    @Override
    public void updateVoucher(Long id, VoucherDTO dto) {
        Voucher existed = requireMyVoucher(id);
        // 秒杀券的 stock/时间窗牵动 Redis 预热与已抢用户资格，一律不改（DTO 里的值被忽略）
        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setTitle(dto.getTitle());
        voucher.setSubTitle(dto.getSubTitle());
        voucher.setRules(dto.getRules());
        voucher.setThreshold(dto.getThreshold() == null ? 0 : dto.getThreshold());
        voucher.setActualValue(dto.getActualValue());
        if (dto.getType() != null && dto.getType() != existed.getType()) {
            throw new BusinessException("券类型不允许修改");
        }
        updateById(voucher);
    }

    @Override
    public void startStop(Long id, int status) {
        if (status != StatusConstants.Voucher.ON_SHELF && status != StatusConstants.Voucher.OFF_SHELF) {
            throw new BusinessException("非法的状态值");
        }
        requireMyVoucher(id);
        lambdaUpdate().eq(Voucher::getId, id).set(Voucher::getStatus, status).update();
    }

    @Override
    @Transactional
    public void removeVoucher(Long id) {
        // 调用本身做存在+归属校验，返回值此处用不到
        requireMyVoucher(id);
        Long claimed = voucherOrderMapper.selectCount(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getVoucherId, id));
        if (claimed != null && claimed > 0) {
            throw new BusinessException("该券已有 " + claimed + " 人领取，只能下架不能删除");
        }
        removeById(id);
        seckillVoucherMapper.deleteById(id);
        // 事务提交后再清 Redis：回滚了不删，避免"DB 券还在、Redis 预热没了"
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stringRedisTemplate.delete(RedisConstants.SECKILL_STOCK_KEY + id);
                stringRedisTemplate.delete(RedisConstants.SECKILL_ORDER_KEY + id);
                stringRedisTemplate.delete(RedisConstants.VOUCHER_ORDER_KEY + id);
            }
        });
    }

    /** 归属校验：只能操作本店的券 */
    private Voucher requireMyVoucher(Long id) {
        Voucher voucher = getById(id);
        if (voucher == null) {
            throw new BusinessException("券不存在");
        }
        if (!voucher.getShopId().equals(shopService.requireMyShopId())) {
            throw new BusinessException("只能操作本店铺的券");
        }
        return voucher;
    }

    /** 秒杀字段的条件校验：库存、时间窗必填且 begin < end */
    private void checkSeckillFields(VoucherDTO dto) {
        if (dto.getStock() == null || dto.getBeginTime() == null || dto.getEndTime() == null
                || !dto.getBeginTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("秒杀券须填写库存与合法的时间窗（开始时间须早于结束时间）");
        }
    }

    /** 事务提交后再写 Redis：回滚了不预热，避免"Redis 有库存、DB 无此券" */
    private void warmUpAfterCommit(Long voucherId, int stock) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stringRedisTemplate.opsForValue()
                        .set(RedisConstants.SECKILL_STOCK_KEY + voucherId, String.valueOf(stock));
            }
        });
    }

    /** 批量挂秒杀附加信息，普通券的库存/时间窗留 null */
    private List<VoucherVO> attachSeckill(List<Voucher> vouchers) {
        if (vouchers.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = vouchers.stream().map(Voucher::getId).collect(Collectors.toSet());
        Map<Long, SeckillVoucher> svMap = seckillVoucherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SeckillVoucher::getVoucherId, Function.identity()));
        List<VoucherVO> vos = new ArrayList<>(vouchers.size());
        for (Voucher v : vouchers) {
            VoucherVO vo = new VoucherVO();
            BeanUtils.copyProperties(v, vo);
            SeckillVoucher sv = svMap.get(v.getId());
            if (sv != null) {
                vo.setStock(sv.getStock());
                vo.setBeginTime(sv.getBeginTime());
                vo.setEndTime(sv.getEndTime());
            }
            vos.add(vo);
        }
        return vos;
    }
}
