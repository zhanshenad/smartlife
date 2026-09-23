package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.DishDTO;
import com.smartlife.pojo.vo.DishVO;
import com.smartlife.server.service.IDishService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 商家端菜品管理。/merchant/** 由 AuthInterceptor 限定 role=2/3。
 */
@RestController
@RequestMapping("/merchant/dish")
@Validated
public class DishController {

    private final IDishService dishService;

    public DishController(IDishService dishService) {
        this.dishService = dishService;
    }

    @PostMapping
    public Result<Void> save(@RequestBody @Valid DishDTO dto) {
        dishService.saveWithFlavor(dto);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<PageResult<DishVO>> page(@RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(dishService.pageQuery(categoryId, status, name, current, size));
    }

    @GetMapping("/{id}")
    public Result<DishVO> detail(@PathVariable @Positive Long id) {
        return Result.ok(dishService.getByIdWithFlavor(id));
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Valid DishDTO dto) {
        dishService.updateWithFlavor(dto);
        return Result.ok();
    }

    /** 批量删除，形如 ?ids=1,2,3 */
    @DeleteMapping
    public Result<Void> delete(@RequestParam List<Long> ids) {
        dishService.deleteByIds(ids);
        return Result.ok();
    }

    /** 起售(1)/停售(0) */
    @PostMapping("/{id}/status/{status}")
    public Result<Void> startStop(@PathVariable @Positive Long id, @PathVariable Integer status) {
        dishService.startStop(id, status);
        return Result.ok();
    }
}
