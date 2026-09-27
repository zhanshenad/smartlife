package com.smartlife.server.controller.user;

import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.Category;
import com.smartlife.server.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户端分类字典：点餐页的分类 tab（管理端维护，这里只读启用中的） */
@RestController
@RequestMapping("/category")
@Tag(name = "用户端-分类字典")
public class CategoryController {

    private final ICategoryService categoryService;

    public CategoryController(ICategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "启用中的分类列表，可按类型过滤（1 菜品 2 套餐）")
    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(required = false) Integer type) {
        return Result.ok(categoryService.lambdaQuery()
                .eq(type != null, Category::getType, type)
                .eq(Category::getStatus, StatusConstants.Common.ENABLED)
                .orderByAsc(Category::getSort)
                .list());
    }
}
