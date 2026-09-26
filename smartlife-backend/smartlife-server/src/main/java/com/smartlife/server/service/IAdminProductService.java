package com.smartlife.server.service;

/** 管理端商品/券治理：违规下架/恢复（旁路商家归属） */
public interface IAdminProductService {

    void dishStartStop(Long dishId, int status);

    void setmealStartStop(Long setmealId, int status);

    /** 券的强制上下架。状态域是 Voucher.ON_SHELF/OFF_SHELF（1/2），与商品启停 0/1 不同 */
    void voucherStartStop(Long voucherId, int status);
}
