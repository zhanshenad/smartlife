package com.smartlife.server.controller.admin;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.Category;
import com.smartlife.server.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** 管理端类目字典：菜品/套餐分类维护 */
@RestController
@RequestMapping("/admin/category")
@Validated
@Tag(name = "管理端-类目字典")
public class AdminCategoryController {

    private final ICategoryService categoryService;

    public AdminCategoryController(ICategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类分页")
    @GetMapping("/page")
    public Result<PageResult<Category>> page(@RequestParam(required = false) Integer type,
                                             @RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(categoryService.pageQuery(type, current, size));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Void> save(@RequestBody Category category) {
        categoryService.saveCategory(category);
        return Result.ok();
    }

    @Operation(summary = "修改分类（类型不允许改）")
    @PutMapping
    public Result<Void> update(@RequestBody Category category) {
        categoryService.updateCategory(category);
        return Result.ok();
    }

    @Operation(summary = "删除分类（分类下有商品则拒绝）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable @Positive Long id) {
        categoryService.deleteCategory(id);
        return Result.ok();
    }

    @Operation(summary = "启用/禁用分类")
    @PutMapping("/{id}/status/{status}")
    public Result<Void> startStop(@PathVariable @Positive Long id,
                                  @PathVariable Integer status) {
        categoryService.startStop(id, status);
        return Result.ok();
    }
}
