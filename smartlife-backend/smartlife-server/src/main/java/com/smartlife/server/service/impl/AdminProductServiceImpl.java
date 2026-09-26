package com.smartlife.server.service.impl;

import com.smartlife.common.constant.AuditConstants;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.server.service.AuditRecorder;
import com.smartlife.server.service.IAdminProductService;
import com.smartlife.server.service.IDishService;
import com.smartlife.server.service.ISetmealService;
import com.smartlife.server.service.IVoucherService;
import com.smartlife.server.util.CacheClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 商品/券治理：违规下架强制执行，不走商家归属校验。
 * 改完按归属店铺清列表缓存，与商家自下架同构，否则用户端最长 30min 还能看到。
 */
@Service
public class AdminProductServiceImpl implements IAdminProductService {

    private final IDishService dishService;
    private final ISetmealService setmealService;
    private final IVoucherService voucherService;
    private final AuditRecorder auditRecorder;
    private final CacheClient cacheClient;

    public AdminProductServiceImpl(IDishService dishService, ISetmealService setmealService,
                                   IVoucherService voucherService, AuditRecorder auditRecorder,
                                   CacheClient cacheClient) {
        this.dishService = dishService;
        this.setmealService = setmealService;
        this.voucherService = voucherService;
        this.auditRecorder = auditRecorder;
        this.cacheClient = cacheClient;
    }

    @Override
    @Transactional
    public void dishStartStop(Long dishId, int status) {
        checkStatus(status);
        Dish dish = dishService.getById(dishId);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        dishService.lambdaUpdate()
                .eq(Dish::getId, dishId)
                .set(Dish::getStatus, status)
                .update();
        cacheClient.deleteByPrefix(RedisConstants.CACHE_DISH_KEY + dish.getShopId() + ":");
        auditRecorder.record(action(status), AuditConstants.TARGET_DISH, dishId);
    }

    @Override
    @Transactional
    public void setmealStartStop(Long setmealId, int status) {
        checkStatus(status);
        Setmeal setmeal = setmealService.getById(setmealId);
        if (setmeal == null) {
            throw new BusinessException("套餐不存在");
        }
        setmealService.lambdaUpdate()
                .eq(Setmeal::getId, setmealId)
                .set(Setmeal::getStatus, status)
                .update();
        cacheClient.deleteByPrefix(RedisConstants.CACHE_SETMEAL_KEY + setmeal.getShopId() + ":");
        auditRecorder.record(action(status), AuditConstants.TARGET_SETMEAL, setmealId);
    }

    @Override
    @Transactional
    public void voucherStartStop(Long voucherId, int status) {
        if (status != StatusConstants.Voucher.ON_SHELF && status != StatusConstants.Voucher.OFF_SHELF) {
            throw new BusinessException("非法的状态值");
        }
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            throw new BusinessException("券不存在");
        }
        voucherService.lambdaUpdate()
                .eq(Voucher::getId, voucherId)
                .set(Voucher::getStatus, status)
                .update();
        // 券的上下架只改状态不动 Redis：下架后领取入口被 ON_SHELF 校验挡住，已领的券仍可核销
        auditRecorder.record(voucherAction(status), AuditConstants.TARGET_VOUCHER, voucherId,
                Map.of("from", voucher.getStatus(), "to", status));
    }

    private void checkStatus(int status) {
        if (status != StatusConstants.Common.DISABLED && status != StatusConstants.Common.ENABLED) {
            throw new BusinessException("非法的状态值");
        }
    }

    private String action(int status) {
        return status == StatusConstants.Common.DISABLED
                ? AuditConstants.ACTION_PRODUCT_OFF_SHELF : AuditConstants.ACTION_PRODUCT_ON_SHELF;
    }

    private String voucherAction(int status) {
        return status == StatusConstants.Voucher.OFF_SHELF
                ? AuditConstants.ACTION_PRODUCT_OFF_SHELF : AuditConstants.ACTION_PRODUCT_ON_SHELF;
    }
}
