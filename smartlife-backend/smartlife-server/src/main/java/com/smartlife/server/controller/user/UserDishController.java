package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.DishVO;
import com.smartlife.server.service.IDishService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端菜品浏览：按店+分类查起售菜品（含口味），免登录。
 */
@RestController
@RequestMapping("/dish")
@Validated
public class UserDishController {

    private final IDishService dishService;

    public UserDishController(IDishService dishService) {
        this.dishService = dishService;
    }

    @GetMapping("/list")
    public Result<List<DishVO>> list(@RequestParam @Positive Long shopId,
                                     @RequestParam @Positive Long categoryId) {
        return Result.ok(dishService.listOnSale(shopId, categoryId));
    }
}
