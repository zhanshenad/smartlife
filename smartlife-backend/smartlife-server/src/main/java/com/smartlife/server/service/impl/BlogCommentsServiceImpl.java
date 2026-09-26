package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.CommentsDTO;
import com.smartlife.pojo.entity.Blog;
import com.smartlife.pojo.entity.BlogComments;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.CommentVO;
import com.smartlife.server.mapper.BlogCommentsMapper;
import com.smartlife.server.service.IBlogCommentsService;
import com.smartlife.server.service.IBlogService;
import com.smartlife.server.service.IUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 博客评论两级结构：一级 parentId=0，二级必须挂一级下（防无限套娃）。
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    private final IBlogService blogService;
    private final IUserService userService;

    public BlogCommentsServiceImpl(IBlogService blogService, IUserService userService) {
        this.blogService = blogService;
        this.userService = userService;
    }

    @Override
    public Long addComment(Long blogId, CommentsDTO dto) {
        if (blogService.getById(blogId) == null) {
            throw new BusinessException("笔记不存在");
        }
        long parentId = dto.getParentId() == null
                ? StatusConstants.Comment.TOP_LEVEL : dto.getParentId();
        if (parentId != StatusConstants.Comment.TOP_LEVEL) {
            BlogComments parent = getById(parentId);
            if (parent == null) {
                throw new BusinessException("被回复的评论不存在");
            }
            // 只允许两级：回复的目标必须是一级评论
            if (parent.getParentId() != null && parent.getParentId() != 0) {
                throw new BusinessException("评论最多两级");
            }
        }
        BlogComments comment = new BlogComments();
        comment.setBlogId(blogId);
        comment.setUserId(BaseContext.require().getId());
        comment.setParentId(parentId);
        comment.setAnswerId(dto.getAnswerId());
        comment.setContent(dto.getContent());
        comment.setLiked(0);
        comment.setStatus(StatusConstants.Comment.NORMAL);
        save(comment);
        blogService.lambdaUpdate()
                .setSql("comments = comments + 1")
                .eq(Blog::getId, blogId)
                .update();
        return comment.getId();
    }

    @Override
    public PageResult<CommentVO> pageByBlog(Long blogId, long current, long size) {
        Page<BlogComments> page = lambdaQuery()
                .eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getStatus, StatusConstants.Comment.NORMAL)
                .eq(BlogComments::getParentId, StatusConstants.Comment.TOP_LEVEL)
                .orderByDesc(BlogComments::getId)
                .page(new Page<>(current, size));
        List<BlogComments> top = page.getRecords();
        if (top.isEmpty()) {
            return PageResult.of(page.getTotal(), List.of());
        }

        // 本页一级评论的二级回复一次性拉全（每条回复量有限，不搞二级分页）
        List<Long> topIds = top.stream().map(BlogComments::getId).toList();
        Map<Long, List<BlogComments>> replies = lambdaQuery()
                .eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getStatus, StatusConstants.Comment.NORMAL)
                .in(BlogComments::getParentId, topIds)
                .orderByAsc(BlogComments::getId)
                .list().stream()
                .collect(Collectors.groupingBy(BlogComments::getParentId));

        List<CommentVO> vos = new ArrayList<>(top.size());
        for (BlogComments c : top) {
            CommentVO vo = toVO(c);
            List<BlogComments> children = replies.getOrDefault(c.getId(), List.of());
            vo.setChildren(children.stream()
                    .map(this::toVO).sorted(Comparator.comparing(CommentVO::getId)).toList());
            vos.add(vo);
        }
        fillAnswerNickName(vos);
        fillUsers(vos);
        return PageResult.of(page.getTotal(), vos);
    }

    // ==================== 私有辅助 ====================

    private CommentVO toVO(BlogComments c) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setUserId(c.getUserId());
        vo.setParentId(c.getParentId());
        vo.setAnswerId(c.getAnswerId());
        vo.setContent(c.getContent());
        vo.setLiked(c.getLiked());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    /** 填二级评论的"回复 @谁"：answerId → 目标评论 → 其作者昵称 */
    private void fillAnswerNickName(List<CommentVO> vos) {
        Set<Long> answerIds = new HashSet<>();
        for (CommentVO vo : vos) {
            if (vo.getChildren() == null) {
                continue;
            }
            for (CommentVO child : vo.getChildren()) {
                if (child.getAnswerId() != null) {
                    answerIds.add(child.getAnswerId());
                }
            }
        }
        if (answerIds.isEmpty()) {
            return;
        }
        Map<Long, Long> answerUser = listByIds(answerIds).stream()
                .collect(Collectors.toMap(BlogComments::getId, BlogComments::getUserId));
        Map<Long, User> users = userService.listByIds(new HashSet<>(answerUser.values())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        for (CommentVO vo : vos) {
            if (vo.getChildren() == null) {
                continue;
            }
            for (CommentVO child : vo.getChildren()) {
                Long uid = child.getAnswerId() == null ? null : answerUser.get(child.getAnswerId());
                User u = uid == null ? null : users.get(uid);
                if (u != null) {
                    child.setAnswerNickName(u.getNickName());
                }
            }
        }
    }

    /** 递归填充评论者昵称头像 */
    private void fillUsers(List<CommentVO> vos) {
        Set<Long> userIds = new HashSet<>();
        collectUserIds(vos, userIds);
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        applyUsers(vos, users);
    }

    private void collectUserIds(List<CommentVO> vos, Set<Long> out) {
        for (CommentVO vo : vos) {
            out.add(vo.getUserId());
            if (vo.getChildren() != null) {
                collectUserIds(vo.getChildren(), out);
            }
        }
    }

    private void applyUsers(List<CommentVO> vos, Map<Long, User> users) {
        for (CommentVO vo : vos) {
            User u = users.get(vo.getUserId());
            if (u != null) {
                vo.setNickName(u.getNickName());
                vo.setIcon(u.getIcon());
            }
            if (vo.getChildren() != null) {
                applyUsers(vo.getChildren(), users);
            }
        }
    }
}
