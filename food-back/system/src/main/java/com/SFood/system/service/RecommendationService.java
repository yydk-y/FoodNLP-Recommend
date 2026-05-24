package com.SFood.system.service;

import com.SFood.system.entity.DishInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 推荐服务接口
 * 提供四种推荐入口，覆盖不同调用场景：
 * - 文本输入（临时、无状态）
 * - 结构化偏好（可跨轮累积）
 * - 已注册用户（持久化偏好 + 协同过滤）
 * - 富数据版本（debug / 解释生成使用）
 */
public interface RecommendationService {

    /**
     * 根据用户输入推荐菜品。
     * 内部调用 NLP 提取偏好后走偏好推荐路径。
     *
     * @param userInput 用户输入文本，如"推荐辣的，不要香菜"
     * @param page      页码
     * @param size      每页大小
     * @return Map{preferences, recommendation(Page), source, explanation}
     */
    Object recommendDishes(String userInput, int page, int size);

    /**
     * 根据结构化偏好信息推荐菜品。
     * 不调 NLP，直接使用传入的 tastes / taboos / dishes。
     *
     * @param preferences Map{ tastes, taboos, dishes }
     * @param page        页码
     * @param size        每页大小
     * @return Page&lt;DishInfo&gt;
     */
    Object recommendDishesByPreferences(Map<String, Object> preferences, int page, int size);

    /**
     * 根据结构化偏好推荐菜品（附带可解释数据）。
     * 返回额外元数据：normalizedPreferences、blockedByTaboo、matchedFeatures、scoreBreakdown。
     *
     * @param preferences Map{ tastes, taboos, dishes }
     * @param page        页码
     * @param size        每页大小
     * @return Map{recommendation(Page), normalizedPreferences, blockedByTaboo, matchedFeatures, scoreBreakdown}
     */
    Object recommendDishesByPreferencesWithExplain(Map<String, Object> preferences, int page, int size);

    /**
     * 根据用户 ID 推荐菜品。
     * 从 UserPreference 表读取持久化偏好，优先 UserCF 协同过滤，
     * 冷启动用户则使用热门 + 多样性推荐。
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param size   每页大小
     * @return Map{recommendation(Page/DTO), source}
     */
    Object recommendDishesByUserId(Long userId, int page, int size);
}