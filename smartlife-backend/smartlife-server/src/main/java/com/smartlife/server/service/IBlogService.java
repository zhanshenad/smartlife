package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.BlogDTO;
import com.smartlife.pojo.entity.Blog;
import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.UserSimpleVO;

import java.util.List;

public interface IBlogService extends IService<Blog> {

    /** 按传入顺序批量查笔记（收件箱滚动分页用），附作者与点赞状态 */
    List<BlogVO> queryByIds(List<Long> ids);

    /** 发布笔记：校验店铺存在，落库后推送全部粉丝收件箱，返回笔记 id */
    Long saveBlog(BlogDTO dto);

    /** 笔记详情：附作者信息与当前用户点赞状态 */
    BlogVO queryBlog(Long id);

    /** 点赞/取消点赞（按当前状态切换），返回切换后是否已赞 */
    boolean likeBlog(Long id);

    /** 点赞排行榜：最早点赞的 5 位用户 */
    List<UserSimpleVO> likeTop5(Long id);

    /** 热门笔记分页（按点赞数降序） */
    PageResult<BlogVO> pageHot(long current, long size);

    /** 某用户的笔记分页 */
    PageResult<BlogVO> pageByUser(Long userId, long current, long size);
}
