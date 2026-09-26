package com.smartlife.server.service;

import com.smartlife.pojo.vo.UserSimpleVO;

import java.util.List;

public interface IFollowService {

    /** 关注/取关：先写 DB（uk 防重，撞索引视为已关注并补写 Redis 自愈），再写 Redis Set */
    void follow(Long followUserId, boolean isFollow);

    /** 是否已关注 */
    boolean isFollow(Long followUserId);

    /** 共同关注：SINTER 两个关注集合 */
    List<UserSimpleVO> common(Long targetUserId);
}
