package com.SFood.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Map;

@SpringBootTest
public class NLPServiceRuleBasedTest {
    
    @Autowired
    private NLPService nlpService;
    
    @Test
    public void testExtractFoodPreferences() {
        String userInput = "我不喜欢吃辣，喜欢吃甜的，不要香菜";
        Map<String, Object> result = nlpService.extractFoodPreferences(userInput);
        
        System.out.println("原始输入: " + result.get("originalInput"));
        System.out.println("忌口: " + result.get("taboos"));
        System.out.println("口味偏好: " + result.get("tastes"));
        System.out.println("菜品: " + result.get("dishes"));
        
        // 验证结果
        assert result.containsKey("taboos");
        assert result.containsKey("tastes");
        assert result.containsKey("dishes");
        assert result.containsKey("originalInput");
    }
    
    @Test
    public void testExtractFoodPreferencesWithComplexInput() {
        String userInput = "我忌口海鲜和香菜，喜欢清淡的食物，不要辣";
        Map<String, Object> result = nlpService.extractFoodPreferences(userInput);
        
        System.out.println("原始输入: " + result.get("originalInput"));
        System.out.println("忌口: " + result.get("taboos"));
        System.out.println("口味偏好: " + result.get("tastes"));
        System.out.println("菜品: " + result.get("dishes"));
        
        // 验证结果
        assert result.containsKey("taboos");
        assert result.containsKey("tastes");
        assert result.containsKey("dishes");
        assert result.containsKey("originalInput");
    }
    
    @Test
    public void testExtractFoodPreferencesWithDish() {
        String userInput = "我喜欢吃宫保鸡丁，不要辣，喜欢甜";
        Map<String, Object> result = nlpService.extractFoodPreferences(userInput);
        
        System.out.println("原始输入: " + result.get("originalInput"));
        System.out.println("忌口: " + result.get("taboos"));
        System.out.println("口味偏好: " + result.get("tastes"));
        System.out.println("菜品: " + result.get("dishes"));
        
        // 验证结果
        assert result.containsKey("taboos");
        assert result.containsKey("tastes");
        assert result.containsKey("dishes");
        assert result.containsKey("originalInput");
    }
}