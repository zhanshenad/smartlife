package com.smartlife.server.service;

public interface IStatsService {

    /** 店铺访问去重计数（登录用户口径，HyperLogLog 12KB 固定内存、0.81% 误差） */
    void recordShopVisit(Long shopId, Long userId);

    /** 某天的店铺 UV，date 为空取今天 */
    long shopUv(Long shopId, String date);
}
