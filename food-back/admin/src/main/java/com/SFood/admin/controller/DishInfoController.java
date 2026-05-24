package com.SFood.admin.controller;

import com.SFood.system.dto.DishInfoDTO;
import com.SFood.system.dto.DishWithCategoryDTO;
import com.SFood.common.dto.ResultDTO;
import com.SFood.system.entity.DishInfo;
import com.SFood.system.service.DishInfoService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 菜品信息控制器
 */
@RestController
@RequestMapping("/dish/dishes")
@Validated
public class DishInfoController {

    @Autowired
    private DishInfoService dishInfoService;

    /**
     * 添加菜品
     * @param dishInfoDTO 菜品信息
     * @return 添加结果
     */
    @PostMapping
    public ResultDTO addDish(@Valid @RequestBody DishInfoDTO dishInfoDTO) {
        try {
            DishInfo dishInfo = dishInfoService.addDish(dishInfoDTO);
            return ResultDTO.success("添加菜品成功", dishInfo);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 更新菜品
     * @param dishInfoDTO 菜品信息
     * @return 更新结果
     */
    @PutMapping
    public ResultDTO updateDish(@Valid @RequestBody DishInfoDTO dishInfoDTO) {
        try {
            DishInfo dishInfo = dishInfoService.updateDish(dishInfoDTO);
            return ResultDTO.success("更新菜品成功", dishInfo);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 删除菜品
     * @param dishId 菜品ID
     * @return 删除结果
     */
    @DeleteMapping("/{dishId}")
    public ResultDTO deleteDish(@PathVariable Long dishId) {
        try {
            boolean result = dishInfoService.deleteDish(dishId);
            return ResultDTO.success("删除菜品成功", result);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询菜品
     * @param dishId 菜品ID
     * @return 菜品信息
     */
    @GetMapping("/{dishId}")
    public ResultDTO getDishById(@PathVariable Long dishId) {
        try {
            DishInfo dishInfo = dishInfoService.getDishById(dishId);
            return ResultDTO.success(dishInfo);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 查询所有菜品
     * @return 菜品列表
     */
    @GetMapping
    public ResultDTO getAllDishes() {
        try {
            List<DishInfo> dishes = dishInfoService.getAllDishes();
            return ResultDTO.success(dishes);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 分页查询菜品
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/page")
    public ResultDTO getDishesByPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<DishInfo> dishPage = dishInfoService.getDishesByPage(page, size);
            return ResultDTO.success(dishPage);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据分类查询菜品
     * @param categoryId 分类ID
     * @return 菜品列表
     */
    @GetMapping("/category/{categoryId}")
    public ResultDTO getDishesByCategory(@PathVariable Long categoryId) {
        try {
            List<DishInfo> dishes = dishInfoService.getDishesByCategory(categoryId);
            return ResultDTO.success(dishes);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据分类分页查询菜品
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/category/{categoryId}/page")
    public ResultDTO getDishesByCategoryPage(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<DishInfo> dishPage = dishInfoService.getDishesByCategoryPage(categoryId, page, size);
            return ResultDTO.success(dishPage);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据状态查询菜品
     * @param status 状态（1-上架，0-下架）
     * @return 菜品列表
     */
    @GetMapping("/status/{status}")
    public ResultDTO getDishesByStatus(@PathVariable Integer status) {
        try {
            List<DishInfo> dishes = dishInfoService.getDishesByStatus(status);
            return ResultDTO.success(dishes);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据菜品名称精确查询菜品
     * @param dishName 菜品名称
     * @return 菜品信息
     */
    @GetMapping("/name")
    public ResultDTO getDishByName(@RequestParam String dishName) {
        try {
            DishInfo dishInfo = dishInfoService.getDishByName(dishName);
            return ResultDTO.success(dishInfo);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据菜品名称模糊查询菜品
     * @param dishName 菜品名称（模糊匹配）
     * @return 菜品列表
     */
    @GetMapping("/search")
    public ResultDTO searchDishesByName(@RequestParam String dishName) {
        try {
            List<DishInfo> dishes = dishInfoService.searchDishesByName(dishName);
            return ResultDTO.success(dishes);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据菜品名称模糊查询菜品（分页）
     * @param dishName 菜品名称（模糊匹配）
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/search/page")
    public ResultDTO searchDishesByNamePage(
            @RequestParam String dishName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<DishInfo> dishPage = dishInfoService.searchDishesByNamePage(dishName, page, size);
            return ResultDTO.success(dishPage);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 查询菜品及其分类信息（根据菜品ID）
     * @param dishId 菜品ID
     * @return 包含分类信息的菜品DTO
     */
    @GetMapping("/{dishId}/with-category")
    public ResultDTO getDishWithCategoryById(@PathVariable Long dishId) {
        try {
            DishWithCategoryDTO dishWithCategory = dishInfoService.getDishWithCategoryById(dishId);
            return ResultDTO.success(dishWithCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 查询所有菜品及其分类信息
     * @return 包含分类信息的菜品DTO列表
     */
    @GetMapping("/with-category")
    public ResultDTO getAllDishesWithCategory() {
        try {
            List<DishWithCategoryDTO> dishesWithCategory = dishInfoService.getAllDishesWithCategory();
            return ResultDTO.success(dishesWithCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }

    /**
     * 根据分类查询菜品及其分类信息
     * @param categoryId 分类ID
     * @return 包含分类信息的菜品DTO列表
     */
    @GetMapping("/with-category/category/{categoryId}")
    public ResultDTO getDishesWithCategoryByCategoryId(@PathVariable Long categoryId) {
        try {
            List<DishWithCategoryDTO> dishesWithCategory = dishInfoService.getDishesWithCategoryByCategoryId(categoryId);
            return ResultDTO.success(dishesWithCategory);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
    
    /**
     * 查询所有菜品（按评分排序）
     * @return 按评分排序的菜品列表
     */
    @GetMapping("/by-rating")
    public ResultDTO getAllDishesByRating() {
        try {
            List<DishInfo> dishes = dishInfoService.getAllDishesByRating();
            return ResultDTO.success(dishes);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
    
    /**
     * 根据分类查询菜品（按评分排序）
     * @param categoryId 分类ID
     * @return 按评分排序的菜品列表
     */
    @GetMapping("/category/{categoryId}/by-rating")
    public ResultDTO getDishesByCategoryByRating(@PathVariable Long categoryId) {
        try {
            List<DishInfo> dishes = dishInfoService.getDishesByCategoryByRating(categoryId);
            return ResultDTO.success(dishes);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
    
    /**
     * 分页查询菜品（按评分排序）
     * @param page 页码
     * @param size 每页大小
     * @return 按评分排序的分页结果
     */
    @GetMapping("/page/by-rating")
    public ResultDTO getDishesByPageByRating(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<DishInfo> dishPage = dishInfoService.getDishesByPageByRating(page, size);
            return ResultDTO.success(dishPage);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
    
    /**
     * 根据分类分页查询菜品（按评分排序）
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 每页大小
     * @return 按评分排序的分页结果
     */
    @GetMapping("/category/{categoryId}/page/by-rating")
    public ResultDTO getDishesByCategoryPageByRating(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<DishInfo> dishPage = dishInfoService.getDishesByCategoryPageByRating(categoryId, page, size);
            return ResultDTO.success(dishPage);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
}