package com.SFood.system.service;

import com.SFood.system.dto.DishCategoryDTO;
import com.SFood.system.entity.DishCategory;

import java.util.List;

/**
 * 菜品分类服务接口
 */
public interface DishCategoryService {
    
    /**
     * 添加分类
     * @param dishCategoryDTO 分类信息
     * @return 添加结果
     */
    DishCategory addCategory(DishCategoryDTO dishCategoryDTO);
    
    /**
     * 更新分类
     * @param dishCategoryDTO 分类信息
     * @return 更新结果
     */
    DishCategory updateCategory(DishCategoryDTO dishCategoryDTO);
    
    /**
     * 删除分类
     * @param categoryId 分类ID
     * @return 删除结果
     */
    boolean deleteCategory(Long categoryId);
    
    /**
     * 根据ID查询分类
     * @param categoryId 分类ID
     * @return 分类信息
     */
    DishCategory getCategoryById(Long categoryId);
    
    /**
     * 查询所有分类
     * @return 分类列表
     */
    List<DishCategory> getAllCategories();
    
    /**
     * 分页查询所有分类
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<DishCategory> getAllCategoriesPage(int page, int size);
    
    /**
     * 根据分类名称查询
     * @param categoryName 分类名称
     * @return 分类信息
     */
    DishCategory getCategoryByName(String categoryName);
}