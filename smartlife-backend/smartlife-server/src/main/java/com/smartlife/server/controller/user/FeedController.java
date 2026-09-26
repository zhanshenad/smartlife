package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.ScrollResultVO;
import com.smartlife.server.service.IFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注流收件箱：滚动分页（游标 lastId + offset，不用页码防插入错位）。
 */
@RestController
@RequestMapping("/feed")
@Tag(name = "用户端-关注流")
public class FeedController {

    private final IFeedService feedService;

    public FeedController(IFeedService feedService) {
        this.feedService = feedService;
    }

    @Operation(summary = "滚动分页读收件箱")
    @GetMapping
    public Result<ScrollResultVO<BlogVO>> scroll(@RequestParam(required = false) Long lastId,
                                                 @RequestParam(required = false) Integer offset,
                                                 @RequestParam(defaultValue = "5") int size) {
        return Result.ok(feedService.scroll(lastId, offset, size));
    }
}
