package com.SFood.system.service;

import com.SFood.system.entity.DishInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 推荐服务接口 — 定义推荐系统的四个入口。
 *
 * <p>覆盖三种调用场景：
 * <ul>
 *   <li>文本输入（临时、无状态）— 前端聊天框直接输入</li>
 *   <li>结构化偏好（可跨轮累积）— 多轮对话/后端直接传入偏好</li>
 *   <li>已注册用户（持久化偏好 + 协同过滤）— 登录用户的个性化推荐</li>
 * </ul>
 *
 * <p>富数据版本（WithExplain）额外返回 debug 信息：
 * normalizedPreferences、blockedByTaboo、matchedFeatures、scoreBreakdown。
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
     * 不调 NLP，直接使用传入的 tastes / taboos / dishes 三个字段。
     *
     * @param preferences Map{ tastes, taboos, dishes }
     * @param page        页码
     * @param size        每页大小
     * @return Page&lt;DishInfo&gt;
     */
    Object recommendDishesByPreferences(Map<String, Object> preferences, int page, int size);

    /**
     * 根据结构化偏好推荐菜品（附带可解释数据）。
     * 返回额外元数据用于前端展示：
     * - normalizedPreferences：归一化后的偏好
     * - blockedByTaboo：被忌口屏蔽了哪些菜
     * - matchedFeatures：每道推荐菜匹配了哪些特征
     * - scoreBreakdown：分数明细（口味分/菜品分/总相似度）
     *
     * @param preferences Map{ tastes, taboos, dishes }
     * @param page        页码
     * @param size        每页大小
     * @return Map{recommendation(Page), normalizedPreferences, blockedByTaboo, matchedFeatures, scoreBreakdown}
     */
    Object recommendDishesByPreferencesWithExplain(Map<String, Object> preferences, int page, int size);

    /**
     * 根据用户 ID 推荐菜品。
     * 从 UserPreference 表读取持久化偏好，采用三级策略：
     * 1. 协同过滤 UserCF（有足够评分数据时优先）
     * 2. 内容推荐（基于持久化偏好 + 历史偏好的加权余弦）
     * 3. 热门多样性推荐（冷启动用户回退）
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param size   每页大小
     * @return Map{recommendation(Page/DTO), source}
     */
    Object recommendDishesByUserId(Long userId, int page, int size);
}
