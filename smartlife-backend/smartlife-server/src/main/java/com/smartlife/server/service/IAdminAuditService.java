package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.entity.AuditLog;

/** 审计查询：按动作/目标过滤分页，只读不写（写入走 AuditRecorder） */
public interface IAdminAuditService extends IService<AuditLog> {

    PageResult<AuditLog> page(String action, String targetType, Long targetId,
                              long current, long size);
}
