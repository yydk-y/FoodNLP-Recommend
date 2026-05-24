package com.SFood.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.SFood.system.entity.DishRating;
import com.SFood.system.mapper.DishRatingMapper;
import com.SFood.system.service.DishRatingService;
import com.SFood.system.dto.RatingStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 菜品评价服务实现类
 */
@Service
public class DishRatingServiceImpl implements DishRatingService {
    
    @Autowired
    private DishRatingMapper dishRatingMapper;
    
    @Override
    public DishRating addRating(DishRating dishRating) {
        // 验证评分范围
        if (dishRating.getScore() < 1 || dishRating.getScore() > 5) {
            throw new RuntimeException("评分必须在1-5星之间");
        }
        
        // 检查是否已评价过
        if (hasUserRated(dishRating.getUserId(), dishRating.getDishId(), dishRating.getOrderId())) {
            throw new RuntimeException("您已经评价过该菜品");
        }
        
        dishRatingMapper.insert(dishRating);
        return dishRating;
    }
    
    @Override
    public DishRating updateRating(Long ratingId, Integer score, String comment) {
        DishRating dishRating = dishRatingMapper.selectById(ratingId);
        if (dishRating != null) {
            if (score != null) {
                if (score < 1 || score > 5) {
                    throw new RuntimeException("评分必须在1-5星之间");
                }
                dishRating.setScore(score);
            }
            if (comment != null) {
                dishRating.setComment(comment);
            }
            dishRatingMapper.updateById(dishRating);
        }
        return dishRating;
    }
    
    @Override
    public void deleteRating(Long ratingId) {
        dishRatingMapper.deleteById(ratingId);
    }
    
    @Override
    public List<DishRating> getRatingsByDishId(Long dishId) {
        LambdaQueryWrapper<DishRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DishRating::getDishId, dishId)
               .orderByDesc(DishRating::getRatingTime);
        return dishRatingMapper.selectList(wrapper);
    }
    
    @Override
    public List<DishRating> getRatingsByUserId(Long userId) {
        LambdaQueryWrapper<DishRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DishRating::getUserId, userId)
               .orderByDesc(DishRating::getRatingTime);
        return dishRatingMapper.selectList(wrapper);
    }
    
    @Override
    public List<DishRating> getRatingsByOrderId(String orderId) {
        LambdaQueryWrapper<DishRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DishRating::getOrderId, orderId)
               .orderByDesc(DishRating::getRatingTime);
        return dishRatingMapper.selectList(wrapper);
    }
    
    @Override
    public Double getAverageRating(Long dishId) {
        List<DishRating> ratings = getRatingsByDishId(dishId);
        if (ratings.isEmpty()) {
            return 0.0;
        }
        
        double sum = ratings.stream().mapToInt(DishRating::getScore).sum();
        return sum / ratings.size();
    }
    
    @Override
    public RatingStatistics getRatingStatistics(Long dishId) {
        RatingStatistics statistics = new RatingStatistics();
        statistics.setDishId(dishId);
        
        List<DishRating> ratings = getRatingsByDishId(dishId);
        statistics.setTotalRatings(ratings.size());
        
        if (ratings.isEmpty()) {
            return statistics;
        }
        
        // 统计各星级数量
        statistics.setFiveStarCount((int) ratings.stream().filter(r -> r.getScore() == 5).count());
        statistics.setFourStarCount((int) ratings.stream().filter(r -> r.getScore() == 4).count());
        statistics.setThreeStarCount((int) ratings.stream().filter(r -> r.getScore() == 3).count());
        statistics.setTwoStarCount((int) ratings.stream().filter(r -> r.getScore() == 2).count());
        statistics.setOneStarCount((int) ratings.stream().filter(r -> r.getScore() == 1).count());
        
        // 计算平均分
        double averageScore = ratings.stream().mapToInt(DishRating::getScore).average().orElse(0.0);
        statistics.setAverageScore(Math.round(averageScore * 10.0) / 10.0); // 保留一位小数
        
        // 计算好评率（4星及以上）
        int positiveCount = statistics.getFourStarCount() + statistics.getFiveStarCount();
        if (statistics.getTotalRatings() > 0) {
            statistics.setPositiveRate(Math.round((positiveCount * 100.0 / statistics.getTotalRatings()) * 10.0) / 10.0);
        }
        
        return statistics;
    }
    
    @Override
    public boolean hasUserRated(Long userId, Long dishId, String orderId) {
        LambdaQueryWrapper<DishRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DishRating::getUserId, userId)
               .eq(DishRating::getDishId, dishId)
               .eq(DishRating::getOrderId, orderId);
        return dishRatingMapper.selectCount(wrapper) > 0;
    }
}