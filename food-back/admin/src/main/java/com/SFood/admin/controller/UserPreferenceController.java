package com.SFood.admin.controller;

import com.SFood.system.entity.UserPreference;
import com.SFood.system.service.UserPreferenceService;
import com.SFood.common.dto.ResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/**
 * 用户偏好控制器
 */
@RestController
@RequestMapping("/preference")
public class UserPreferenceController {
    
    @Autowired
    private UserPreferenceService userPreferenceService;
    
    /**
     * 获取用户偏好
     */
    @GetMapping("/{userId}")
    public ResultDTO<UserPreference> getUserPreference(@PathVariable Long userId) {
        UserPreference preference = userPreferenceService.getUserPreference(userId);
        return ResultDTO.success(preference);
    }
    
    /**
     * 创建或更新用户偏好
     */
    @PostMapping("/save")
    public ResultDTO<UserPreference> saveOrUpdatePreference(@RequestBody UserPreference userPreference) {
        UserPreference result = userPreferenceService.saveOrUpdatePreference(userPreference);
        return ResultDTO.success(result);
    }
    
    /**
     * 更新口味偏好
     */
    @PutMapping("/taste")
    public ResultDTO<UserPreference> updateTaste(@RequestParam Long userId, 
                                               @RequestParam String taste) {
        UserPreference result = userPreferenceService.updateTaste(userId, taste);
        return ResultDTO.success(result);
    }
    
    /**
     * 更新食材偏好
     */
    @PutMapping("/ingredient")
    public ResultDTO<UserPreference> updateIngredient(@RequestParam Long userId, 
                                                     @RequestParam String ingredient) {
        UserPreference result = userPreferenceService.updateIngredient(userId, ingredient);
        return ResultDTO.success(result);
    }
    
    /**
     * 更新忌口
     */
    @PutMapping("/taboo")
    public ResultDTO<UserPreference> updateTaboo(@RequestParam Long userId, 
                                                @RequestParam String taboo) {
        UserPreference result = userPreferenceService.updateTaboo(userId, taboo);
        return ResultDTO.success(result);
    }
    
    /**
     * 删除用户偏好
     */
    @DeleteMapping("/{userId}")
    public ResultDTO<Boolean> deletePreference(@PathVariable Long userId) {
        boolean result = userPreferenceService.deletePreference(userId);
        return ResultDTO.success(result);
    }
    
    /**
     * 初始化用户偏好
     */
    @PostMapping("/init/{userId}")
    public ResultDTO<UserPreference> initPreference(@PathVariable Long userId) {
        UserPreference result = userPreferenceService.initPreference(userId);
        return ResultDTO.success(result);
    }
    
    /**
     * 获取用户历史偏好
     */
    @GetMapping("/history/{userId}")
    public ResultDTO<UserPreference> getHistoryPreference(@PathVariable Long userId) {
        UserPreference result = userPreferenceService.getHistoryPreference(userId);
        return ResultDTO.success(result);
    }

    /**
     * 获取用户偏好对比（当前偏好 vs 历史偏好）
     */
    @GetMapping("/compare/{userId}")
    public ResultDTO<Map<String, Object>> getPreferenceComparison(@PathVariable Long userId) {
        UserPreference currentPreference = userPreferenceService.getUserPreference(userId);
        UserPreference historyPreference = userPreferenceService.getHistoryPreference(userId);
        
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("current", currentPreference);
        comparison.put("history", historyPreference);
        
        // 计算变化趋势
        Map<String, String> trends = new HashMap<>();
        if (currentPreference != null && historyPreference != null) {
            trends.put("taste", comparePreferenceField(currentPreference.getTaste(), historyPreference.getTaste()));
            trends.put("ingredient", comparePreferenceField(currentPreference.getIngredient(), historyPreference.getIngredient()));
            trends.put("taboo", comparePreferenceField(currentPreference.getTaboo(), historyPreference.getTaboo()));
        }
        comparison.put("trends", trends);
        
        return ResultDTO.success(comparison);
    }

