package com.smartlife.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.constant.AuditConstants;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.AuditLog;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.UserVO;
import com.smartlife.common.result.PageResult;
import com.smartlife.server.mapper.AuditLogMapper;
import com.smartlife.server.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账号治理测试：封禁闭环（DB + 踢全端联动）、审计落库。
 * DB 走事务回滚；Redis 的 ver 副作用不回滚，AfterEach 手工清。
 */
@SpringBootTest
@Transactional
@DisplayName("账号治理：封禁/解封闭环")
class AdminUserServiceImplTest {

    private static final Long ADMIN_ID = 997L;

    @Autowired
    private IAdminUserService adminUserService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AuditLogMapper auditLogMapper;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    /** 清理动态造的用户的 Redis 副作用（ver 键无 TTL，不清会永久残留） */
    private void cleanRedis(Long userId) {
        redis.delete(RedisConstants.LOGIN_VER_KEY + userId);
        redis.delete(RedisConstants.LOGIN_USER_KEY + userId);
    }

    private User newUser(int role) {
        User u = new User();
        u.setPhone("139" + String.format("%08d", System.nanoTime() % 100000000L));
        u.setNickName("治理测试用户");
        u.setRole(role);
        u.setStatus(StatusConstants.Common.ENABLED);
        userMapper.insert(u);
        return u;
    }

    private void loginAsAdmin() {
        BaseContext.set(new LoginUser(ADMIN_ID, RoleConstants.ADMIN, "测试管理员"));
    }

    @Test
    @DisplayName("封禁：DB 置 0 的同时版本号加一（已在线会话立即失效）")
    void banKicksAllSessions() {
        loginAsAdmin();
        User u = newUser(RoleConstants.USER);
        try {
            adminUserService.changeStatus(u.getId(), StatusConstants.Common.DISABLED);

            assertEquals(StatusConstants.Common.DISABLED,
                    userMapper.selectById(u.getId()).getStatus());
            assertEquals("1",
                    redis.opsForValue().get(RedisConstants.LOGIN_VER_KEY + u.getId()));
        } finally {
            cleanRedis(u.getId());
        }
    }

    @Test
    @DisplayName("封禁写审计：BAN_USER + 变更前后快照")
    void banWritesAudit() {
        loginAsAdmin();
        User u = newUser(RoleConstants.USER);
        try {
            adminUserService.changeStatus(u.getId(), StatusConstants.Common.DISABLED);

            List<AuditLog> logs = auditLogMapper.selectList(
                    new LambdaQueryWrapper<AuditLog>()
                            .eq(AuditLog::getAction, AuditConstants.ACTION_BAN_USER)
                            .eq(AuditLog::getTargetId, u.getId()));
            assertEquals(1, logs.size());
            assertEquals(ADMIN_ID, logs.get(0).getOperatorId());
            assertTrue(logs.get(0).getDetail().contains("\"to\":0"));
        } finally {
            cleanRedis(u.getId());
        }
    }

    @Test
    @DisplayName("解封：status 回 1，不动版本号")
    void enableRestores() {
        loginAsAdmin();
        User u = newUser(RoleConstants.USER);
        try {
            adminUserService.changeStatus(u.getId(), StatusConstants.Common.DISABLED);
            adminUserService.changeStatus(u.getId(), StatusConstants.Common.ENABLED);

            assertEquals(StatusConstants.Common.ENABLED,
                    userMapper.selectById(u.getId()).getStatus());
            assertEquals("1",
                    redis.opsForValue().get(RedisConstants.LOGIN_VER_KEY + u.getId()));
        } finally {
            cleanRedis(u.getId());
        }
    }

    @Test
    @DisplayName("同状态重复提交：幂等跳过，不踢人不写审计")
    void sameStatusIsNoop() {
        loginAsAdmin();
        User u = newUser(RoleConstants.USER);
        try {
            adminUserService.changeStatus(u.getId(), StatusConstants.Common.ENABLED);

            assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.LOGIN_VER_KEY + u.getId())));
            assertEquals(0, auditLogMapper.selectCount(
                    new LambdaQueryWrapper<AuditLog>()
                            .eq(AuditLog::getTargetId, u.getId())));
        } finally {
            cleanRedis(u.getId());
        }
    }

    @Test
    @DisplayName("护栏：不能封管理员、不能操作自己")
    void guards() {
        loginAsAdmin();
        User admin = newUser(RoleConstants.ADMIN);
        try {
            BusinessException e1 = assertThrows(BusinessException.class,
                    () -> adminUserService.changeStatus(admin.getId(), StatusConstants.Common.DISABLED));
            assertTrue(e1.getMessage().contains("管理员"));

            BusinessException e2 = assertThrows(BusinessException.class,
                    () -> adminUserService.changeStatus(ADMIN_ID, StatusConstants.Common.DISABLED));
            assertTrue(e2.getMessage().contains("自己"));
        } finally {
            cleanRedis(admin.getId());
        }
    }

    @Test
    @DisplayName("分页：按角色过滤，返回 VO 且带状态")
    void pageFiltersByRole() {
        loginAsAdmin();
        User u = newUser(RoleConstants.MERCHANT);
        try {
            PageResult<UserVO> page = adminUserService.page(RoleConstants.MERCHANT,
                    StatusConstants.Common.ENABLED, 1, 10);
            assertTrue(page.getRecords().stream()
                    .anyMatch(vo -> u.getId().equals(vo.getId())));
            UserVO mine = page.getRecords().stream()
                    .filter(vo -> u.getId().equals(vo.getId())).findFirst().orElseThrow();
            assertEquals(StatusConstants.Common.ENABLED, mine.getStatus());
        } finally {
            cleanRedis(u.getId());
        }
    }
}
