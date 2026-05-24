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
 * 推荐控制器 — REST API 层。
 *
 * <p>提供三个推荐入口 + 一个临时偏好推荐入口，覆盖前端所有调用场景：
 * <ul>
 *   <li>/recommend-by-input — 根据用户输入文本推荐（面向普通用户）</li>
 *   <li>/recommend-by-preferences — 根据结构化偏好推荐（面向有偏好数据的场景）</li>
 *   <li>/recommend-by-userId — 根据已注册用户ID推荐（面向登录用户）</li>
 *   <li>/recommend-by-temp-preference — 根据Redis中缓存的临时偏好推荐（多轮对话场景）</li>
 * </ul>
 *
 * <p>所有接口统一返回 ResultDTO，分页参数默认 page=1, size=10。
 */
@RestController
@RequestMapping("/recommendation")
public class RecommendationController {

    /** 推荐服务：执行推荐算法 */
    @Autowired
    private RecommendationService recommendationService;

    /** Redis 模板：用于读取临时偏好缓存（temp-preference 接口使用） */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据用户输入文本推荐菜品。
     * 内部调用 NLP 提取偏好后走偏好推荐路径。
     *
     * 请求体示例：{"userInput":"我想吃辣的，不要香菜", "page":1, "size":10}
     */
    @PostMapping("/recommend-by-input")
    public ResultDTO recommendDishesByInput(@RequestBody Map<String, Object> request) {
        try {
            // 提取用户输入文本
            String userInput = (String) request.get("userInput");
            // 分页参数处理（兼容 Number 和 String 两种前端传参方式）
            int page = request.containsKey("page") ?
                (request.get("page") instanceof Number ? ((Number) request.get("page")).intValue() :
                Integer.parseInt(request.get("page").toString())) : 1;
            int size = request.containsKey("size") ?
                (request.get("size") instanceof Number ? ((Number) request.get("size")).intValue() :
                Integer.parseInt(request.get("size").toString())) : 10;

            if (userInput == null || userInput.trim().isEmpty()) {
                return ResultDTO.error("用户输入不能为空");
            }

            // 委托推荐服务处理
            Object result = recommendationService.recommendDishes(userInput, page, size);
            return ResultDTO.success(result);

        } catch (Exception e) {
            return ResultDTO.error("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 根据结构化偏好信息推荐菜品（不调NLP，直接使用传入的偏好）。
     *
     * 请求体示例：{"preferences":{"tastes":["辣"],"taboos":["香菜"],"dishes":[]}, "page":1, "size":10}
     */
    @PostMapping("/recommend-by-preferences")
    public ResultDTO recommendDishesByPreferences(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> preferences = (Map<String, Object>) request.get("preferences");
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
     * 根据用户ID推荐菜品。
     * 从 UserPreference 表读取持久化偏好，优先走协同过滤策略。
     *
     * 请求体示例：{"userId":1, "page":1, "size":10}
     */
    @PostMapping("/recommend-by-userId")
    public ResultDTO recommendDishesByUserId(@RequestBody Map<String, Object> request) {
        try {
            Object userIdObj = request.get("userId");
            Long userId = null;

            // 兼容前端可能传 Number 或 String 两种 userId 格式
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                try {
                    userId = Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    return ResultDTO.error("用户ID格式错误");
                }
            }
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
     * 根据临时偏好ID推荐菜品。
     * 临时偏好ID由 NLPController 在调用 /nlp/extract-preferences 时生成并保存到 Redis。
     * 此接口从Redis读取临时偏好后调用推荐服务。
     *
     * 请求体示例：{"tempPreferenceId":"temp_preference_xxx", "page":1, "size":10}
     */
    @PostMapping("/recommend-by-temp-preference")
    public ResultDTO recommendDishesByTempPreference(@RequestBody Map<String, Object> request) {
        try {
            String tempPreferenceId = (String) request.get("tempPreferenceId");
            int page = request.containsKey("page") ?
                (request.get("page") instanceof Number ? ((Number) request.get("page")).intValue() :
                Integer.parseInt(request.get("page").toString())) : 1;
            int size = request.containsKey("size") ?
                (request.get("size") instanceof Number ? ((Number) request.get("size")).intValue() :
                Integer.parseInt(request.get("size").toString())) : 10;

            if (tempPreferenceId == null || tempPreferenceId.trim().isEmpty()) {
                return ResultDTO.error("临时偏好ID不能为空");
            }

            // 从 Redis 读取缓存的临时偏好
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
     * 健康检查接口。
     * 用于监控推荐服务是否正常运行。
     */
    @PostMapping("/health")
    public ResultDTO<String> healthCheck() {
        return ResultDTO.success("推荐服务运行正常");
    }
}