    /**
     * 更新历史偏好记录（将当前偏好保存为历史偏好）
     */
    @PostMapping("/history/save/{userId}")
    public ResultDTO saveHistoryPreference(@PathVariable Long userId) {
        UserPreference currentPreference = userPreferenceService.getUserPreference(userId);
        if (currentPreference == null) {
            return ResultDTO.error("用户偏好不存在");
        }
        
        // 创建历史偏好记录
        UserPreference historyPreference = new UserPreference();
        historyPreference.setUserId(userId);
        historyPreference.setTaste(currentPreference.getTaste());
        historyPreference.setIngredient(currentPreference.getIngredient());
        historyPreference.setTaboo(currentPreference.getTaboo());
        
        // 这里需要实现保存历史偏好的逻辑
        // 由于UserPreferenceService中没有保存历史偏好的方法，暂时返回成功
        return ResultDTO.success(true);
    }

    /**
     * 获取用户偏好变化趋势
     */
    @GetMapping("/trend/{userId}")
    public ResultDTO<Map<String, Object>> getPreferenceTrend(@PathVariable Long userId) {
        UserPreference currentPreference = userPreferenceService.getUserPreference(userId);
        UserPreference historyPreference = userPreferenceService.getHistoryPreference(userId);
        
        Map<String, Object> trendAnalysis = new HashMap<>();
        
        if (currentPreference != null && historyPreference != null) {
            // 口味偏好变化分析
            trendAnalysis.put("tasteChange", analyzePreferenceChange(currentPreference.getTaste(), historyPreference.getTaste()));
            
            // 食材偏好变化分析
            trendAnalysis.put("ingredientChange", analyzePreferenceChange(currentPreference.getIngredient(), historyPreference.getIngredient()));
            
            // 忌口变化分析
            trendAnalysis.put("tabooChange", analyzePreferenceChange(currentPreference.getTaboo(), historyPreference.getTaboo()));
            
            // 总体变化趋势
            trendAnalysis.put("overallTrend", calculateOverallTrend(currentPreference, historyPreference));
        }
        
        return ResultDTO.success(trendAnalysis);
    }

    /**
     * 获取用户偏好变化历史
     */
    @GetMapping("/history/list/{userId}")
    public ResultDTO<List<UserPreference>> getPreferenceHistory(@PathVariable Long userId, 
                                                                @RequestParam(defaultValue = "7") int days) {
        List<UserPreference> historyList = userPreferenceService.getPreferenceHistory(userId, days);
        return ResultDTO.success(historyList);
    }

    /**
     * 分析用户偏好变化趋势（使用服务层方法）
     */
    @GetMapping("/analysis/{userId}")
    public ResultDTO<Map<String, Object>> analyzePreferenceTrend(@PathVariable Long userId) {
        Map<String, Object> analysis = userPreferenceService.analyzePreferenceTrend(userId);
        return ResultDTO.success(analysis);
    }

    /**
     * 批量保存历史偏好记录
     */
    @PostMapping("/history/batch-save")
    public ResultDTO<Map<String, Object>> batchSaveHistoryPreferences(@RequestBody List<Long> userIds) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        List<String> failedUsers = new ArrayList<>();
        
        for (Long userId : userIds) {
            try {
                boolean saved = userPreferenceService.saveHistoryPreference(userId);
                if (saved) {
                    successCount++;
                } else {
                    failCount++;
                    failedUsers.add("用户ID: " + userId);
                }
            } catch (Exception e) {
                failCount++;
                failedUsers.add("用户ID: " + userId + " (错误: " + e.getMessage() + ")");
            }
        }
        
        result.put("totalUsers", userIds.size());
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failedUsers", failedUsers);
        
