package com.SFood.system.service;

import com.SFood.system.dto.DishInfoDTO;
import com.SFood.system.dto.DishWithCategoryDTO;
import com.SFood.system.entity.DishInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 菜品信息服务接口
 */
public interface DishInfoService {
    
    /**
     * 添加菜品
     * @param dishInfoDTO 菜品信息
     * @return 添加结果
     */
    DishInfo addDish(DishInfoDTO dishInfoDTO);
    
    /**
     * 更新菜品
     * @param dishInfoDTO 菜品信息
     * @return 更新结果
     */
    DishInfo updateDish(DishInfoDTO dishInfoDTO);
    
    /**
     * 删除菜品
     * @param dishId 菜品ID
     * @return 删除结果
     */
    boolean deleteDish(Long dishId);
    
    /**
     * 根据ID查询菜品
     * @param dishId 菜品ID
     * @return 菜品信息
     */
    DishInfo getDishById(Long dishId);
    
    /**
     * 查询所有菜品
     * @return 菜品列表
     */
    List<DishInfo> getAllDishes();
    
    /**
     * 分页查询菜品
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    Page<DishInfo> getDishesByPage(int page, int size);
    
    /**
     * 根据分类查询菜品
     * @param categoryId 分类ID
     * @return 菜品列表
     */
    List<DishInfo> getDishesByCategory(Long categoryId);
    
    /**
     * 根据分类分页查询菜品
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    Page<DishInfo> getDishesByCategoryPage(Long categoryId, int page, int size);
    
    /**
     * 根据状态查询菜品
     * @param status 状态（1-上架，0-下架）
     * @return 菜品列表
     */
    List<DishInfo> getDishesByStatus(Integer status);
    
    /**
     * 根据菜品名称查询菜品（精确匹配）
     * @param dishName 菜品名称
     * @return 菜品信息
     */
    DishInfo getDishByName(String dishName);
    
    /**
     * 根据菜品名称模糊查询菜品
     * @param dishName 菜品名称（模糊匹配）
     * @return 菜品列表
     */
    List<DishInfo> searchDishesByName(String dishName);
    
    /**
     * 根据菜品名称模糊查询菜品（分页）
     * @param dishName 菜品名称（模糊匹配）
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    Page<DishInfo> searchDishesByNamePage(String dishName, int page, int size);
    
    /**
     * 查询菜品及其分类信息（根据菜品ID）
     * @param dishId 菜品ID
     * @return 包含分类信息的菜品DTO
     */
    DishWithCategoryDTO getDishWithCategoryById(Long dishId);
    
    /**
     * 查询所有菜品及其分类信息
     * @return 包含分类信息的菜品DTO列表
     */
    List<DishWithCategoryDTO> getAllDishesWithCategory();
    
    /**
     * 根据分类查询菜品及其分类信息
     * @param categoryId 分类ID
     * @return 包含分类信息的菜品DTO列表
     */
    List<DishWithCategoryDTO> getDishesWithCategoryByCategoryId(Long categoryId);
    
    /**
     * 查询所有菜品（按评分排序）
     * @return 按评分排序的菜品列表
     */
    List<DishInfo> getAllDishesByRating();
    
    /**
     * 根据分类查询菜品（按评分排序）
     * @param categoryId 分类ID
     * @return 按评分排序的菜品列表
     */
    List<DishInfo> getDishesByCategoryByRating(Long categoryId);
    
    /**
     * 分页查询菜品（按评分排序）
     * @param page 页码
     * @param size 每页大小
     * @return 按评分排序的分页结果
     */
    Page<DishInfo> getDishesByPageByRating(int page, int size);
    
    /**
     * 根据分类分页查询菜品（按评分排序）
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 每页大小
     * @return 按评分排序的分页结果
     */
    Page<DishInfo> getDishesByCategoryPageByRating(Long categoryId, int page, int size);
}