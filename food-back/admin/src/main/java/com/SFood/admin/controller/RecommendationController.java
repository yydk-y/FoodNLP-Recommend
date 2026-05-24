package com.SFood.admin.controller;

import com.SFood.common.dto.ResultDTO;
import com.SFood.system.entity.DishInfo;
import com.SFood.system.service.RecommendationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 推荐控制器
 * 提供菜品推荐功能
 */
@RestController
@RequestMapping("/recommendation")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据用户输入推荐菜品
     * @param request 包含用户输入和推荐数量的请求
     * @return 推荐结果
     */
    @PostMapping("/recommend-by-input")
    public ResultDTO recommendDishesByInput(@RequestBody Map<String, Object> request) {
        try {
            String userInput = (String) request.get("userInput");
            // 分页参数
            int page = request.containsKey("page") ? 
                (request.get("page") instanceof Number ? ((Number) request.get("page")).intValue() : 
                Integer.parseInt(request.get("page").toString())) : 1;
            int size = request.containsKey("size") ? 
                (request.get("size") instanceof Number ? ((Number) request.get("size")).intValue() : 
                Integer.parseInt(request.get("size").toString())) : 10;

            if (userInput == null || userInput.trim().isEmpty()) {
                return ResultDTO.error("用户输入不能为空");
            }

            Object result = recommendationService.recommendDishes(userInput, page, size);
            return ResultDTO.success(result);

        } catch (Exception e) {
            return ResultDTO.error("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户偏好信息推荐菜品
     * @param request 包含用户偏好和推荐数量的请求
     * @return 推荐结果
     */
    @PostMapping("/recommend-by-preferences")
    public ResultDTO recommendDishesByPreferences(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> preferences = (Map<String, Object>) request.get("preferences");
            // 分页参数
            int page = request.containsKey("page") ? 
                (request.get("page") instanceof Number ? ((Number) request.get("page")).intValue() : 
                Integer.parseInt(request.get("page").toString())) : 1;
            int size = request.containsKey("size") ? 
                (request.get("size") instanceof Number ? ((Number) request.get("size")).intValue() : 
                Integer.parseInt(request.get("size").toString())) :10;

            if (preferences == null) {
                return ResultDTO.error("用户偏好信息不能为空");
            }

            Object result = recommendationService.recommendDishesByPreferences(preferences, page, size);
            return ResultDTO.success(result);

        } catch (Exception e) {
            return ResultDTO.error("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID推荐菜品
     * @param request 包含用户ID和推荐数量的请求
     * @return 推荐结果
     */
    @PostMapping("/recommend-by-userId")
    public ResultDTO recommendDishesByUserId(@RequestBody Map<String, Object> request) {
        try {
            Object userIdObj = request.get("userId");
            Long userId = null;
            
            // 处理不同类型的userId
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                try {
                    userId = Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    return ResultDTO.error("用户ID格式错误");
                }
            }
            // 分页参数
            int page = request.containsKey("page") ? 
                (request.get("page") instanceof Number ? ((Number) request.get("page")).intValue() : 
                Integer.parseInt(request.get("page").toString())) : 1;
            int size = request.containsKey("size") ? 
                (request.get("size") instanceof Number ? ((Number) request.get("size")).intValue() : 
                Integer.parseInt(request.get("size").toString())) : 10;

            if (userId == null) {
                return ResultDTO.error("用户ID不能为空");
            }

            Object result = recommendationService.recommendDishesByUserId(userId, page, size);
            return ResultDTO.success(result);

        } catch (Exception e) {
            return ResultDTO.error("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 根据临时偏好ID推荐菜品
     * @param request 包含临时偏好ID和分页参数的请求
     * @return 推荐结果
     */
    @PostMapping("/recommend-by-temp-preference")
    public ResultDTO recommendDishesByTempPreference(@RequestBody Map<String, Object> request) {
        try {
            String tempPreferenceId = (String) request.get("tempPreferenceId");
            // 分页参数
            int page = request.containsKey("page") ? 
                (request.get("page") instanceof Number ? ((Number) request.get("page")).intValue() : 
                Integer.parseInt(request.get("page").toString())) : 1;
            int size = request.containsKey("size") ? 
                (request.get("size") instanceof Number ? ((Number) request.get("size")).intValue() : 
                Integer.parseInt(request.get("size").toString())) : 10;

            if (tempPreferenceId == null || tempPreferenceId.trim().isEmpty()) {
                return ResultDTO.error("临时偏好ID不能为空");
            }

            // 从Redis获取临时偏好
            Object tempPreferenceObj = redisTemplate.opsForValue().get(tempPreferenceId);
            if (tempPreferenceObj == null) {
                return ResultDTO.error("临时偏好不存在或已过期");
            }

            // 检查类型并调用推荐服务
            if (tempPreferenceObj instanceof Map) {
                Map<String, Object> preferences = (Map<String, Object>) tempPreferenceObj;
                Object result = recommendationService.recommendDishesByPreferences(preferences, page, size);
                return ResultDTO.success(result);
            } else {
                return ResultDTO.error("临时偏好格式错误");
            }

        } catch (Exception e) {
            return ResultDTO.error("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查接口
     */
    @PostMapping("/health")
    public ResultDTO<String> healthCheck() {
        return ResultDTO.success("推荐服务运行正常");
    }
}