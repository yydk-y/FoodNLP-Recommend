package com.SFood.system.service;

import com.SFood.system.entity.DishInfo;

import java.util.List;
import java.util.Map;

/**
 * 推荐解释服务
 * 用于基于关键词和推荐结果生成可读解释
 */
public interface RecommendationExplanationService {

    /**
     * 生成推荐解释
     *
     * @param userInput 用户原始输入
     * @param preferences BERT提取到的偏好关键词
     * @param recommendedDishes 推荐结果
     * @return 推荐解释文本
     */
    String generateExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes);
}
