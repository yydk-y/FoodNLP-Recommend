package com.SFood.system.service.impl;

import com.SFood.system.entity.DishInfo;
import com.SFood.system.entity.OrderDetail;
import com.SFood.system.entity.OrderMain;
import com.SFood.system.entity.UserPreference;
import com.SFood.system.mapper.OrderDetailMapper;
import com.SFood.system.mapper.OrderMainMapper;
import com.SFood.system.mapper.UserPreferenceMapper;
import com.SFood.system.service.DishInfoService;
import com.SFood.system.service.UserPreferenceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户偏好服务实现。
 *
 * <p>管理 user_preference 表的CRUD操作，核心职责：
 * <ul>
 *   <li>为推荐系统提供用户偏好的数据源（口味/食材/忌口）</li>
 *   <li>偏好变更时自动清除推荐缓存，保证推荐实时性</li>
 *   <li>通过分析近7天订单动态更新偏好（历史偏好 → 推荐降权使用）</li>
 * </ul>
 *
 * <p>推荐系统中 {@link RecommendationServiceImpl#recommendDishesByUserId} 依赖此服务：
 * <ul>
 *   <li>getUserPreference → 读取当前偏好（高权重 1.0/0.9）</li>
 *   <li>getHistoryPreference → 读取历史偏好（低权重 0.4/0.3，防偏好突变）</li>
 * </ul>
 */
@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    // ==================== 注入的 Mapper 和 Service ====================

    /** 用户偏好 Mapper：操作 user_preference 表 */
    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    /** 订单主表 Mapper：查询用户近期的订单 */
    @Autowired
    private OrderMainMapper orderMainMapper;

    /** 订单详情 Mapper：查询订单中的具体菜品 */
    @Autowired
    private OrderDetailMapper orderDetailMapper;

    /** 菜品信息服务：根据 dishId 查菜品的口味等信息 */
    @Autowired
    private DishInfoService dishInfoService;

    /** Redis 模板：存储历史偏好缓存、失效推荐缓存 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    // ==================== Redis 缓存 Key 常量 ====================

    /** Redis key 前缀：用户历史偏好缓存（30天过期） */
    private static final String USER_HISTORY_PREFERENCE_KEY = "user:history:preference:";
    /** Redis key 前缀：推荐结果缓存（清除这些key使推荐实时更新） */
    private static final String USER_RECOMMENDATION_CACHE_PREFIX = "recommendation:user_id:";
    /** Redis key 前缀：偏好推荐结果缓存（同上） */
    private static final String USER_PREFERENCE_RECOMMENDATION_CACHE_PREFIX = "recommendation:preferences:";
    /** 历史偏好缓存过期时间：30天（秒） */
    private static final long HISTORY_PREFERENCE_EXPIRY = 30L * 24 * 60 * 60;


    // ========================================================================
    //  核心 CRUD
    // ========================================================================

    /**
     * 获取用户偏好。
     * 如果数据库中无记录，自动创建空偏好并返回。
     *
     * @param userId 用户ID
     * @return 用户偏好对象（含 taste/ingredient/taboo 字段）
     */
    @Override
    public UserPreference getUserPreference(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        // 按 userId 查 user_preference 表
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference preference = userPreferenceMapper.selectOne(wrapper);

        // 没有记录 → 自动初始化一个空的偏好记录
        if (preference == null) {
            return initPreference(userId);
        }
        return preference;
    }

    /**
     * 保存或更新用户偏好。
     * 已存在则覆盖三个字段（taste/ingredient/taboo）+ 更新 updateTime。
     * 不存在则插入新记录。
     * 更新后自动清除推荐缓存。
     */
    @Override
    public UserPreference saveOrUpdatePreference(UserPreference userPreference) {
        if (userPreference == null || userPreference.getUserId() == null) {
            throw new RuntimeException("用户偏好信息不完整");
        }

        Long userId = userPreference.getUserId();
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference existing = userPreferenceMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新现有记录
            existing.setTaste(userPreference.getTaste());
            existing.setIngredient(userPreference.getIngredient());
            existing.setTaboo(userPreference.getTaboo());
            existing.setUpdateTime(LocalDateTime.now());
            userPreferenceMapper.updateById(existing);
            invalidateRecommendationCaches(userId);  // 清除该用户的推荐缓存
            return existing;
        }

        // 插入新记录
        userPreference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.insert(userPreference);
        invalidateRecommendationCaches(userId);
        return userPreference;
    }

    /** 更新口味偏好 */
    @Override
    public UserPreference updateTaste(Long userId, String taste) {
        UserPreference preference = getUserPreference(userId);
        preference.setTaste(taste);
        preference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.updateById(preference);
        invalidateRecommendationCaches(userId);
        return preference;
    }

    /** 更新食材偏好 */
    @Override
    public UserPreference updateIngredient(Long userId, String ingredient) {
        UserPreference preference = getUserPreference(userId);
        preference.setIngredient(ingredient);
        preference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.updateById(preference);
        invalidateRecommendationCaches(userId);
        return preference;
    }

    /** 更新忌口 */
    @Override
    public UserPreference updateTaboo(Long userId, String taboo) {
        UserPreference preference = getUserPreference(userId);
        preference.setTaboo(taboo);
        preference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.updateById(preference);
        invalidateRecommendationCaches(userId);
        return preference;
    }

    /** 删除用户的所有偏好记录 */
    @Override
    public boolean deletePreference(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        int result = userPreferenceMapper.delete(wrapper);
        invalidateRecommendationCaches(userId);
        return result > 0;
    }


    // ========================================================================
    //  初始化
    // ========================================================================

    /**
     * 初始化用户偏好记录。
     * 如果已存在则直接返回，不存在则创建空偏好（三个字段均为 "[]"）。
     */
    @Override
    public UserPreference initPreference(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setTaste("[]");          // 空JSON数组
        preference.setIngredient("[]");
        preference.setTaboo("[]");
        preference.setUpdateTime(LocalDateTime.now());

        // 先检查是否已被其他线程创建（防重复）
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference existing = userPreferenceMapper.selectOne(wrapper);

        if (existing == null) {
            userPreferenceMapper.insert(preference);
            invalidateRecommendationCaches(userId);
            return preference;
        }
        return existing;
    }


    // ========================================================================
    //  缓存失效（偏好变更时清除推荐缓存）
    // ========================================================================

    /**
     * 清除该用户相关的所有推荐缓存。
     * 当用户修改偏好后，旧的推荐结果不再准确，必须清除以触发重新计算。
     *
     * 清除范围：
     * - recommendation:user_id:{userId}:* （用户ID推荐缓存）
     * - recommendation:preferences:* （偏好推荐缓存，不做用户隔离，全部清除）
     */
    private void invalidateRecommendationCaches(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            // 删除该用户的推荐缓存（精确匹配 userId）
            Set<String> userRecKeys = redisTemplate.keys(USER_RECOMMENDATION_CACHE_PREFIX + userId + ":*");
            if (userRecKeys != null && !userRecKeys.isEmpty()) {
                redisTemplate.delete(userRecKeys);
            }

            // 删除所有偏好推荐缓存（不做用户隔离，因为偏好组合可能跨用户）
            Set<String> prefRecKeys = redisTemplate.keys(USER_PREFERENCE_RECOMMENDATION_CACHE_PREFIX + "*");
            if (prefRecKeys != null && !prefRecKeys.isEmpty()) {
                redisTemplate.delete(prefRecKeys);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ========================================================================
    //  历史偏好管理
    // ========================================================================

    /**
     * 保存历史偏好到 Redis 缓存。
     * 历史偏好来自用户近7天的订单分析，用于推荐系统中的降权计算。
     * 缓存30天过期。
     *
     * @param preferredTastes  最常点的口味列表（前3）
     * @param preferredDishes  最常点的菜品名称列表（前5）
     */
    private void saveHistoryPreferenceCache(Long userId, List<String> preferredTastes, List<String> preferredDishes) {
        try {
            UserPreference historyPreference = new UserPreference();
            historyPreference.setUserId(userId);
            historyPreference.setTaste(String.join(",", preferredTastes));       // 逗号分隔口味
            historyPreference.setIngredient(String.join(",", preferredDishes));  // 逗号分隔菜品（复用ingredient字段存菜名）
            historyPreference.setUpdateTime(LocalDateTime.now());

            String key = USER_HISTORY_PREFERENCE_KEY + userId;
            redisTemplate.opsForValue().set(key, historyPreference, HISTORY_PREFERENCE_EXPIRY, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取用户的历史偏好（从 Redis 缓存读取）。
     * 兼容 Jackson 反序列化后的 LinkedHashMap 形态。
     *
     * @return UserPreference 对象，缓存不存在时返回 null
     */
    @Override
    public UserPreference getHistoryPreference(Long userId) {
        try {
            String key = USER_HISTORY_PREFERENCE_KEY + userId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }

            // 形态1：直接是 UserPreference 对象
            if (value instanceof UserPreference pref) {
                return pref;
            }

            // 形态2：LinkedHashMap（Jackson 反序列化默认行为）→ 手动映射
            if (value instanceof java.util.LinkedHashMap<?, ?> map) {
                UserPreference preference = new UserPreference();
                if (map.containsKey("userId")) {
                    preference.setUserId(((Number) map.get("userId")).longValue());
                }
                if (map.containsKey("taste")) {
                    preference.setTaste((String) map.get("taste"));
                }
                if (map.containsKey("ingredient")) {
                    preference.setIngredient((String) map.get("ingredient"));
                }
                // updateTime 可能是 LocalDateTime 或 String 两种格式，兼容处理
                if (map.containsKey("updateTime") && map.get("updateTime") instanceof String timeStr) {
                    try {
                        if (timeStr.contains("T")) {
                            preference.setUpdateTime(LocalDateTime.parse(timeStr));
                        } else if (timeStr.contains(" ")) {
                            java.time.format.DateTimeFormatter formatter =
                                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            preference.setUpdateTime(LocalDateTime.parse(timeStr, formatter));
                        }
                    } catch (Exception ignored) {
                    }
                }
                return preference;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    // ========================================================================
    //  基于订单的偏好自动更新
    // ========================================================================

    /**
     * 根据用户近7天的订单信息，自动分析偏好并更新历史偏好缓存。
     *
     * 分析逻辑：
     * 1. 查询用户近7天的所有订单
     * 2. 统计菜品出现频率（取前5）
     * 3. 统计口味出现频率（通过 dishInfo 表关联，取前3）
     * 4. 保存到 Redis 历史偏好缓存
     *
     * 推荐系统中，历史偏好用于 {@link RecommendationServiceImpl#calculateSimilarityWithPriority} 的降权计算。
     * 当前偏好权重 1.0/0.9，历史偏好权重 0.4/0.3。
     */
    @Override
    public void updatePreferenceByRecentOrders(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        // 查询近7天的订单
        List<OrderDetail> recentOrderDetails = getUserRecentOrderDetails(userId);
        if (recentOrderDetails.isEmpty()) {
            return;
        }

        // 统计菜品频率和口味频率
        Map<String, Integer> dishFrequency = new HashMap<>();   // 菜品名 → 点单次数
        Map<String, Integer> tasteFrequency = new HashMap<>();  // 口味名 → 出现次数

        for (OrderDetail detail : recentOrderDetails) {
            String dishName = detail.getDishName();
            dishFrequency.put(dishName, dishFrequency.getOrDefault(dishName, 0) + detail.getNum());

            // 通过 dishInfo 表查询该菜品的口味字段，累加口味出现频率
            try {
                DishInfo dishInfo = dishInfoService.getDishById(detail.getDishId());
                if (dishInfo != null && dishInfo.getTaste() != null) {
                    String[] tastes = dishInfo.getTaste().split(",");
                    for (String taste : tastes) {
                        taste = taste.trim();
                        if (!taste.isEmpty()) {
                            tasteFrequency.put(taste, tasteFrequency.getOrDefault(taste, 0) + detail.getNum());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 取点单次数前5的菜品
        List<String> preferredDishes = dishFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= 1)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 取出现次数前3的口味
        List<String> preferredTastes = tasteFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= 1)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 保存到 Redis 历史偏好缓存
        saveHistoryPreferenceCache(userId, preferredTastes, preferredDishes);
        invalidateRecommendationCaches(userId);
    }

    /**
     * 查询用户近7天的所有订单详情。
     * 先查 OrderMain 获取订单ID列表，再查 OrderDetail 获取具体菜品。
     */
    private List<OrderDetail> getUserRecentOrderDetails(Long userId) {
        LocalDateTime start = LocalDateTime.now().minusDays(7);

        // 查用户近7天的订单主表
        LambdaQueryWrapper<OrderMain> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(OrderMain::getUserId, userId)
                .ge(OrderMain::getCreateTime, start)
                .select(OrderMain::getOrderId);
        List<OrderMain> orders = orderMainMapper.selectList(orderWrapper);
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = orders.stream().map(OrderMain::getOrderId).collect(Collectors.toList());

        // 查这些订单的详情
        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(OrderDetail::getOrderId, orderIds);
        List<OrderDetail> details = orderDetailMapper.selectList(detailWrapper);
        return details == null ? new ArrayList<>() : details;
    }


    // ========================================================================
    //  未实现的方法（预留接口）
    // ========================================================================

    /** 保存历史偏好到数据库（暂未实现） */
    @Override
    public boolean saveHistoryPreference(Long userId) {
        return false;
    }

    /** 获取偏好变化历史（暂未实现） */
    @Override
    public List<UserPreference> getPreferenceHistory(Long userId, int days) {
        return List.of();
    }

    /** 分析偏好变化趋势（暂未实现） */
    @Override
    public Map<String, Object> analyzePreferenceTrend(Long userId) {
        return Map.of();
    }
}
