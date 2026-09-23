package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.ShoppingCartDTO;
import com.smartlife.pojo.entity.ShoppingCart;
import com.smartlife.server.service.IShoppingCartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端购物车，需登录。
 */
@RestController
@RequestMapping("/shopping-cart")
public class ShoppingCartController {

    private final IShoppingCartService shoppingCartService;

    public ShoppingCartController(IShoppingCartService shoppingCartService) {
        this.shoppingCartService = shoppingCartService;
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody @Valid ShoppingCartDTO dto) {
        shoppingCartService.add(dto);
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<ShoppingCart>> list() {
        return Result.ok(shoppingCartService.listMine());
    }

    @PostMapping("/sub")
    public Result<Void> sub(@RequestBody @Valid ShoppingCartDTO dto) {
        shoppingCartService.sub(dto);
        return Result.ok();
    }

    @DeleteMapping("/clean")
    public Result<Void> clean() {
        shoppingCartService.cleanMine();
        return Result.ok();
    }
}
