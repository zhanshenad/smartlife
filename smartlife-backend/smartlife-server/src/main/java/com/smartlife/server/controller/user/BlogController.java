package com.smartlife.server.controller.user;

import com.smartlife.common.context.BaseContext;
import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.BlogDTO;
import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.UserSimpleVO;
import com.smartlife.server.service.IBlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 探店笔记：发布、详情、点赞（ZSet）、排行榜、热门/个人分页。
 */
@RestController
@RequestMapping("/blog")
@Tag(name = "用户端-探店笔记")
public class BlogController {

    private final IBlogService blogService;

    public BlogController(IBlogService blogService) {
        this.blogService = blogService;
    }

    @Operation(summary = "发布笔记")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody BlogDTO dto) {
        return Result.ok(blogService.saveBlog(dto));
    }

    @Operation(summary = "笔记详情")
    @GetMapping("/{id}")
    public Result<BlogVO> detail(@PathVariable("id") Long id) {
        return Result.ok(blogService.queryBlog(id));
    }

    @Operation(summary = "点赞/取消点赞")
    @PutMapping("/like/{id}")
    public Result<Boolean> like(@PathVariable("id") Long id) {
        return Result.ok(blogService.likeBlog(id));
    }

    @Operation(summary = "点赞排行榜（最早点赞的 5 人）")
    @GetMapping("/likes/{id}")
    public Result<List<UserSimpleVO>> likeTop(@PathVariable("id") Long id) {
        return Result.ok(blogService.likeTop5(id));
    }

    @Operation(summary = "热门笔记分页")
    @GetMapping("/hot")
    public Result<PageResult<BlogVO>> hot(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(blogService.pageHot(current, size));
    }

    @Operation(summary = "某用户的笔记分页")
    @GetMapping("/of/user")
    public Result<PageResult<BlogVO>> ofUser(@RequestParam("userId") Long userId,
                                             @RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(blogService.pageByUser(userId, current, size));
    }

    @Operation(summary = "我的笔记分页")
    @GetMapping("/of/me")
    public Result<PageResult<BlogVO>> ofMe(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(blogService.pageByUser(
                BaseContext.require().getId(), current, size));
    }
}
