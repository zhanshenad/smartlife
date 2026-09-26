package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.entity.Follow;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.UserSimpleVO;
import com.smartlife.server.mapper.FollowMapper;
import com.smartlife.server.service.IFollowService;
import com.smartlife.server.service.IUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关注关系：DB tb_follow 为权威（uk 防重），Redis 双 Set 为读路径
 * （follow:我关注谁 + follow:followers:谁关注我）。先 DB 后 Redis，撞 uk 时补写自愈。
 */
@Service
public class FollowServiceImpl implements IFollowService {

    private final FollowMapper followMapper;
    private final IUserService userService;
    private final StringRedisTemplate redis;

    public FollowServiceImpl(FollowMapper followMapper, IUserService userService,
                             StringRedisTemplate redis) {
        this.followMapper = followMapper;
        this.userService = userService;
        this.redis = redis;
    }

    @Override
    public void follow(Long followUserId, boolean isFollow) {
        Long userId = BaseContext.require().getId();
        if (userId.equals(followUserId)) {
            throw new BusinessException("不能关注自己");
        }
        if (userService.getById(followUserId) == null) {
            throw new BusinessException("用户不存在");
        }
        String key = followKey(userId);
        // 反向粉丝索引：被关注者收到自己的粉丝集合，发笔记推送收件箱用
        String followersKey = RedisConstants.FOLLOWERS_KEY + followUserId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            try {
                followMapper.insert(follow);
            } catch (DuplicateKeyException e) {
                // 已关注：幂等，顺带补写 Set 修复可能的 Redis 漂移
            }
            redis.opsForSet().add(key, followUserId.toString());
            redis.opsForSet().add(followersKey, userId.toString());
        } else {
            followMapper.delete(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            redis.opsForSet().remove(key, followUserId.toString());
            redis.opsForSet().remove(followersKey, userId.toString());
        }
    }

    @Override
    public boolean isFollow(Long followUserId) {
        Long userId = BaseContext.require().getId();
        return Boolean.TRUE.equals(redis.opsForSet().isMember(followKey(userId), followUserId.toString()));
    }

    @Override
    public List<UserSimpleVO> common(Long targetUserId) {
        Long userId = BaseContext.require().getId();
        Set<String> intersect = redis.opsForSet()
                .intersect(followKey(userId), followKey(targetUserId));
        if (intersect == null || intersect.isEmpty()) {
            return List.of();
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        Map<Long, User> users = userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return ids.stream().map(id -> {
            UserSimpleVO vo = new UserSimpleVO();
            User u = users.get(id);
            if (u != null) {
                vo.setId(u.getId());
                vo.setNickName(u.getNickName());
                vo.setIcon(u.getIcon());
            }
            return vo;
        }).filter(vo -> vo.getId() != null).toList();
    }

    private String followKey(Long userId) {
        return RedisConstants.FOLLOW_KEY + userId;
    }
}
