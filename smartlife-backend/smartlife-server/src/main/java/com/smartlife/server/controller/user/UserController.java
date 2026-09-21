package com.smartlife.server.controller.user;

import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.UserVO;
import com.smartlife.server.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端：当前用户信息。
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户端接口")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = userService.getById(BaseContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.ok(UserVO.from(user));
    }
}
