package com.SFood.system.service;

import com.SFood.system.entity.DishRating;
import com.SFood.system.dto.RatingStatistics;
import java.util.List;

/**
 * 菜品评价服务接口
 */
public interface DishRatingService {
    
    /**
     * 添加菜品评价
     * @param dishRating 评价信息
     * @return 评价信息
     */
    DishRating addRating(DishRating dishRating);
    
    /**
     * 更新菜品评价
     * @param ratingId 评价ID
     * @param score 评分
     * @param comment 评价内容
     * @return 更新后的评价信息
     */
    DishRating updateRating(Long ratingId, Integer score, String comment);
    
    /**
     * 删除菜品评价
     * @param ratingId 评价ID
     */
    void deleteRating(Long ratingId);
    
    /**
     * 获取菜品的所有评价
     * @param dishId 菜品ID
     * @return 评价列表
     */
    List<DishRating> getRatingsByDishId(Long dishId);
    
    /**
     * 获取用户的评价列表
     * @param userId 用户ID
     * @return 评价列表
     */
    List<DishRating> getRatingsByUserId(Long userId);
    
    /**
     * 获取订单的评价列表
     * @param orderId 订单ID
     * @return 评价列表
     */
    List<DishRating> getRatingsByOrderId(String orderId);
    
    /**
     * 获取菜品的平均评分
     * @param dishId 菜品ID
     * @return 平均评分
     */
    Double getAverageRating(Long dishId);
    
    /**
     * 获取菜品的评价统计
     * @param dishId 菜品ID
     * @return 评价统计信息
     */
    RatingStatistics getRatingStatistics(Long dishId);
    
    /**
     * 检查用户是否已评价菜品
     * @param userId 用户ID
     * @param dishId 菜品ID
     * @param orderId 订单ID
     * @return 是否已评价
     */
    boolean hasUserRated(Long userId, Long dishId, String orderId);
}