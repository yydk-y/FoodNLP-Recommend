package com.SFood.admin.controller;

import com.SFood.system.dto.DishCategoryDTO;
import com.SFood.common.dto.ResultDTO;
import com.SFood.system.entity.DishCategory;
import com.SFood.system.service.DishCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 菜品分类控制器
 */
@RestController
@RequestMapping("/dish/categories")
@Validated
public class DishCategoryController {

    @Autowired
    private DishCategoryService dishCategoryService;

    /**
     * 添加分类
     * @param dishCategoryDTO 分类信息
     * @return 添加结果
     */
    @PostMapping
    public ResultDTO addCategory(@Valid @RequestBody DishCategoryDTO dishCategoryDTO) {
        try {
            DishCategory dishCategory = dishCategoryService.addCategory(dishCategoryDTO);
            return ResultDTO.success("添加分类成功", dishCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 更新分类
     * @param dishCategoryDTO 分类信息
     * @return 更新结果
     */
    @PutMapping
    public ResultDTO updateCategory(@Valid @RequestBody DishCategoryDTO dishCategoryDTO) {
        try {
            DishCategory dishCategory = dishCategoryService.updateCategory(dishCategoryDTO);
            return ResultDTO.success("更新分类成功", dishCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 删除分类
     * @param categoryId 分类ID
     * @return 删除结果
     */
    @DeleteMapping("/{categoryId}")
    public ResultDTO deleteCategory(@PathVariable Long categoryId) {
        try {
            boolean result = dishCategoryService.deleteCategory(categoryId);
            return ResultDTO.success("删除分类成功", result);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询分类
     * @param categoryId 分类ID
     * @return 分类信息
     */
    @GetMapping("/{categoryId}")
    public ResultDTO getCategoryById(@PathVariable Long categoryId) {
        try {
            DishCategory dishCategory = dishCategoryService.getCategoryById(categoryId);
            return ResultDTO.success(dishCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 查询所有分类
     * @return 分类列表
     */
    @GetMapping
    public ResultDTO getAllCategories() {
        try {
            List<DishCategory> categories = dishCategoryService.getAllCategories();
            return ResultDTO.success(categories);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 分页查询所有分类
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/page")
    public ResultDTO getAllCategoriesPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<DishCategory> categoriesPage = 
                dishCategoryService.getAllCategoriesPage(page, size);
            return ResultDTO.success(categoriesPage);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据分类名称查询
     * @param categoryName 分类名称
     * @return 分类信息
     */
    @GetMapping("/name")
    public ResultDTO getCategoryByName(@RequestParam String categoryName) {
        try {
            DishCategory dishCategory = dishCategoryService.getCategoryByName(categoryName);
            return ResultDTO.success(dishCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
}