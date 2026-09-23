package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.SetmealDTO;
import com.smartlife.pojo.vo.SetmealVO;
import com.smartlife.server.service.ISetmealService;
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
 * 商家端套餐管理。
 */
@RestController
@RequestMapping("/merchant/setmeal")
@Validated
public class SetmealController {

    private final ISetmealService setmealService;

    public SetmealController(ISetmealService setmealService) {
        this.setmealService = setmealService;
    }

    @PostMapping
    public Result<Void> save(@RequestBody @Valid SetmealDTO dto) {
        setmealService.saveWithDish(dto);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<PageResult<SetmealVO>> page(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String name,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(setmealService.pageQuery(categoryId, status, name, current, size));
    }

    @GetMapping("/{id}")
    public Result<SetmealVO> detail(@PathVariable @Positive Long id) {
        return Result.ok(setmealService.getByIdWithDish(id));
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Valid SetmealDTO dto) {
        setmealService.updateWithDish(dto);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam List<Long> ids) {
        setmealService.deleteByIds(ids);
        return Result.ok();
    }

    /** 起售(1)/停售(0)，起售校验关联菜品全部在售 */
    @PostMapping("/{id}/status/{status}")
    public Result<Void> startStop(@PathVariable @Positive Long id, @PathVariable Integer status) {
        setmealService.startStop(id, status);
        return Result.ok();
    }
}
