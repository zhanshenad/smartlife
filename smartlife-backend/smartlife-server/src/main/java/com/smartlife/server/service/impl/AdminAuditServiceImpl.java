package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.entity.AuditLog;
import com.smartlife.server.mapper.AuditLogMapper;
import com.smartlife.server.service.IAdminAuditService;
import org.springframework.stereotype.Service;

/** 审计查询实现。审计只增不改，这里纯读 */
@Service
public class AdminAuditServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements IAdminAuditService {

    @Override
    public PageResult<AuditLog> page(String action, String targetType, Long targetId,
                                     long current, long size) {
        Page<AuditLog> page = lambdaQuery()
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .eq(targetType != null && !targetType.isBlank(), AuditLog::getTargetType, targetType)
                .eq(targetId != null, AuditLog::getTargetId, targetId)
                .orderByDesc(AuditLog::getId)
                .page(new Page<>(current, size));
        return PageResult.of(page.getTotal(), page.getRecords());
    }
}
