package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.AuditConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.UserVO;
import com.smartlife.server.mapper.UserMapper;
import com.smartlife.server.service.AuditRecorder;
import com.smartlife.server.service.IAdminUserService;
import com.smartlife.server.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 账号治理：封禁/解封与全站账号查询。
 * 封禁闭环 = DB status 置 0 的同时踢全端会话，只改 DB 则已在线会话最长再活 30min。
 */
@Service
public class AdminUserServiceImpl extends ServiceImpl<UserMapper, User> implements IAdminUserService {

    private final SessionService sessionService;
    private final AuditRecorder auditRecorder;

    public AdminUserServiceImpl(SessionService sessionService, AuditRecorder auditRecorder) {
        this.sessionService = sessionService;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public PageResult<UserVO> page(Integer role, Integer status, long current, long size) {
        Page<User> page = lambdaQuery()
                .eq(role != null, User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getId)
                .page(new Page<>(current, size));
        List<UserVO> vos = page.getRecords().stream().map(UserVO::from).toList();
        return PageResult.of(page.getTotal(), vos);
    }

    @Override
    @Transactional
    public void changeStatus(Long userId, int status) {
        if (status != StatusConstants.Common.DISABLED && status != StatusConstants.Common.ENABLED) {
            throw new BusinessException("非法的状态值");
        }
        if (userId.equals(BaseContext.require().getId())) {
            throw new BusinessException("不能操作自己的账号");
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getRole() != null && user.getRole() == RoleConstants.ADMIN) {
            throw new BusinessException("不能封禁管理员账号");
        }
        if (user.getStatus() != null && user.getStatus() == status) {
            return;
        }
        lambdaUpdate().eq(User::getId, userId).set(User::getStatus, status).update();
        if (status == StatusConstants.Common.DISABLED) {
            // 踢全端：ver 版本号加一，已签发 token 下次请求即 401
            sessionService.kickAll(userId);
        }
        auditRecorder.record(status == StatusConstants.Common.DISABLED
                        ? AuditConstants.ACTION_BAN_USER : AuditConstants.ACTION_ENABLE_USER,
                AuditConstants.TARGET_USER, userId,
                Map.of("from", user.getStatus(), "to", status));
    }
}
