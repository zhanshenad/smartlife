package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.dto.BlogDTO;
import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.ScrollResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收件箱滚动分页：翻页不重不漏，游标（minTime+offset）语义正确。
 */
@SpringBootTest
@Transactional
@DisplayName("关注流：滚动分页")
class FeedServiceImplTest {

    private static final Long USER_ID = 48L;
    private static final Long SHOP_ID = 1L;

    @Autowired
    private IFeedService feedService;
    @Autowired
    private IBlogService blogService;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.FEED_KEY + USER_ID);
    }

    private void loginAsUser() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
    }

    /** 造 3 篇真实笔记并手动塞进我的收件箱（score=blogId，单调） */
    private List<Long> prepareThreeBlogs() {
        loginAsUser();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BlogDTO dto = new BlogDTO();
            dto.setShopId(SHOP_ID);
            dto.setTitle("笔记" + i);
            dto.setImages("a.png");
            dto.setContent("正文" + i);
            Long id = blogService.saveBlog(dto);
            ids.add(id);
            redis.opsForZSet().add(RedisConstants.FEED_KEY + USER_ID, id.toString(), id);
        }
        return ids;
    }

    @Test
    @DisplayName("滚动分页：两页取完不重不漏，倒序")
    void scrollPagesWithoutOverlapOrGap() {
        List<Long> ids = prepareThreeBlogs();

        // 第一页 2 条：最新的两篇
        ScrollResultVO<BlogVO> page1 = feedService.scroll(null, null, 2);
        assertEquals(2, page1.getList().size());
        assertEquals(ids.get(2), page1.getList().get(0).getId(), "最新在前");
        assertEquals(ids.get(1), page1.getList().get(1).getId());
        assertEquals(ids.get(1), page1.getMinTime(), "游标=本页最小 score");
        assertEquals(1, page1.getOffset(), "score 唯一，同分仅自身");

        // 第二页接着游标取：剩下最旧一篇，且不重复
        ScrollResultVO<BlogVO> page2 = feedService.scroll(page1.getMinTime(), page1.getOffset(), 2);
        assertEquals(1, page2.getList().size());
        assertEquals(ids.get(0), page2.getList().get(0).getId());

        List<Long> seen = new ArrayList<>();
        page1.getList().forEach(v -> seen.add(v.getId()));
        page2.getList().forEach(v -> seen.add(v.getId()));
        assertEquals(3, seen.size(), "3 篇全见过");
        assertTrue(seen.stream().distinct().count() == 3, "无重复");
    }

    @Test
    @DisplayName("收件箱为空：返回空页不炸")
    void scrollEmptyInbox() {
        loginAsUser();
        ScrollResultVO<BlogVO> result = feedService.scroll(null, null, 5);
        assertTrue(result.getList().isEmpty());
    }
}
