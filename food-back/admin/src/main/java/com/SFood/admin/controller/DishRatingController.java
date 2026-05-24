package com.SFood.admin.controller;

import com.SFood.system.entity.DishRating;
import com.SFood.system.service.DishRatingService;
import com.SFood.system.dto.RatingStatistics;
import com.SFood.common.dto.ResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜品评价控制器
 */
@RestController
@RequestMapping("/rating")
public class DishRatingController {
    
    @Autowired
    private DishRatingService dishRatingService;
    
    /**
     * 添加菜品评价
     */
    @PostMapping("/add")
    public ResultDTO<DishRating> addRating(@RequestBody DishRating dishRating) {
        DishRating result = dishRatingService.addRating(dishRating);
        return ResultDTO.success(result);
    }
    
    /**
     * 更新菜品评价
     */
    @PutMapping("/update")
    public ResultDTO<DishRating> updateRating(@RequestParam Long ratingId,
                                             @RequestParam(required = false) Integer score,
                                             @RequestParam(required = false) String comment) {
        DishRating result = dishRatingService.updateRating(ratingId, score, comment);
        return ResultDTO.success(result);
    }
    
    /**
     * 删除菜品评价
     */
    @DeleteMapping("/delete/{ratingId}")
    public ResultDTO<Void> deleteRating(@PathVariable Long ratingId) {
        dishRatingService.deleteRating(ratingId);
        return ResultDTO.success("删除成功", null);
    }
    
    /**
     * 获取菜品的所有评价
     */
    @GetMapping("/dish/{dishId}")
    public ResultDTO<List<DishRating>> getRatingsByDishId(@PathVariable Long dishId) {
        List<DishRating> ratings = dishRatingService.getRatingsByDishId(dishId);
        return ResultDTO.success(ratings);
    }
    
    /**
     * 获取用户的评价列表
     */
    @GetMapping("/user/{userId}")
    public ResultDTO<List<DishRating>> getRatingsByUserId(@PathVariable Long userId) {
        List<DishRating> ratings = dishRatingService.getRatingsByUserId(userId);
        return ResultDTO.success(ratings);
    }
    
    /**
     * 获取订单的评价列表
     */
    @GetMapping("/order/{orderId}")
    public ResultDTO<List<DishRating>> getRatingsByOrderId(@PathVariable String orderId) {
        List<DishRating> ratings = dishRatingService.getRatingsByOrderId(orderId);
        return ResultDTO.success(ratings);
    }
    
    /**
     * 获取菜品的平均评分
     */
    @GetMapping("/average/{dishId}")
    public ResultDTO<Double> getAverageRating(@PathVariable Long dishId) {
        Double average = dishRatingService.getAverageRating(dishId);
        return ResultDTO.success(average);
    }
    
    /**
     * 获取菜品的评价统计
     */
    @GetMapping("/statistics/{dishId}")
    public ResultDTO<RatingStatistics> getRatingStatistics(@PathVariable Long dishId) {
        RatingStatistics statistics = dishRatingService.getRatingStatistics(dishId);
        return ResultDTO.success(statistics);
    }
    
    /**
     * 检查用户是否已评价菜品
     */
    @GetMapping("/check")
    public ResultDTO<Boolean> hasUserRated(@RequestParam Long userId,
                                          @RequestParam Long dishId,
                                          @RequestParam String orderId) {
        boolean hasRated = dishRatingService.hasUserRated(userId, dishId, orderId);
        return ResultDTO.success(hasRated);
    }
}