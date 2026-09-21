/**
 * 用户端接口（role=1）：浏览店铺与菜品、下单支付、领券秒杀、探店社交。
 * 鉴权要求：登录即可（《重构计划》§5.1.4 权限矩阵的"其余"分支）——
 * AuthInterceptor 只对 /merchant/** 和 /admin/** 做额外的角色校验。
 * 包结构参见《重构计划》§3.4。本包在 P0 建骨架时先占位，P1 起逐步填充。
 */
package com.smartlife.server.controller.user;
