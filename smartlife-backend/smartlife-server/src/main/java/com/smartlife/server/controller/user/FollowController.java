package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.UserSimpleVO;
import com.smartlife.server.service.IFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关注关系：关注/取关、是否关注、共同关注（SINTER）。
 */
@RestController
@RequestMapping("/follow")
@Tag(name = "用户端-关注")
public class FollowController {

    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    @Operation(summary = "关注/取关")
    @PutMapping("/{id}/{isFollow}")
    public Result<Void> follow(@PathVariable("id") Long id,
                               @PathVariable("isFollow") Boolean isFollow) {
        followService.follow(id, Boolean.TRUE.equals(isFollow));
        return Result.ok();
    }

    @Operation(summary = "是否已关注")
    @GetMapping("/or/not/{id}")
    public Result<Boolean> isFollow(@PathVariable("id") Long id) {
        return Result.ok(followService.isFollow(id));
    }

    @Operation(summary = "共同关注")
    @GetMapping("/common/{id}")
    public Result<List<UserSimpleVO>> common(@PathVariable("id") Long id) {
        return Result.ok(followService.common(id));
    }
}
