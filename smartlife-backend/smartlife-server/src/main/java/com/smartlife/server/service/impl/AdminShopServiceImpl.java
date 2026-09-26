package com.smartlife.server.service.impl;

import com.smartlife.common.constant.AuditConstants;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.server.service.AuditRecorder;
import com.smartlife.server.service.IAdminShopService;
import com.smartlife.server.service.IOrderService;
import com.smartlife.server.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 店铺治理。停业/恢复走 Cache Aside：改 DB 后删详情缓存，
 * 否则用户端最长 30min 仍读到营业中的旧店（逻辑过期缓存无 TTL 靠删触发重建）。
 */
@Service
public class AdminShopServiceImpl implements IAdminShopService {

    private final IShopService shopService;
    private final IOrderService orderService;
    private final AuditRecorder auditRecorder;
    private final StringRedisTemplate redis;

    public AdminShopServiceImpl(IShopService shopService, IOrderService orderService,
                                AuditRecorder auditRecorder, StringRedisTemplate redis) {
        this.shopService = shopService;
        this.orderService = orderService;
        this.auditRecorder = auditRecorder;
        this.redis = redis;
    }

    @Override
    @Transactional
    public void changeStatus(Long shopId, int status) {
        if (status != StatusConstants.Common.DISABLED && status != StatusConstants.Common.ENABLED) {
            throw new BusinessException("非法的状态值");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        if (shop.getStatus() != null && shop.getStatus() == status) {
            return;
        }
        shopService.lambdaUpdate()
                .eq(Shop::getId, shopId)
                .set(Shop::getStatus, status)
                .update();
        redis.delete(RedisConstants.CACHE_SHOP_KEY + shopId);
        auditRecorder.record(status == StatusConstants.Common.DISABLED
                        ? AuditConstants.ACTION_SHOP_SUSPEND : AuditConstants.ACTION_SHOP_RESUME,
                AuditConstants.TARGET_SHOP, shopId,
                Map.of("from", shop.getStatus(), "to", status));
    }

    @Override
    @Transactional
    public void remove(Long shopId) {
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        Long inFlight = orderService.lambdaQuery()
                .eq(Orders::getShopId, shopId)
                .in(Orders::getStatus,
                        StatusConstants.Order.PENDING_PAYMENT,
                        StatusConstants.Order.TO_BE_CONFIRMED,
                        StatusConstants.Order.CONFIRMED,
                        StatusConstants.Order.DELIVERY_IN_PROGRESS)
                .count();
        if (inFlight != null && inFlight > 0) {
            throw new BusinessException("该店铺还有 " + inFlight + " 笔未完结订单，请先在订单巡检中处置");
        }
        shopService.removeById(shopId);
        redis.delete(RedisConstants.CACHE_SHOP_KEY + shopId);
        // GEO 按 typeId 分 key，摘掉自己的成员
        redis.opsForZSet().remove(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(), shopId.toString());
        auditRecorder.record(AuditConstants.ACTION_SHOP_DELETE, AuditConstants.TARGET_SHOP, shopId,
                Map.of("name", shop.getName()));
    }
}
