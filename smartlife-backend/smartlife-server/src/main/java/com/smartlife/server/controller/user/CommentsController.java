package com.smartlife.server.controller.user;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.CommentsDTO;
import com.smartlife.pojo.vo.CommentVO;
import com.smartlife.server.service.IBlogCommentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客评论：两级结构，一级时间倒序、二级时间正序。
 */
@RestController
@RequestMapping("/blog")
@Tag(name = "用户端-博客评论")
public class CommentsController {

    private final IBlogCommentsService commentsService;

    public CommentsController(IBlogCommentsService commentsService) {
        this.commentsService = commentsService;
    }

    @Operation(summary = "发评论")
    @PostMapping("/{id}/comment")
    public Result<Long> add(@PathVariable("id") Long id, @Valid @RequestBody CommentsDTO dto) {
        return Result.ok(commentsService.addComment(id, dto));
    }

    @Operation(summary = "评论分页")
    @GetMapping("/{id}/comment")
    public Result<PageResult<CommentVO>> page(@PathVariable("id") Long id,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(commentsService.pageByBlog(id, current, size));
    }
}
