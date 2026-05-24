package com.SFood.admin.controller;

import com.SFood.common.dto.ResultDTO;
import com.SFood.system.service.NLPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * NLP控制器
 * 提供基于BERT的食物偏好提取功能
 */
@RestController
@RequestMapping("/nlp")
public class NLPController {
    
    @Autowired
    private NLPService nlpService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    
    /**
     * 提取食物偏好信息
     *
     * @param userInput 用户输入文本
     * @return 提取结果
     */
    @PostMapping("/extract-preferences")
    public ResultDTO extractFoodPreferences(@RequestBody Map<String, String> request) {
        try {
            String userInput = request.get("userInput");
            
            if (userInput == null || userInput.trim().isEmpty()) {
                return ResultDTO.error("用户输入不能为空");
            }
            
            Map<String, Object> result = nlpService.extractFoodPreferences(userInput);
            
            // 生成临时偏好ID
            String tempPreferenceId = "temp_preference_" + UUID.randomUUID().toString();
            
            // 保存到Redis，设置1小时过期时间
            redisTemplate.opsForValue().set(tempPreferenceId, result, 1, TimeUnit.HOURS);
            
            // 在返回结果中添加临时偏好ID
            result.put("tempPreferenceId", tempPreferenceId);
            
            return ResultDTO.success(result);
            
        } catch (Exception e) {
            return ResultDTO.error("偏好提取失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量提取食物偏好信息
     * @param request 包含用户输入列表的请求
     * @return 批量提取结果
     */
    @PostMapping("/extract-preferences-batch")
    public ResultDTO extractFoodPreferencesBatch(@RequestBody Map<String, String[]> request) {
        try {
            String[] userInputs = request.get("userInputs");
            
            if (userInputs == null || userInputs.length == 0) {
                return ResultDTO.error("用户输入列表不能为空");
            }
            
            Map<String, Object>[] results = nlpService.extractFoodPreferencesBatch(userInputs);
            
            // 生成批量临时偏好ID
            String batchTempPreferenceId = "batch_temp_preference_" + UUID.randomUUID().toString();
            
            // 保存到Redis，设置1小时过期时间
            redisTemplate.opsForValue().set(batchTempPreferenceId, results, 1, TimeUnit.HOURS);
            
            // 创建返回结果，包含批量结果和临时偏好ID
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("results", results);
            response.put("batchTempPreferenceId", batchTempPreferenceId);
            
            return ResultDTO.success(response);
            
        } catch (Exception e) {
            return ResultDTO.error("批量偏好提取失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据临时偏好ID获取偏好信息
     * @param tempPreferenceId 临时偏好ID
     * @return 偏好信息
     */
    @GetMapping("/temp-preference/{tempPreferenceId}")
    public ResultDTO getTempPreference(@PathVariable String tempPreferenceId) {
        try {
            if (tempPreferenceId == null || tempPreferenceId.trim().isEmpty()) {
                return ResultDTO.error("临时偏好ID不能为空");
            }
            
            // 从Redis获取临时偏好
            Object tempPreference = redisTemplate.opsForValue().get(tempPreferenceId);
            
            if (tempPreference == null) {
                return ResultDTO.error("临时偏好不存在或已过期");
            }
            
            return ResultDTO.success(tempPreference);
            
        } catch (Exception e) {
            return ResultDTO.error("获取临时偏好失败: " + e.getMessage());
        }
    }

    /**
     * 更新临时偏好（支持多轮对话）
     * @param request 包含临时偏好ID和新用户输入的请求
     * @return 更新后的偏好信息
     */
    @PostMapping("/update-temp-preference")
    public ResultDTO updateTempPreference(@RequestBody Map<String, String> request) {
        try {
            String tempPreferenceId = request.get("tempPreferenceId");
            String userInput = request.get("userInput");
            
            if (tempPreferenceId == null || tempPreferenceId.trim().isEmpty()) {
                return ResultDTO.error("临时偏好ID不能为空");
            }
            
            if (userInput == null || userInput.trim().isEmpty()) {
                return ResultDTO.error("用户输入不能为空");
            }
            
            // 从Redis获取现有临时偏好
            Object existingPreferenceObj = redisTemplate.opsForValue().get(tempPreferenceId);
            if (existingPreferenceObj == null) {
                return ResultDTO.error("临时偏好不存在或已过期");
            }
            
            if (!(existingPreferenceObj instanceof Map)) {
                return ResultDTO.error("临时偏好格式错误");
            }
            
            // 提取新的偏好
            Map<String, Object> newPreferences = nlpService.extractFoodPreferences(userInput);
            
            // 合并偏好
            Map<String, Object> existingPreference = (Map<String, Object>) existingPreferenceObj;
            Map<String, Object> mergedPreference = mergePreferences(existingPreference, newPreferences);
            
            // 保存更新后的偏好到Redis，重置过期时间
            redisTemplate.opsForValue().set(tempPreferenceId, mergedPreference, 1, java.util.concurrent.TimeUnit.HOURS);
            
            // 返回更新后的偏好
            mergedPreference.put("tempPreferenceId", tempPreferenceId);
            return ResultDTO.success(mergedPreference);
            
        } catch (Exception e) {
            return ResultDTO.error("更新临时偏好失败: " + e.getMessage());
        }
    }

    /**
     * 合并两个偏好对象
     * @param existing 现有偏好
     * @param newPref 新偏好
     * @return 合并后的偏好
     */
    private Map<String, Object> mergePreferences(Map<String, Object> existing, Map<String, Object> newPref) {
        Map<String, Object> merged = new java.util.HashMap<>(existing);
        
        // 合并口味偏好
        merged.put("tastes", mergeLists(existing.get("tastes"), newPref.get("tastes")));
        
        // 合并菜品偏好
        merged.put("dishes", mergeLists(existing.get("dishes"), newPref.get("dishes")));
        
        // 合并忌口
        merged.put("taboos", mergeLists(existing.get("taboos"), newPref.get("taboos")));
        
        return merged;
    }

    /**
     * 合并两个列表，去重
     * @param existingList 现有列表
     * @param newList 新列表
     * @return 合并后的列表
     */
    private java.util.List<String> mergeLists(Object existingListObj, Object newListObj) {
        java.util.Set<String> mergedSet = new java.util.HashSet<>();
        
        // 添加现有列表元素
        if (existingListObj instanceof java.util.List) {
            java.util.List<?> existingList = (java.util.List<?>) existingListObj;
            for (Object item : existingList) {
                if (item instanceof String) {
                    mergedSet.add((String) item);
                }
            }
        }
        
        // 添加新列表元素
        if (newListObj instanceof java.util.List) {
            java.util.List<?> newList = (java.util.List<?>) newListObj;
            for (Object item : newList) {
                if (item instanceof String) {
                    mergedSet.add((String) item);
                }
            }
        }
        
        return new java.util.ArrayList<>(mergedSet);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResultDTO<String> healthCheck() {
        return ResultDTO.success("NLP服务运行正常");
    }

}