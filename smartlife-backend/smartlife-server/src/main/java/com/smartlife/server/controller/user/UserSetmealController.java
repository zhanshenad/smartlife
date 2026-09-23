package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.SetmealVO;
import com.smartlife.server.service.ISetmealService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端套餐浏览：按店+分类查起售套餐，免登录。
 */
@RestController
@RequestMapping("/setmeal")
@Validated
public class UserSetmealController {

    private final ISetmealService setmealService;

    public UserSetmealController(ISetmealService setmealService) {
        this.setmealService = setmealService;
    }

    @GetMapping("/list")
    public Result<List<SetmealVO>> list(@RequestParam @Positive Long shopId,
                                        @RequestParam @Positive Long categoryId) {
        return Result.ok(setmealService.listOnSale(shopId, categoryId));
    }
}
