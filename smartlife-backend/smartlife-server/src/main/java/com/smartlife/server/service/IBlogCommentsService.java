package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.CommentsDTO;
import com.smartlife.pojo.entity.BlogComments;
import com.smartlife.pojo.vo.CommentVO;

public interface IBlogCommentsService extends IService<BlogComments> {

    /** 发评论：两级限制（回复必须挂在一级评论下），计数 comments+1 */
    Long addComment(Long blogId, CommentsDTO dto);

    /** 评论分页：一级时间倒序，嵌套二级回复时间正序 */
    PageResult<CommentVO> pageByBlog(Long blogId, long current, long size);
}
