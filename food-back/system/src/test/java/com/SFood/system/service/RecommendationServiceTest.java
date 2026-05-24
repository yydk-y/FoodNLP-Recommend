package com.SFood.system.service;

import com.SFood.system.entity.DishInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * 推荐服务测试类
 */
@SpringBootTest
public class RecommendationServiceTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private NLPService nlpService;

    @Test
    public void testRecommendDishes() {
        // 测试用户输入
        String userInput = "我喜欢吃辣的，不要香菜";
        int limit = 5;

        // 调用推荐服务
        List<DishInfo> recommendedDishes = recommendationService.recommendDishes(userInput, limit);

        // 打印推荐结果
        System.out.println("推荐结果:");
        for (DishInfo dish : recommendedDishes) {
            System.out.println("菜品名称: " + dish.getDishName() + ", 价格: " + dish.getPrice());
        }

        // 验证结果
        assert recommendedDishes != null;
        assert recommendedDishes.size() <= limit;
    }

    @Test
    public void testRecommendDishesByPreferences() {
        // 提取用户偏好
        String userInput = "我喜欢吃甜的，不要海鲜";
        Map<String, Object> preferences = nlpService.extractFoodPreferences(userInput);
        int limit = 3;

        // 调用推荐服务
        List<DishInfo> recommendedDishes = recommendationService.recommendDishesByPreferences(preferences, limit);

        // 打印推荐结果
        System.out.println("基于偏好的推荐结果:");
        for (DishInfo dish : recommendedDishes) {
            System.out.println("菜品名称: " + dish.getDishName() + ", 价格: " + dish.getPrice());
        }

        // 验证结果
        assert recommendedDishes != null;
        assert recommendedDishes.size() <= limit;
    }

    @Test
    public void testRecommendDishesWithTaboo() {
        // 测试包含忌口的情况
        String userInput = "我不喜欢吃辣，不要香菜和葱";
        int limit = 5;

        // 调用推荐服务
        List<DishInfo> recommendedDishes = recommendationService.recommendDishes(userInput, limit);

        // 打印推荐结果
        System.out.println("带忌口的推荐结果:");
        for (DishInfo dish : recommendedDishes) {
            System.out.println("菜品名称: " + dish.getDishName() + ", 价格: " + dish.getPrice());
        }

        // 验证结果
        assert recommendedDishes != null;
        assert recommendedDishes.size() <= limit;
    }
}