/**
 * 管理端接口（role=3）：入驻审核、店铺/商品/券治理、账号治理、平台看板、操作审计。
 * 鉴权要求：仅 role=3。由 AuthInterceptor 按路径前缀 /admin/** 校验。
 * 关键动作（审核、封禁、踢人）都要落 tb_audit_log，可追溯谁在什么时候批了谁。
 * 包结构参见《重构计划》§3.4。本包在 P0 建骨架时先占位，P6 起逐步填充。
 */
package com.smartlife.server.controller.admin;
