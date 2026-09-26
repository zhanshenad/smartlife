package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.AuditConstants;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.MerchantApplyDTO;
import com.smartlife.pojo.entity.MerchantApply;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.entity.User;
import com.smartlife.server.mapper.MerchantApplyMapper;
import com.smartlife.server.service.AuditRecorder;
import com.smartlife.server.service.IMerchantApplyService;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 入驻审核。通过的原子性靠"一个事务 + CAS 占位"保证：
 * UPDATE ... WHERE status=0 只会有一个管理员改得动，建店/回填/升角色同事务成败与共。
 */
@Slf4j
@Service
public class MerchantApplyServiceImpl extends ServiceImpl<MerchantApplyMapper, MerchantApply>
        implements IMerchantApplyService {

    private final IShopService shopService;
    private final IUserService userService;
    private final AuditRecorder auditRecorder;
    private final StringRedisTemplate redis;

    public MerchantApplyServiceImpl(IShopService shopService, IUserService userService,
                                    AuditRecorder auditRecorder, StringRedisTemplate redis) {
        this.shopService = shopService;
        this.userService = userService;
        this.auditRecorder = auditRecorder;
        this.redis = redis;
    }

    @Override
    public Long submit(MerchantApplyDTO dto) {
        Long userId = BaseContext.require().getId();
        if (BaseContext.require().getRole() != null
                && BaseContext.require().getRole() == RoleConstants.ADMIN) {
            throw new BusinessException("管理员无需申请入驻");
        }
        if (lambdaQuery().eq(MerchantApply::getUserId, userId)
                .eq(MerchantApply::getStatus, StatusConstants.MerchantApply.PENDING)
                .exists()) {
            throw new BusinessException("已有待审核的申请，请耐心等待");
        }
        if (shopService.lambdaQuery().eq(Shop::getMerchantId, userId).exists()) {
            throw new BusinessException("你已是商家，无需重复入驻");
        }
        MerchantApply apply = new MerchantApply();
        apply.setUserId(userId);
        apply.setShopName(dto.getShopName());
        apply.setShopTypeId(dto.getShopTypeId());
        apply.setArea(dto.getArea());
        apply.setAddress(dto.getAddress());
        apply.setX(dto.getX());
        apply.setY(dto.getY());
        apply.setContactName(dto.getContactName());
        apply.setContactPhone(dto.getContactPhone());
        apply.setLicenseImages(dto.getLicenseImages());
        apply.setStatus(StatusConstants.MerchantApply.PENDING);
        save(apply);
        return apply.getId();
    }

    @Override
    public MerchantApply myLatest() {
        return lambdaQuery()
                .eq(MerchantApply::getUserId, BaseContext.require().getId())
                .orderByDesc(MerchantApply::getId)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public PageResult<MerchantApply> page(Integer status, long current, long size) {
        Page<MerchantApply> page = lambdaQuery()
                .eq(status != null, MerchantApply::getStatus, status)
                .orderByDesc(MerchantApply::getId)
                .page(new Page<>(current, size));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    @Transactional
    public void approve(Long applyId) {
        // CAS 占位：只有待审核单能流转，两个管理员并发审同一单只有一个成功
        boolean occupied = lambdaUpdate()
                .eq(MerchantApply::getId, applyId)
                .eq(MerchantApply::getStatus, StatusConstants.MerchantApply.PENDING)
                .set(MerchantApply::getStatus, StatusConstants.MerchantApply.APPROVED)
                .set(MerchantApply::getAuditUserId, BaseContext.require().getId())
                .set(MerchantApply::getAuditTime, LocalDateTime.now())
                .update();
        if (!occupied) {
            throw new BusinessException("申请单不存在或已被处理");
        }
        MerchantApply apply = getById(applyId);

        if (shopService.lambdaQuery().eq(Shop::getMerchantId, apply.getUserId()).exists()) {
            throw new BusinessException("该商家已有店铺，无法重复建店");
        }
        // 建店 + 回填
        Shop shop = new Shop();
        shop.setMerchantId(apply.getUserId());
        shop.setName(apply.getShopName());
        shop.setTypeId(apply.getShopTypeId());
        shop.setArea(apply.getArea());
        shop.setAddress(apply.getAddress());
        shop.setX(apply.getX());
        shop.setY(apply.getY());
        shop.setStatus(StatusConstants.Common.ENABLED);
        shopService.save(shop);
        lambdaUpdate().eq(MerchantApply::getId, applyId)
                .set(MerchantApply::getShopId, shop.getId())
                .update();
        // 升商家角色：仅普通用户升，防止误把管理员降级成商家
        userService.lambdaUpdate()
                .eq(User::getId, apply.getUserId())
                .eq(User::getRole, RoleConstants.USER)
                .set(User::getRole, RoleConstants.MERCHANT)
                .update();
        // 坐标齐全才进 GEO 索引，否则"附近店铺"搜不到（列表仍可见）
        if (apply.getX() != null && apply.getY() != null) {
            redis.opsForGeo().add(RedisConstants.SHOP_GEO_KEY + apply.getShopTypeId(),
                    new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                            new Point(apply.getX(), apply.getY())));
        }
        auditRecorder.record(AuditConstants.ACTION_APPROVE_APPLY,
                AuditConstants.TARGET_MERCHANT_APPLY, applyId,
                Map.of("shopId", shop.getId(), "merchantId", apply.getUserId()));
    }

    @Override
    @Transactional
    public void reject(Long applyId, String remark) {
        if (remark == null || remark.isBlank()) {
            throw new BusinessException("驳回必须填写审核意见");
        }
        boolean occupied = lambdaUpdate()
                .eq(MerchantApply::getId, applyId)
                .eq(MerchantApply::getStatus, StatusConstants.MerchantApply.PENDING)
                .set(MerchantApply::getStatus, StatusConstants.MerchantApply.REJECTED)
                .set(MerchantApply::getAuditRemark, remark)
                .set(MerchantApply::getAuditUserId, BaseContext.require().getId())
                .set(MerchantApply::getAuditTime, LocalDateTime.now())
                .update();
        if (!occupied) {
            throw new BusinessException("申请单不存在或已被处理");
        }
        auditRecorder.record(AuditConstants.ACTION_REJECT_APPLY,
                AuditConstants.TARGET_MERCHANT_APPLY, applyId,
                Map.of("remark", remark));
    }
}