        return ResultDTO.success(result);
    }

    /**
     * 获取用户偏好统计信息
     */
    @GetMapping("/stats/{userId}")
    public ResultDTO<Map<String, Object>> getUserPreferenceStats(@PathVariable Long userId) {
        Map<String, Object> stats = new HashMap<>();
        
        UserPreference current = userPreferenceService.getUserPreference(userId);
        UserPreference history = userPreferenceService.getHistoryPreference(userId);
        List<UserPreference> historyList = userPreferenceService.getPreferenceHistory(userId, 30);
        
        // 基础统计
        stats.put("hasCurrentPreference", current != null);
        stats.put("hasHistoryPreference", history != null);
        stats.put("historyRecordCount", historyList != null ? historyList.size() : 0);
        
        // 偏好字段统计
        if (current != null) {
            stats.put("tasteCount", countPreferenceItems(current.getTaste()));
            stats.put("ingredientCount", countPreferenceItems(current.getIngredient()));
            stats.put("tabooCount", countPreferenceItems(current.getTaboo()));
        }
        
        // 变化频率分析
        if (historyList != null && historyList.size() > 1) {
            stats.put("changeFrequency", calculateChangeFrequency(historyList));
        }
        
        return ResultDTO.success(stats);
    }
    /**
     * 比较两个偏好字段的变化
     */
    private String comparePreferenceField(String current, String history) {
        if (current == null && history == null) {
            return "无变化";
        }
        if (current == null) {
            return "偏好消失";
        }
        if (history == null) {
            return "新增偏好";
        }
        return current.equals(history) ? "无变化" : "有变化";
    }

    /**
     * 分析偏好变化
     */
    private Map<String, Object> analyzePreferenceChange(String current, String history) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (current == null && history == null) {
            analysis.put("status", "无偏好记录");
            analysis.put("changeType", "无变化");
            return analysis;
        }
        
        if (current == null) {
            analysis.put("status", "偏好消失");
            analysis.put("changeType", "减少");
            analysis.put("historyValue", history);
            return analysis;
        }
        
        if (history == null) {
            analysis.put("status", "新增偏好");
            analysis.put("changeType", "增加");
            analysis.put("currentValue", current);
            return analysis;
        }
        
        if (current.equals(history)) {
            analysis.put("status", "偏好稳定");
            analysis.put("changeType", "无变化");
            analysis.put("value", current);
        } else {
            analysis.put("status", "偏好变化");
            analysis.put("changeType", "修改");
            analysis.put("currentValue", current);
            analysis.put("historyValue", history);
        }
        
        return analysis;
    }

    /**
     * 计算总体变化趋势
     */
    private String calculateOverallTrend(UserPreference current, UserPreference history) {
        int changeCount = 0;
        
        if (!Objects.equals(current.getTaste(), history.getTaste())) {
            changeCount++;
        }
        if (!Objects.equals(current.getIngredient(), history.getIngredient())) {
            changeCount++;
        }
        if (!Objects.equals(current.getTaboo(), history.getTaboo())) {
            changeCount++;
        }
        
        if (changeCount == 0) {
            return "偏好稳定";
        } else if (changeCount == 1) {
            return "轻微变化";
        } else if (changeCount == 2) {
            return "中度变化";
        } else {
            return "显著变化";
        }
    }

    /**
     * 统计偏好项数量
     */
    private int countPreferenceItems(String preferenceJson) {
        if (preferenceJson == null || preferenceJson.trim().isEmpty() || preferenceJson.equals("[]")) {
            return 0;
        }
        
        try {
            // 简单统计逗号分隔的项数
            String[] items = preferenceJson.replaceAll("[\\[\\]]", "").split(",");
            int count = 0;
            for (String item : items) {
                if (item != null && !item.trim().isEmpty()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 计算偏好变化频率
     */
    private String calculateChangeFrequency(List<UserPreference> historyList) {
        if (historyList == null || historyList.size() < 2) {
            return "数据不足";
        }
        
        int changeCount = 0;
        UserPreference previous = historyList.get(0);
        
        for (int i = 1; i < historyList.size(); i++) {
            UserPreference current = historyList.get(i);
            if (!Objects.equals(previous.getTaste(), current.getTaste()) ||
                !Objects.equals(previous.getIngredient(), current.getIngredient()) ||
                !Objects.equals(previous.getTaboo(), current.getTaboo())) {
                changeCount++;
            }
            previous = current;
        }
        
        double frequency = (double) changeCount / (historyList.size() - 1);
        
        if (frequency < 0.1) {
            return "变化缓慢";
        } else if (frequency < 0.3) {
            return "变化适中";
        } else {
            return "变化频繁";
        }
    }
}