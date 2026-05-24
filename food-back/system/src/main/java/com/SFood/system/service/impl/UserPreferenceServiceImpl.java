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

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    @Autowired
    private OrderMainMapper orderMainMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private DishInfoService dishInfoService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String USER_HISTORY_PREFERENCE_KEY = "user:history:preference:";
    private static final String USER_RECOMMENDATION_CACHE_PREFIX = "recommendation:user_id:";
    private static final String USER_PREFERENCE_RECOMMENDATION_CACHE_PREFIX = "recommendation:preferences:";
    private static final long HISTORY_PREFERENCE_EXPIRY = 30L * 24 * 60 * 60;

    @Override
    public UserPreference getUserPreference(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference preference = userPreferenceMapper.selectOne(wrapper);

        if (preference == null) {
            return initPreference(userId);
        }
        return preference;
    }

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
            existing.setTaste(userPreference.getTaste());
            existing.setIngredient(userPreference.getIngredient());
            existing.setTaboo(userPreference.getTaboo());
            existing.setUpdateTime(LocalDateTime.now());
            userPreferenceMapper.updateById(existing);
            invalidateRecommendationCaches(userId);
            return existing;
        }

        userPreference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.insert(userPreference);
        invalidateRecommendationCaches(userId);
        return userPreference;
    }

    @Override
    public UserPreference updateTaste(Long userId, String taste) {
        UserPreference preference = getUserPreference(userId);
        preference.setTaste(taste);
        preference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.updateById(preference);
        invalidateRecommendationCaches(userId);
        return preference;
    }

    @Override
    public UserPreference updateIngredient(Long userId, String ingredient) {
        UserPreference preference = getUserPreference(userId);
        preference.setIngredient(ingredient);
        preference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.updateById(preference);
        invalidateRecommendationCaches(userId);
        return preference;
    }

    @Override
    public UserPreference updateTaboo(Long userId, String taboo) {
        UserPreference preference = getUserPreference(userId);
        preference.setTaboo(taboo);
        preference.setUpdateTime(LocalDateTime.now());
        userPreferenceMapper.updateById(preference);
        invalidateRecommendationCaches(userId);
        return preference;
    }

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

    @Override
    public UserPreference initPreference(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setTaste("[]");
        preference.setIngredient("[]");
        preference.setTaboo("[]");
        preference.setUpdateTime(LocalDateTime.now());

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

    private void invalidateRecommendationCaches(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            Set<String> userRecKeys = redisTemplate.keys(USER_RECOMMENDATION_CACHE_PREFIX + userId + ":*");
            if (userRecKeys != null && !userRecKeys.isEmpty()) {
                redisTemplate.delete(userRecKeys);
            }

            Set<String> prefRecKeys = redisTemplate.keys(USER_PREFERENCE_RECOMMENDATION_CACHE_PREFIX + "*");
            if (prefRecKeys != null && !prefRecKeys.isEmpty()) {
                redisTemplate.delete(prefRecKeys);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveHistoryPreferenceCache(Long userId, List<String> preferredTastes, List<String> preferredDishes) {
        try {
            UserPreference historyPreference = new UserPreference();
            historyPreference.setUserId(userId);
            historyPreference.setTaste(String.join(",", preferredTastes));
            historyPreference.setIngredient(String.join(",", preferredDishes));
            historyPreference.setUpdateTime(LocalDateTime.now());

            String key = USER_HISTORY_PREFERENCE_KEY + userId;
            redisTemplate.opsForValue().set(key, historyPreference, HISTORY_PREFERENCE_EXPIRY, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public UserPreference getHistoryPreference(Long userId) {
        try {
            String key = USER_HISTORY_PREFERENCE_KEY + userId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }

            if (value instanceof UserPreference pref) {
                return pref;
            }

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
                if (map.containsKey("updateTime") && map.get("updateTime") instanceof String timeStr) {
                    try {
                        if (timeStr.contains("T")) {
                            preference.setUpdateTime(LocalDateTime.parse(timeStr));
                        } else if (timeStr.contains(" ")) {
                            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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

    @Override
    public boolean saveHistoryPreference(Long userId) {
        return false;
    }

    @Override
    public List<UserPreference> getPreferenceHistory(Long userId, int days) {
        return List.of();
    }

    @Override
    public Map<String, Object> analyzePreferenceTrend(Long userId) {
        return Map.of();
    }

    private List<OrderDetail> getUserRecentOrderDetails(Long userId) {
        LocalDateTime start = LocalDateTime.now().minusDays(7);

        LambdaQueryWrapper<OrderMain> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(OrderMain::getUserId, userId)
                .ge(OrderMain::getCreateTime, start)
                .select(OrderMain::getOrderId);
        List<OrderMain> orders = orderMainMapper.selectList(orderWrapper);
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = orders.stream().map(OrderMain::getOrderId).collect(Collectors.toList());

        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(OrderDetail::getOrderId, orderIds);
        List<OrderDetail> details = orderDetailMapper.selectList(detailWrapper);
        return details == null ? new ArrayList<>() : details;
    }

    @Override
    public void updatePreferenceByRecentOrders(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        List<OrderDetail> recentOrderDetails = getUserRecentOrderDetails(userId);
        if (recentOrderDetails.isEmpty()) {
            return;
        }

        Map<String, Integer> dishFrequency = new HashMap<>();
        Map<String, Integer> tasteFrequency = new HashMap<>();

        for (OrderDetail detail : recentOrderDetails) {
            String dishName = detail.getDishName();
            dishFrequency.put(dishName, dishFrequency.getOrDefault(dishName, 0) + detail.getNum());

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

        List<String> preferredDishes = dishFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= 1)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> preferredTastes = tasteFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= 1)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        saveHistoryPreferenceCache(userId, preferredTastes, preferredDishes);
        invalidateRecommendationCaches(userId);
    }
}
