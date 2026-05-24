package com.SFood.system.service;

import com.SFood.system.entity.UserPreference;

import java.util.List;
import java.util.Map;

/**
 * 用户偏好服务接口 — 管理已注册用户的口味、食材、忌口等持久化偏好。
 *
 * <p>偏好数据存储在 user_preference 表中，JSON 数组格式。
 * 推荐系统通过 {@link RecommendationServiceImpl#recommendDishesByUserId} 调用此服务读取偏好，
 * 为登录用户提供个性化推荐。
 *
 * <p>核心能力：
 * - CRUD 操作：查询/保存/更新/删除用户偏好
 * - 历史偏好管理：根据近7天订单动态更新偏好，保存历史偏好供降权使用
 * - 缓存失效：偏好变更时自动清除相关的推荐缓存，确保推荐结果实时更新
 */
public interface UserPreferenceService {

    /**
     * 获取用户偏好。
     * 如果用户尚未创建偏好记录，自动调用 initPreference 初始化。
     *
     * @param userId 用户ID
     * @return 用户偏好信息（含 taste/ingredient/taboo 三个字段）
     */
    UserPreference getUserPreference(Long userId);

    /**
     * 创建或更新用户偏好。
     * 已存在则更新（覆盖口味/食材/忌口三个字段），不存在则插入新记录。
     * 更新后自动清除推荐缓存。
     *
     * @param userPreference 用户偏好信息
     * @return 更新后的用户偏好信息
     */
    UserPreference saveOrUpdatePreference(UserPreference userPreference);

    /**
     * 更新口味偏好。
     *
     * @param userId 用户ID
     * @param taste 口味偏好（JSON数组格式，如"["辣","酸"]"）
     * @return 更新后的用户偏好信息
     */
    UserPreference updateTaste(Long userId, String taste);

    /**
     * 更新食材偏好。
     *
     * @param userId 用户ID
     * @param ingredient 食材偏好（JSON数组格式）
     * @return 更新后的用户偏好信息
     */
    UserPreference updateIngredient(Long userId, String ingredient);

    /**
     * 更新忌口偏好。
     *
     * @param userId 用户ID
     * @param taboo 忌口（JSON数组格式，如"["香菜","海鲜"]"）
     * @return 更新后的用户偏好信息
     */
    UserPreference updateTaboo(Long userId, String taboo);

    /**
     * 删除用户的所有偏好记录。
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deletePreference(Long userId);

    /**
     * 初始化用户偏好记录。
     * 如果数据库中不存在该用户偏好，创建一个空偏好记录（三个字段均为"[]"）。
     *
     * @param userId 用户ID
     * @return 用户偏好信息（新建或已存在的）
     */
    UserPreference initPreference(Long userId);

    /**
     * 根据用户近7天的订单信息，自动分析并更新用户的偏好。
     * 统计用户最常点的5道菜和最偏好的3种口味，保存到 Redis 历史偏好缓存。
     * 用于推荐系统中的"历史偏好降权"策略，避免偏好突变导致推荐跳变。
     *
     * @param userId 用户ID
     */
    void updatePreferenceByRecentOrders(Long userId);

    /**
     * 获取用户的历史偏好（从 Redis 缓存读取）。
     * 历史偏好由 updatePreferenceByRecentOrders 写入，用于 calculateSimilarityWithPriority 中的降权计算。
     *
     * @param userId 用户ID
     * @return 用户历史偏好信息（可能为 null）
     */
    UserPreference getHistoryPreference(Long userId);

    /**
     * 保存历史偏好记录到数据库（暂未实现）。
     */
    boolean saveHistoryPreference(Long userId);

    /**
     * 获取用户偏好变化历史（暂未实现）。
     */
    List<UserPreference> getPreferenceHistory(Long userId, int days);

    /**
     * 分析用户偏好变化趋势（暂未实现）。
     */
    Map<String, Object> analyzePreferenceTrend(Long userId);
}
