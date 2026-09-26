package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.dto.BlogDTO;
import com.smartlife.pojo.entity.Blog;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.BlogVO;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 笔记：发布推送粉丝收件箱（推模式）、点赞切换、排行榜顺序、热门排序。
 * DB 由事务回滚；Redis 副作用 AfterEach 手工清。
 */
@SpringBootTest
@Transactional
@DisplayName("探店笔记：发布/点赞/排行榜")
class BlogServiceImplTest {

    private static final Long USER_ID = 48L;
    private static final Long FAN_ID = 49L;
    private static final Long SHOP_ID = 1L;

    @Autowired
    private IBlogService blogService;
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.FOLLOWERS_KEY + USER_ID);
        redis.delete(RedisConstants.FEED_KEY + FAN_ID);
        var likedKeys = redis.keys(RedisConstants.BLOG_LIKED_KEY + "*");
        if (likedKeys != null && !likedKeys.isEmpty()) {
            redis.delete(likedKeys);
        }
    }

    private void loginAsUser() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
    }

    private BlogDTO newDto() {
        BlogDTO dto = new BlogDTO();
        dto.setShopId(SHOP_ID);
        dto.setTitle("测试笔记");
        dto.setImages("a.png");
        dto.setContent("测试正文");
        return dto;
    }

    @Test
    @DisplayName("发布：落库并推送到粉丝收件箱（follow:followers 而非 follow）")
    void saveBlogPushesToFollowers() {
        loginAsUser();
        redis.opsForSet().add(RedisConstants.FOLLOWERS_KEY + USER_ID, FAN_ID.toString());

        Long id = blogService.saveBlog(newDto());

        assertNotNull(id);
        assertNotNull(blogService.getById(id));
        assertEquals(Double.valueOf(id),
                redis.opsForZSet().score(RedisConstants.FEED_KEY + FAN_ID, id.toString()),
                "粉丝收件箱应收到该笔记，score=笔记id");
    }

    @Test
    @DisplayName("发布：店铺不存在拒绝")
    void saveBlogRejectsUnknownShop() {
        loginAsUser();
        BlogDTO dto = newDto();
        dto.setShopId(99999L);
        BusinessException e = assertThrows(BusinessException.class, () -> blogService.saveBlog(dto));
        assertTrue(e.getMessage().contains("店铺不存在"));
    }

    @Test
    @DisplayName("点赞切换：赞上 liked+1 入 ZSet，再点取消还原")
    void likeBlogToggles() {
        loginAsUser();
        Long id = blogService.saveBlog(newDto());

        assertTrue(blogService.likeBlog(id));
        assertEquals(1, blogService.getById(id).getLiked());
        assertNotNull(redis.opsForZSet()
                .score(RedisConstants.BLOG_LIKED_KEY + id, USER_ID.toString()));

        assertFalse(blogService.likeBlog(id));
        assertEquals(0, blogService.getById(id).getLiked());
        assertNull(redis.opsForZSet()
                .score(RedisConstants.BLOG_LIKED_KEY + id, USER_ID.toString()));
    }

    @Test
    @DisplayName("排行榜：按点赞时间正序取最早 5 人")
    void likeTop5ReturnsEarliest() {
        // 点赞者必须真实存在（排行榜要回查用户表），动态造（事务回滚自动清理）
        User liker1 = new User();
        liker1.setPhone("13900000301");
        liker1.setNickName("点赞者一");
        liker1.setRole(1);
        liker1.setStatus(1);
        userService.save(liker1);
        User liker2 = new User();
        liker2.setPhone("13900000302");
        liker2.setNickName("点赞者二");
        liker2.setRole(1);
        liker2.setStatus(1);
        userService.save(liker2);

        loginAsUser();
        Long id = blogService.saveBlog(newDto());
        // 直接构造 ZSet：两个用户不同时间戳点赞（liker1 最早）
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        redis.opsForZSet().add(key, liker1.getId().toString(), 1000L);
        redis.opsForZSet().add(key, liker2.getId().toString(), 2000L);

        List<UserSimpleVO> top = blogService.likeTop5(id);

        assertEquals(2, top.size());
        assertEquals(liker1.getId(), top.get(0).getId(), "时间戳小的（最早点赞）在前");
        assertEquals(liker2.getId(), top.get(1).getId());
    }

    @Test
    @DisplayName("热门分页：按 liked 降序")
    void pageHotOrdersByLiked() {
        loginAsUser();
        Long a = blogService.saveBlog(newDto());
        BlogDTO dto = newDto();
        dto.setTitle("第二篇");
        Long b = blogService.saveBlog(dto);
        blogService.lambdaUpdate().setSql("liked = liked + 5").eq(Blog::getId, b).update();

        List<BlogVO> hot = blogService.pageHot(1, 10).getRecords();

        assertEquals(b, hot.get(0).getId());
        assertTrue(hot.stream().anyMatch(v -> v.getId().equals(a)));
    }
}
