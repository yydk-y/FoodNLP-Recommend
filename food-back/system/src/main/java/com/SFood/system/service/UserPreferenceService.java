package com.SFood.system.service;

import com.SFood.system.entity.UserPreference;

import java.util.List;
import java.util.Map;

/**
 * 用户偏好服务接口
 */
public interface UserPreferenceService {
    
    /**
     * 获取用户偏好
     * @param userId 用户ID
     * @return 用户偏好信息
     */
    UserPreference getUserPreference(Long userId);
    
    /**
     * 创建或更新用户偏好
     * @param userPreference 用户偏好信息
     * @return 更新后的用户偏好信息
     */
    UserPreference saveOrUpdatePreference(UserPreference userPreference);
    
    /**
     * 更新口味偏好
     * @param userId 用户ID
     * @param taste 口味偏好（JSON数组）
     * @return 更新后的用户偏好信息
     */
    UserPreference updateTaste(Long userId, String taste);
    
    /**
     * 更新食材偏好
     * @param userId 用户ID
     * @param ingredient 食材偏好（JSON数组）
     * @return 更新后的用户偏好信息
     */
    UserPreference updateIngredient(Long userId, String ingredient);
    
    /**
     * 更新忌口
     * @param userId 用户ID
     * @param taboo 忌口（JSON数组）
     * @return 更新后的用户偏好信息
     */
    UserPreference updateTaboo(Long userId, String taboo);
    
    /**
     * 删除用户偏好
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deletePreference(Long userId);
    
    /**
     * 初始化用户偏好（如果不存在则创建）
     * @param userId 用户ID
     * @return 用户偏好信息
     */
    UserPreference initPreference(Long userId);
    
    /**
     * 根据用户近7天的订单信息动态更新用户偏好
     * @param userId 用户ID
     * @return 更新后的用户偏好信息
     */
    void updatePreferenceByRecentOrders(Long userId);
    
    /**
     * 获取用户的历史偏好
     * @param userId 用户ID
     * @return 用户历史偏好信息
     */
    UserPreference getHistoryPreference(Long userId);

    /**
     * 保存历史偏好记录
     * @param userId 用户ID
     * @return 是否保存成功
     */
    boolean saveHistoryPreference(Long userId);

    /**
     * 获取用户偏好变化历史
     * @param userId 用户ID
     * @param days 历史天数
     * @return 偏好变化历史列表
     */
    List<UserPreference> getPreferenceHistory(Long userId, int days);

    /**
     * 分析用户偏好变化趋势
     * @param userId 用户ID
     * @return 偏好变化趋势分析
     */
    Map<String, Object> analyzePreferenceTrend(Long userId);
}