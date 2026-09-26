package com.smartlife.server.service;

/** 管理端店铺治理：停业整顿 / 恢复 / 删除 */
public interface IAdminShopService {

    /** 停业整顿（status→0）或恢复（→1），联动删店铺详情缓存 */
    void changeStatus(Long shopId, int status);

    /** 删除店铺。有未完结订单则拒绝，要求先走订单巡检处置 */
    void remove(Long shopId);
}
