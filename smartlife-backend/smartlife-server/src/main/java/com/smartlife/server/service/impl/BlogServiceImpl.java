package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.BlogDTO;
import com.smartlife.pojo.entity.Blog;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.UserSimpleVO;
import com.smartlife.server.mapper.BlogMapper;
import com.smartlife.server.service.IBlogService;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.service.IUserService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 探店笔记：点赞权威在 Redis ZSet（blog:liked:{id}），
 * 落库后推送到粉丝收件箱（feed:{fanId}，推模式）。
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private static final int LIKE_TOP_SIZE = 5;

    private final IUserService userService;
    private final IShopService shopService;
    private final StringRedisTemplate redis;

    public BlogServiceImpl(IUserService userService, IShopService shopService,
                           StringRedisTemplate redis) {
        this.userService = userService;
        this.shopService = shopService;
        this.redis = redis;
    }

    @Override
    public Long saveBlog(BlogDTO dto) {
        LoginUser me = BaseContext.require();
        if (shopService.getById(dto.getShopId()) == null) {
            throw new BusinessException("店铺不存在");
        }
        Blog blog = new Blog();
        blog.setUserId(me.getId());
        blog.setShopId(dto.getShopId());
        blog.setTitle(dto.getTitle());
        blog.setImages(dto.getImages());
        blog.setContent(dto.getContent());
        blog.setLiked(0);
        blog.setComments(0);
        save(blog);

        // 推模式：推送到所有粉丝的收件箱，score = 笔记 id（自增、单调）
        Set<String> fans = redis.opsForSet()
                .members(RedisConstants.FOLLOWERS_KEY + me.getId());
        if (fans != null) {
            for (String fan : fans) {
                redis.opsForZSet().add(RedisConstants.FEED_KEY + fan,
                        blog.getId().toString(), blog.getId());
            }
        }
        return blog.getId();
    }

    @Override
    public List<BlogVO> queryByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Blog> blogs = listByIds(ids).stream()
                .collect(Collectors.toMap(Blog::getId, Function.identity()));
        List<BlogVO> vos = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Blog b = blogs.get(id);
            if (b != null) {
                BlogVO vo = new BlogVO();
                BeanUtils.copyProperties(b, vo);
                vos.add(vo);
            }
        }
        fillAuthor(vos);
        Long me = BaseContext.get() == null ? null : BaseContext.get().getId();
        if (me != null) {
            for (BlogVO vo : vos) {
                vo.setIsLike(isLiked(vo.getId(), me));
            }
        }
        return vos;
    }

    @Override
    public BlogVO queryBlog(Long id) {
        Blog blog = requireBlog(id);
        BlogVO vo = new BlogVO();
        BeanUtils.copyProperties(blog, vo);
        fillAuthor(List.of(vo));
        vo.setIsLike(isLiked(id, BaseContext.require().getId()));
        return vo;
    }

    @Override
    public boolean likeBlog(Long id) {
        requireBlog(id);
        Long userId = BaseContext.require().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // add 返回"是否新增"，与 remove 一样原子，消除查后改的并发窗口
        Boolean added = redis.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        if (Boolean.TRUE.equals(added)) {
            lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, id).update();
            return true;
        }
        redis.opsForZSet().remove(key, userId.toString());
        lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, id).update();
        return false;
    }

    @Override
    public List<UserSimpleVO> likeTop5(Long id) {
        requireBlog(id);
        // ZRANGE 0 4：score 为点赞时间戳，升序即最早点赞在前
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().rangeWithScores(RedisConstants.BLOG_LIKED_KEY + id, 0, LIKE_TOP_SIZE - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = tuples.stream().map(t -> Long.valueOf(t.getValue())).toList();
        Map<Long, User> users = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<UserSimpleVO> result = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            User u = users.get(uid);
            if (u != null) {
                UserSimpleVO vo = new UserSimpleVO();
                vo.setId(u.getId());
                vo.setNickName(u.getNickName());
                vo.setIcon(u.getIcon());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public PageResult<BlogVO> pageHot(long current, long size) {
        Page<Blog> page = lambdaQuery()
                .orderByDesc(Blog::getLiked)
                .orderByDesc(Blog::getId)
                .page(new Page<>(current, size));
        return toVOPage(page);
    }

    @Override
    public PageResult<BlogVO> pageByUser(Long userId, long current, long size) {
        Page<Blog> page = lambdaQuery()
                .eq(Blog::getUserId, userId)
                .orderByDesc(Blog::getId)
                .page(new Page<>(current, size));
        return toVOPage(page);
    }

    // ==================== 私有辅助 ====================

    private PageResult<BlogVO> toVOPage(Page<Blog> page) {
        List<BlogVO> vos = page.getRecords().stream().map(b -> {
            BlogVO vo = new BlogVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).toList();
        fillAuthor(vos);
        Long me = BaseContext.get() == null ? null : BaseContext.get().getId();
        if (me != null) {
            for (BlogVO vo : vos) {
                vo.setIsLike(isLiked(vo.getId(), me));
            }
        }
        return PageResult.of(page.getTotal(), vos);
    }

    /** 批量填充作者昵称/头像 */
    private void fillAuthor(List<BlogVO> vos) {
        Set<Long> userIds = vos.stream().map(BlogVO::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> users = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        for (BlogVO vo : vos) {
            User u = users.get(vo.getUserId());
            if (u != null) {
                vo.setNickName(u.getNickName());
                vo.setIcon(u.getIcon());
            }
        }
    }

    private boolean isLiked(Long blogId, Long userId) {
        return redis.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blogId, userId.toString()) != null;
    }

    private Blog requireBlog(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            throw new BusinessException("笔记不存在");
        }
        return blog;
    }
}
