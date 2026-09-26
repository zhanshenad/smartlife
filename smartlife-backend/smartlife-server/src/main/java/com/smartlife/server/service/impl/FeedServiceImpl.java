package com.smartlife.server.service.impl;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.ScrollResultVO;
import com.smartlife.server.service.IBlogService;
import com.smartlife.server.service.IFeedService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 关注流收件箱（推模式）：feed:{userId} ZSet，member/score 均为笔记 id。
 * 滚动分页 = max 含等值 + offset 跳同分。
 */
@Service
public class FeedServiceImpl implements IFeedService {

    private final IBlogService blogService;
    private final StringRedisTemplate redis;

    public FeedServiceImpl(IBlogService blogService, StringRedisTemplate redis) {
        this.blogService = blogService;
        this.redis = redis;
    }

    @Override
    public ScrollResultVO<BlogVO> scroll(Long lastId, Integer offset, int size) {
        Long userId = BaseContext.require().getId();
        String key = RedisConstants.FEED_KEY + userId;

        long max = lastId == null ? Long.MAX_VALUE : lastId;
        int os = offset == null ? 0 : offset;

        // ZREVRANGEBYSCORE key max 0 LIMIT os size：按 score 降序取 (os, os+size]
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, os, size);
        if (tuples == null || tuples.isEmpty()) {
            return new ScrollResultVO<>(List.of(), 0L, 0);
        }

        List<Long> ids = new ArrayList<>(tuples.size());
        long minTime = 0;
        int sameCount = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            ids.add(Long.valueOf(t.getValue()));
            // getScore() 契约可空，实际 ZSet 成员必有 score；0 与自增 id（从 1 起）不冲突
            Double scoreObj = t.getScore();
            long score = scoreObj == null ? 0L : scoreObj.longValue();
            if (score == minTime) {
                sameCount++;
            } else {
                minTime = score;
                sameCount = 1;
            }
        }
        return new ScrollResultVO<>(blogService.queryByIds(ids), minTime, sameCount);
    }
}
