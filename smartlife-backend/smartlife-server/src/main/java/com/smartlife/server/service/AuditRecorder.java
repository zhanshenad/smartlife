package com.smartlife.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.AuditLog;
import com.smartlife.server.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 审计落库组件。治理类接口在改动生效处调用，随调用方事务一起提交或回滚。
 * detail 传变更前后的 JSON 快照。
 */
@Component
public class AuditRecorder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditLogMapper auditLogMapper;

    public AuditRecorder(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 无明细重载：避免调用点传 null 时与 Map/String 重载产生歧义 */
    public void record(String action, String targetType, Long targetId) {
        record(action, targetType, targetId, (String) null);
    }

    /** Map 重载：统一走 Jackson 序列化，杜绝手拼 JSON 的转义/注入问题 */
    public void record(String action, String targetType, Long targetId, Map<String, Object> detail) {
        record(action, targetType, targetId, writeJson(detail));
    }

    public void record(String action, String targetType, Long targetId, String detail) {
        // operator_id 表上 NOT NULL：无登录上下文（如定时任务）直接炸出来，不留静默失败
        LoginUser operator = BaseContext.require();
        AuditLog log = new AuditLog();
        log.setOperatorId(operator.getId());
        log.setOperatorName(operator.getNickname());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            log.setIp(request.getRemoteAddr());
        }
        auditLogMapper.insert(log);
    }

    private static String writeJson(Map<String, Object> detail) {
        if (detail == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(detail);
        } catch (Exception e) {
            // 序列化失败不该吞掉审计：降级为 toString，至少保留可读痕迹
            return String.valueOf(detail);
        }
    }
}
