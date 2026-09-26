package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.UserSimpleVO;
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
 * 关注：DB+Redis 双写（关注集合 + 反向粉丝索引）、幂等、共同关注。
 * 48 之外的账号测试内动态造（49 号用户并不存在，实测踩过）。
 */
@SpringBootTest
@Transactional
@DisplayName("关注：双写/幂等/共同关注")
class FollowServiceImplTest {

    private static final Long USER_ID = 48L;

    @Autowired
    private IFollowService followService;
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        var followKeys = redis.keys(RedisConstants.FOLLOW_KEY + "*");
        var followerKeys = redis.keys(RedisConstants.FOLLOWERS_KEY + "*");
        if (followKeys != null && !followKeys.isEmpty()) {
            redis.delete(followKeys);
        }
        if (followerKeys != null && !followerKeys.isEmpty()) {
            redis.delete(followerKeys);
        }
    }

    private void login(Long userId) {
        BaseContext.set(new LoginUser(userId, 1, "测试用户" + userId));
    }

    /** 造一个真实存在的用户（事务回滚自动清理），返回其 id */
    private Long newUser(String phone) {
        User u = new User();
        u.setPhone(phone);
        u.setNickName(phone);
        u.setRole(1);
        u.setStatus(1);
        userService.save(u);
        return u.getId();
    }

    @Test
    @DisplayName("关注：DB 落行 + 双 Set 写入（关注集合 + 粉丝索引）")
    void followWritesBothSides() {
        Long other = newUser("13900000201");
        login(USER_ID);
        followService.follow(other, true);

        assertTrue(followService.isFollow(other));
        assertTrue(Boolean.TRUE.equals(redis.opsForSet()
                .isMember(RedisConstants.FOLLOWERS_KEY + other, USER_ID.toString())),
                "被关注者的粉丝索引应包含我");
    }

    @Test
    @DisplayName("重复关注：幂等不炸")
    void followIdempotent() {
        Long other = newUser("13900000202");
        login(USER_ID);
        followService.follow(other, true);
        followService.follow(other, true);

        assertTrue(followService.isFollow(other));
    }

    @Test
    @DisplayName("取关：双侧同步移除")
    void unfollowRemovesBothSides() {
        Long other = newUser("13900000203");
        login(USER_ID);
        followService.follow(other, true);
        followService.follow(other, false);

        assertFalse(followService.isFollow(other));
        assertFalse(Boolean.TRUE.equals(redis.opsForSet()
                .isMember(RedisConstants.FOLLOWERS_KEY + other, USER_ID.toString())));
    }

    @Test
    @DisplayName("关注自己：拒绝")
    void followRejectsSelf() {
        login(USER_ID);
        assertThrows(BusinessException.class, () -> followService.follow(USER_ID, true));
    }

    @Test
    @DisplayName("共同关注：SINTER 取交集")
    void commonIntersects() {
        Long other = newUser("13900000204");
        Long third = newUser("13900000205");
        login(USER_ID);
        followService.follow(third, true);
        login(other);
        followService.follow(third, true);

        login(USER_ID);
        List<UserSimpleVO> common = followService.common(other);
        assertEquals(1, common.size());
        assertEquals(third, common.get(0).getId());
    }
}
