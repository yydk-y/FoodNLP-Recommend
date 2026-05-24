package com.SFood.system.service.impl;

import com.SFood.system.entity.DishInfo;
import com.SFood.system.entity.DishRating;
import com.SFood.system.entity.UserPreference;
import com.SFood.system.mapper.DishRatingMapper;
import com.SFood.system.service.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 推荐服务实现类
 * 基于BERT关键词和加权余弦向量实现菜品推荐
 *
 * <p>核心功能：
 * 1. 根据用户输入文本推荐菜品
 * 2. 根据用户偏好信息推荐菜品
 * 3. 根据用户ID自动查询偏好并推荐菜品
 * 4. 过滤掉包含用户忌口的菜品
 * 5. 基于加权余弦相似度计算推荐分数
 *
 * <p>权重分配：
 * - 口味：0.7（较高权重，用户选择菜品的主要因素）
 * - 菜品偏好：0.8（最高权重，直接匹配用户明确喜欢的菜品）
 * - 菜品名称：0.8（较高权重，名称匹配度高）
 * - 食材：0.3（较低权重，重要性次之）
 * - 描述：0.2（最低权重，作为辅助信息）
 *
 * <p>推荐流程：
 * 1. 提取用户偏好（口味、忌口、菜品偏好）
 * 2. 过滤掉下架的菜品
 * 3. 过滤掉包含用户忌口的菜品
 * 4. 计算每个菜品与用户偏好的加权相似度
 * 5. 按相似度排序，返回前N个推荐结果
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {
    /** 单次推荐返回的最大菜品数 */
    private static final int TOP_RECOMMENDATION_SIZE = 9;
    /** 推荐来源标识：协同过滤 */
    private static final String SOURCE_USER_CF = "USER_CF";
    /** 推荐来源标识：内容推荐 */
    private static final String SOURCE_CONTENT = "CONTENT_BASED";
    /** 推荐来源标识：热门+多样性 */
    private static final String SOURCE_POPULAR = "POPULAR_DIVERSIFIED";
    /** 协同过滤最小共同评分数量阈值 — 低于此值的用户不做 CF */
    private static final int CF_MIN_COMMON_RATINGS = 2;
    /** 协同过滤最多使用的相似邻居数量 */
    private static final int CF_TOP_K_NEIGHBORS = 20;
    /** 协同过滤显著性修正分母 — 越大对少量共同评分惩罚越强 */
    private static final int CF_SIGNIFICANCE_SHRINKAGE = 8;
    /** Redis key: 全量菜品缓存 */
    private static final String DISHES_ALL_CACHE_KEY = "dishes:all";
    /** Redis key 前缀: 用户输入的临时偏好 */
    private static final String USER_INPUT_TEMP_PREFERENCE_KEY_PREFIX = "recommendation:temp_preference:user_input:";
    /** 临时偏好缓存的 TTL（分钟） */
    private static final long TEMP_PREFERENCE_TTL_MINUTES = 30;
    /** 偏好文本的分隔符正则：中文顿号、逗号、斜杠、空白 */
    private static final Pattern TERM_SPLIT_PATTERN = Pattern.compile("[、,，/\\s]+");

    /** 同义词映射表 — 将口语化表达归一为标准口味/食材词 */
    private static final Map<String, List<String>> TERM_SYNONYMS = createSynonymMap();

    /** NLP服务，用于从用户输入中提取食物偏好信息 */
    @Autowired
    private NLPService nlpService;

    /** 菜品信息服务，用于获取菜品数据 */
    @Autowired
    private DishInfoService dishInfoService;

    /** 用户偏好服务，用于根据用户ID查询偏好信息 */
    @Autowired
    private UserPreferenceService userPreferenceService;

    /** 菜品评分服务，用于冷启动推荐 */
    @Autowired(required = false)
    private DishRatingService dishRatingService;

    /** 菜品评分Mapper，用于协同过滤 */
    @Autowired(required = false)
    private DishRatingMapper dishRatingMapper;

    /** 推荐解释服务，用于生成推荐原因 */
    @Autowired(required = false)
    private RecommendationExplanationService recommendationExplanationService;

    /** JSON解析器，用于解析JSON格式的偏好数据 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Redis模板，用于缓存推荐结果 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据用户输入推荐菜品
     *
     * @param userInput 用户输入文本，如"我喜欢辣的，不要香菜"
     * @return 推荐的菜品列表，按相似度排序
     */
    @Override
    public Object recommendDishes(String userInput, int page, int size) {
        String normalizedInput = userInput == null ? "" : userInput.trim();
        // 生成缓存键
        String cacheKey = "recommendation:v2:user_input:" + normalizedInput.toLowerCase() + ":" + page + ":" + size;
        
        // 尝试从Redis获取缓存的推荐结果
        try {
            Object cachedResponse = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResponse instanceof Map<?, ?> cachedMap) {
                if (!isRecommendationEmpty(cachedMap)) {
                    System.out.println("从Redis缓存获取推荐结果");
                    return cachedResponse;
                }
                // 旧缓存可能是空推荐，清理后重新计算，避免直接返回空列表
                redisTemplate.delete(cacheKey);
                System.out.println("命中空推荐缓存，已删除并重新计算推荐");
            }
        } catch (Exception e) {
            // Redis缓存失败，继续执行推荐逻辑
            System.out.println("Redis缓存读取失败: " + e.getMessage());
        }

        // 提取用户偏好
        Map<String, Object> preferences = new HashMap<>();
        if (!normalizedInput.isEmpty()) {
            try {
                Map<String, Object> extracted = nlpService.extractFoodPreferences(normalizedInput);
                if (extracted != null) {
                    preferences.putAll(extracted);
                }
            } catch (Exception e) {
                System.out.println("NLP偏好提取失败，使用冷启动推荐: " + e.getMessage());
            }
        }
        preferences = ensurePreferenceShape(preferences);

        // 打印提取的关键词，方便调试
        System.out.println("用户输入: " + normalizedInput);
        System.out.println("提取的口味偏好: " + preferences.get("tastes"));
        System.out.println("提取的忌口: " + preferences.get("taboos"));
        System.out.println("提取的菜品偏好: " + preferences.get("dishes"));

        // 根据偏好推荐菜品（按相似度固定返回前9个）
        boolean hasTemporaryPreferences = hasTemporaryPreferences(preferences);
        String tempPreferenceKey = USER_INPUT_TEMP_PREFERENCE_KEY_PREFIX + normalizedInput.toLowerCase();

        Object recommendationResult;
        boolean clearedTemporaryPreferences = false;

        if (hasTemporaryPreferences) {
            saveTempPreference(tempPreferenceKey, preferences);
            recommendationResult = recommendDishesByPreferences(preferences, 1, TOP_RECOMMENDATION_SIZE);

            // 临时偏好推荐为空：删除临时偏好和推荐缓存，回退冷启动推荐
            if (isRecommendationEmpty(recommendationResult)) {
                deleteTempPreference(tempPreferenceKey);
                deleteRecommendationCache(cacheKey);
                preferences = ensurePreferenceShape(new HashMap<>());
                recommendationResult = recommendPopularDishes(null, 1, TOP_RECOMMENDATION_SIZE);
                clearedTemporaryPreferences = true;
                System.out.println("临时偏好推荐为空，已删除临时偏好与推荐缓存，并回退到冷启动推荐");
            }
        } else {
            recommendationResult = recommendPopularDishes(null, 1, TOP_RECOMMENDATION_SIZE);
        }

        String recommendationSource = resolveInputRecommendationSource(hasTemporaryPreferences, clearedTemporaryPreferences);

        // 组装带解释的返回结果
        Map<String, Object> response = new HashMap<>();
        response.put("preferences", preferences);
        response.put("recommendation", recommendationResult);
        response.put("source", recommendationSource);

        if (recommendationResult instanceof Page<?> resultPage) {
            @SuppressWarnings("unchecked")
            Page<DishInfo> dishPage = (Page<DishInfo>) resultPage;
            List<DishInfo> recommendedDishes = dishPage.getRecords();
            String explanation = clearedTemporaryPreferences
                    ? "根据你本次输入提取到的临时偏好未匹配到可推荐菜品，已自动清空本次临时偏好，并为你切换到热门多样化推荐。"
                    : (hasTemporaryPreferences
                    ? buildRecommendationExplanation(normalizedInput, preferences, recommendedDishes)
                    : "你还没有提供明确偏好，先为你推荐当前热门且多样化的菜品。");
            response.put("explanation", explanation);
        } else {
            response.put("explanation", "推荐解释暂不可用。");
        }

        // 将推荐结果缓存到Redis，设置30分钟过期时间
        if (!clearedTemporaryPreferences) {
            cacheValue(cacheKey, response, 30, java.util.concurrent.TimeUnit.MINUTES, "推荐结果已缓存到Redis");
        }

        return response;
    }
    /**
     * 确保偏好 Map 包含三个标准的键，缺失时补空列表。
     * 避免后续代码频繁判空。
     */
    private Map<String, Object> ensurePreferenceShape(Map<String, Object> preferences) {
        Map<String, Object> normalized = preferences == null ? new HashMap<>() : new HashMap<>(preferences);
        normalized.computeIfAbsent("tastes", k -> new ArrayList<String>());
        normalized.computeIfAbsent("taboos", k -> new ArrayList<String>());
        normalized.computeIfAbsent("dishes", k -> new ArrayList<String>());
        return normalized;
    }

    /**
     * 判断用户是否提供了实质性偏好（口味、菜品、忌口至少一项非空）。
     * 用于区分"有明确需求"和"随便看看"两种场景。
     */
    private boolean hasTemporaryPreferences(Map<String, Object> preferences) {
        if (preferences == null) return false;

        List<String> tastes = (List<String>) preferences.getOrDefault("tastes", Collections.emptyList());
        List<String> dishes = (List<String>) preferences.getOrDefault("dishes", Collections.emptyList());
        List<String> taboos = (List<String>) preferences.getOrDefault("taboos", Collections.emptyList());

        boolean hasTaste = tastes.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
        boolean hasDish = dishes.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
        boolean hasTaboo = taboos.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
        return hasTaste || hasDish || hasTaboo;
    }

    /**
     * 检查推荐结果是否为空。
     * 能处理 Page、Map（含 records 键）、嵌套 Map（含 recommendation→Page）多种反序列化形态。
     */
    private boolean isRecommendationEmpty(Object recommendationResult) {
        if (recommendationResult == null) return true;

        if (recommendationResult instanceof Page<?> page) {
            return page.getRecords() == null || page.getRecords().isEmpty();
        }

        if (recommendationResult instanceof Map<?, ?> map) {
            // 直接是 Page 反序列化后的结构：{"records":[], ...}
            if (map.containsKey("records")) {
                Object records = map.get("records");
                return !(records instanceof Collection<?>) || ((Collection<?>) records).isEmpty();
            }
            // 包装结构：{"recommendation": Page/Map}
            Object inner = map.get("recommendation");
            if (inner instanceof Page<?> innerPage) {
                return innerPage.getRecords() == null || innerPage.getRecords().isEmpty();
            }
            if (inner instanceof Map<?, ?> innerMap && innerMap.containsKey("records")) {
                Object records = innerMap.get("records");
                return !(records instanceof Collection<?>) || ((Collection<?>) records).isEmpty();
            }
        }
        return false;
    }

    private String buildRecommendationExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        if (recommendationExplanationService == null) {
            return "已根据你提取到的口味、忌口和偏好菜品关键词完成推荐。";
        }
        return recommendationExplanationService.generateExplanation(userInput, preferences, recommendedDishes);
    }

    /**
     * 从 Redis 读取缓存。
     * - 空推荐缓存会被直接删除（避免反缓存）；
     * - 支持 expectedType 和 Map 两种形态（兼容序列化后的格式）。
     *
     * @param cacheKey     Redis key
     * @param expectedType 期望的 Java 类型
     * @param hitMessage   命中时打印的日志
     * @return 缓存的对象，未命中返回 null
     */
    private Object getCachedValue(String cacheKey, Class<?> expectedType, String hitMessage) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) return null;
            if (isRecommendationEmpty(cached)) {
                redisTemplate.delete(cacheKey);
                System.out.println("命中空推荐缓存，已删除: " + cacheKey);
                return null;
            }
            if (expectedType.isInstance(cached) || cached instanceof Map<?, ?>) {
                System.out.println(hitMessage);
                return cached;
            }
        } catch (Exception e) {
            System.out.println("Redis缓存读取失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 写入 Redis 缓存，空结果不写入（会删除旧的空缓存）。
     */
    private void cacheValue(String cacheKey, Object value, long timeout, java.util.concurrent.TimeUnit unit, String successMessage) {
        if (isRecommendationEmpty(value)) {
            try { redisTemplate.delete(cacheKey); } catch (Exception e) { System.out.println("Redis空缓存删除失败: " + e.getMessage()); }
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, value, timeout, unit);
            System.out.println(successMessage);
        } catch (Exception e) {
            System.out.println("Redis缓存写入失败: " + e.getMessage());
        }
    }

    /** 保存用户输入的临时偏好到 Redis，设置固定 TTL */
    private void saveTempPreference(String tempPreferenceKey, Map<String, Object> preferences) {
        try {
            redisTemplate.opsForValue().set(tempPreferenceKey, preferences, TEMP_PREFERENCE_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("临时偏好缓存写入失败: " + e.getMessage());
        }
    }

    /** 删除 Redis 中的临时偏好 */
    private void deleteTempPreference(String tempPreferenceKey) {
        try { redisTemplate.delete(tempPreferenceKey); } catch (Exception e) { System.out.println("临时偏好缓存删除失败: " + e.getMessage()); }
    }

    /** 删除 Redis 中的推荐结果缓存 */
    private void deleteRecommendationCache(String cacheKey) {
        try { redisTemplate.delete(cacheKey); } catch (Exception e) { System.out.println("推荐缓存删除失败: " + e.getMessage()); }
    }

    /**
     * 从 Redis 或 DB 获取全量菜品列表（含已下架）。
     * Redis TTL 1 小时，对菜品增删不敏感的业务适用。
     */
    private List<DishInfo> getAllDishesWithCache() {
        try {
            Object cachedDishes = redisTemplate.opsForValue().get(DISHES_ALL_CACHE_KEY);
            if (cachedDishes instanceof List<?> cachedList) {
                List<DishInfo> dishes = convertCachedDishList(cachedList);
                if (dishes != null) {
                    System.out.println("从Redis缓存获取菜品列表");
                    return dishes;
                }
            }
        } catch (Exception e) {
            System.out.println("Redis缓存读取失败: " + e.getMessage());
        }
        List<DishInfo> dishes = dishInfoService.getAllDishes();
        cacheValue(DISHES_ALL_CACHE_KEY, dishes, 1, java.util.concurrent.TimeUnit.HOURS, "菜品列表已缓存到Redis");
        return dishes;
    }

    /**
     * 转换 Redis 反序列化的菜品列表。
     * Jackson 序列化后可能是 DishInfo 对象或 LinkedHashMap，两种形式都处理。
     */
    @SuppressWarnings("unchecked")
    private List<DishInfo> convertCachedDishList(List<?> cachedList) {
        if (cachedList.isEmpty()) return new ArrayList<>();
        Object first = cachedList.get(0);
        if (first instanceof DishInfo) {
            return cachedList.stream()
                    .filter(DishInfo.class::isInstance)
                    .map(DishInfo.class::cast)
                    .collect(Collectors.toList());
        }
        if (first instanceof LinkedHashMap<?, ?>) {
            return cachedList.stream()
                    .filter(LinkedHashMap.class::isInstance)
                    .map(item -> mapToDishInfo((LinkedHashMap<?, ?>) item))
                    .collect(Collectors.toList());
        }
        return null;
    }

    /**
     * 将 LinkedHashMap 反序列化为 DishInfo 实体。
     * 兼容 Jackson 默认序列化（字段名称按 Java 属性命名规则）。
     */
    private DishInfo mapToDishInfo(LinkedHashMap<?, ?> map) {
        DishInfo dishInfo = new DishInfo();
        dishInfo.setDishId(numberToLong(map.get("dishId")));
        dishInfo.setDishName(toStringOrNull(map.get("dishName")));
        dishInfo.setIngredient(toStringOrNull(map.get("ingredient")));
        dishInfo.setTaste(toStringOrNull(map.get("taste")));
        dishInfo.setDescription(toStringOrNull(map.get("description")));
        dishInfo.setImageUrl(toStringOrNull(map.get("imageUrl")));
        dishInfo.setPrice(numberToBigDecimal(map.get("price")));
        dishInfo.setStatus(numberToInteger(map.get("status")));
        dishInfo.setHeat(numberToInteger(map.get("heat")));
        dishInfo.setCategoryId(numberToLong(map.get("categoryId")));
        return dishInfo;
    }

    private String toStringOrNull(Object value) { return value == null ? null : value.toString(); }
    private Long numberToLong(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private Integer numberToInteger(Object value) { return value instanceof Number n ? n.intValue() : null; }
    private BigDecimal numberToBigDecimal(Object value) { return value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null; }

    /** 获取所有上架的可用菜品（status=1） */
    private List<DishInfo> getAvailableDishes() {
        return getAllDishesWithCache().stream()
                .filter(dish -> Objects.equals(dish.getStatus(), 1))
                .collect(Collectors.toList());
    }

    /**
     * 将菜品列表按 page/size 截断为分页对象。
     * 不会重新查库，纯内存切片。
     */
    private Page<DishInfo> toPage(List<DishInfo> dishes, int page, int size) {
        int total = dishes.size();
        int safeSize = Math.max(size, 1);
        int start = Math.max(0, (Math.max(page, 1) - 1) * safeSize);
        int end = Math.min(start + safeSize, total);
        List<DishInfo> pageDishes = start < total ? new ArrayList<>(dishes.subList(start, end)) : new ArrayList<>();
        Page<DishInfo> dishPage = new Page<>(page, size);
        dishPage.setRecords(pageDishes);
        dishPage.setTotal(total);
        dishPage.setPages((total + safeSize - 1) / safeSize);
        return dishPage;
    }

    /**
     * 提取偏好列表中指定键的值，做归一化（同义词展开）后返回。
     * 用于匹配计算，会扩大搜索范围。
     */
    private List<String> getPreferenceList(Map<String, Object> preferences, String key) {
        Object value = preferences == null ? null : preferences.get(key);
        if (value instanceof List<?> list) return normalizeTerms(list);
        if (value instanceof String text) return parseStringList(text);
        return new ArrayList<>();
    }

    /**
     * 提取原始偏好值，不做同义词展开。
     * 用于生成缓存 key 和前端展示。
     */
    private List<String> getRawPreferenceList(Map<String, Object> preferences, String key) {
        Object value = preferences == null ? null : preferences.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    String s = o.toString().trim();
                    if (!s.isEmpty()) result.add(s);
                }
            }
            return result;
        }
        if (value instanceof String text) return parseStringListRaw(text);
        return new ArrayList<>();
    }

    /**
     * 解析 JSON 数组字符串 → List&lt;String&gt;，失败时按分隔符拆分。
     * 结果做同义词归一化。
     */
    private List<String> parseStringList(String text) {
        if (text == null || text.trim().isEmpty() || "[]".equals(text.trim())) return new ArrayList<>();
        try {
            return normalizeTerms(objectMapper.readValue(text, List.class));
        } catch (Exception ignored) {
            return normalizeTerms(Arrays.asList(TERM_SPLIT_PATTERN.split(text)));
        }
    }

    /**
     * 解析 JSON 数组字符串，保持原始值（不做同义词展开）。
     */
    private List<String> parseStringListRaw(String text) {
        if (text == null || text.trim().isEmpty() || "[]".equals(text.trim())) return new ArrayList<>();
        try {
            List<Object> raw = objectMapper.readValue(text, List.class);
            List<String> result = new ArrayList<>();
            for (Object o : raw) {
                if (o != null) { String s = o.toString().trim(); if (!s.isEmpty()) result.add(s); }
            }
            return result;
        } catch (Exception ignored) {
            return Arrays.stream(TERM_SPLIT_PATTERN.split(text))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
    }

    /**
     * 对术语列表做归一化：去重 + 同义词展开。
     * 如 "辣" → "辣" + "麻辣" + "香辣" + "辣椒" 等。
     */
    private List<String> normalizeTerms(Collection<?> terms) {
        if (terms == null) return new ArrayList<>();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object termObj : terms) {
            if (termObj == null) continue;
            String term = termObj.toString().trim();
            if (term.isEmpty()) continue;
            normalized.add(term);
            normalized.addAll(expandSynonyms(term));
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 同义词展开。
     * 如果 term 匹配同义词映射的 key 或任一别名，返回完整集合（key + 所有别名）。
     * 否则返回原始 term。
     */
    private List<String> expandSynonyms(String term) {
        if (term == null) return Collections.emptyList();
        String normalized = term.trim();
        if (normalized.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        expanded.add(normalized);

        for (Map.Entry<String, List<String>> entry : TERM_SYNONYMS.entrySet()) {
            String canonical = entry.getKey();
            List<String> aliases = entry.getValue();
            if (canonical.equals(normalized) || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized))) {
                expanded.add(canonical);
                expanded.addAll(aliases);
            }
        }
        return new ArrayList<>(expanded);
    }

    /**
     * 同义词映射表。
     * key = 标准口味/食材，value = 所有口语化别名。
     * 用于：偏好提取后的归一化匹配、忌口检测、缓存 key 生成。
     */
    private static Map<String, List<String>> createSynonymMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("酸", Arrays.asList("酸味", "酸口", "偏酸", "酸一点", "酸爽", "酸辣", "酸甜", "醋", "陈醋", "香醋", "米醋", "糖醋", "泡菜", "酸菜", "番茄", "柠檬", "山楂", "话梅", "梅子"));
        map.put("甜", Arrays.asList("甜味", "甜口", "偏甜", "甜一点", "甜香", "香甜", "酸甜", "糖醋", "蜜汁", "焦糖", "红糖", "拔丝", "糖", "蜂蜜", "冰糖", "桂花"));
        map.put("海鲜", Arrays.asList("海产", "虾", "虾仁", "蟹", "鱼", "贝", "扇贝", "蛤蜊", "鱿鱼", "章鱼", "海带"));
        map.put("清淡", Arrays.asList("少油", "不油", "低脂", "清爽"));
        map.put("苦", Arrays.asList("苦味", "苦口", "偏苦", "苦一点", "微苦", "清苦", "苦瓜", "苦菊", "莲子心", "陈皮"));
        map.put("辣", Arrays.asList("辣味", "辣口", "偏辣", "辣一点", "麻辣", "香辣", "酸辣", "微辣", "中辣", "重辣", "特辣", "变态辣", "椒麻", "剁椒", "泡椒", "辣椒", "小米椒", "青椒", "红椒", "花椒", "胡椒"));
        map.put("咸", Arrays.asList("咸味", "咸口", "偏咸", "咸一点", "鲜咸", "咸鲜", "酱香", "酱爆", "酱烧", "酱油", "豆瓣酱", "黄豆酱", "腊味", "腌", "盐焗", "咸蛋黄", "咸菜"));
        return map;
    }

    /** 判断术语集合是否包含非空条目 */
    private boolean hasAnyTerm(Collection<String> terms) {
        return terms != null && terms.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
    }

    /** 按热度降序排序 */
    private List<DishInfo> sortedByHeat(List<DishInfo> dishes) {
        return dishes.stream()
                .sorted(Comparator.comparingInt((DishInfo dish) -> dish.getHeat() == null ? 0 : dish.getHeat()).reversed())
                .collect(Collectors.toList());
    }

    /** 从 菜品↔分数 条目列表中提取菜品列表 */
    private List<DishInfo> dishesFromScores(List<Map.Entry<DishInfo, Double>> scores) {
        return scores.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    /**
     * 将一组术语以指定权重加入用户特征向量。
     * 每个术语加上 prefix 前缀作为键，weight 作为值。
     * 用于构建 UserCF 中用户偏好特征。
     */
    private void addWeightedFeatures(Map<String, Double> features, String prefix, Collection<?> values, double weight) {
        for (String value : normalizeTerms(values)) {
            features.put(prefix + value, weight);
        }
    }

    /**
     * 构建菜品的特征向量。
     * 包含四个维度，各维度权重不同：
     * - 菜名 (0.8)
     * - 口味 (0.8)
     * - 食材 (0.65)
     * - 描述 (0.25)
     */
    private Map<String, Double> buildDishFeatures(DishInfo dish) {
        Map<String, Double> dishFeatures = new HashMap<>();
        addRawFeature(dishFeatures, "dish_", dish.getDishName(), 0.8);
        addRawFeatures(dishFeatures, "taste_", parseStringListRaw(dish.getTaste()), 0.8);
        addRawFeatures(dishFeatures, "ingredient_", parseStringListRaw(dish.getIngredient()), 0.65);
        addRawFeatures(dishFeatures, "desc_", dish.getDescription() == null ? Collections.emptyList() : Arrays.asList(TERM_SPLIT_PATTERN.split(dish.getDescription())), 0.25);
        return dishFeatures;
    }

    /** 批量添加特征（集合版） */
    private void addRawFeatures(Map<String, Double> features, String prefix, Collection<?> values, double weight) {
        for (Object v : values) {
            if (v == null) continue;
            String term = v.toString().trim();
            if (term.isEmpty()) continue;
            features.put(prefix + term, weight);
        }
    }

    /** 添加单个特征 */
    private void addRawFeature(Map<String, Double> features, String prefix, String value, double weight) {
        if (value != null && !value.trim().isEmpty()) {
            features.put(prefix + value.trim(), weight);
        }
    }

    private boolean containsSeafoodToken(String text) {
        if (text == null || text.isEmpty()) return false;
        return TERM_SYNONYMS.getOrDefault("海鲜", Collections.emptyList()).stream().anyMatch(text::contains) || text.contains("海鲜");
    }

    private void addDetectedTasteFeatures(Map<String, Double> dishFeatures, String searchable) {
        addDetectedTasteFeature(dishFeatures, searchable, "酸");
        addDetectedTasteFeature(dishFeatures, searchable, "甜");
        addDetectedTasteFeature(dishFeatures, searchable, "苦");
        addDetectedTasteFeature(dishFeatures, searchable, "辣");
        addDetectedTasteFeature(dishFeatures, searchable, "咸");
    }

    private void addDetectedTasteFeature(Map<String, Double> dishFeatures, String searchable, String taste) {
        if (containsTasteToken(searchable, taste)) {
            dishFeatures.put("taste_" + taste, 0.9);
        }
    }

    private boolean containsTasteToken(String text, String taste) {
        if (text == null || text.isEmpty()) return false;
        return TERM_SYNONYMS.getOrDefault(taste, Collections.emptyList()).stream().anyMatch(text::contains) || text.contains(taste);
    }
    private boolean hasNonEmptyPreference(String value) {
        return value != null && !parseStringList(value).isEmpty();
    }


    /**
     * 根据用户偏好信息推荐菜品
     *
     * @param preferences 用户偏好信息，包含口味、忌口、菜品偏好等
     * @param page 页码
     * @param size 每页大小
     * @return 推荐的菜品列表或分页结果
     */
    @Override
    public Object recommendDishesByPreferences(Map<String, Object> preferences, int page, int size) {
        Object detailed = recommendDishesByPreferencesWithExplain(preferences, page, size);
        if (detailed instanceof Map<?, ?> map && map.containsKey("recommendation")) {
            return map.get("recommendation");
        }
        return detailed;
    }

    @Override
    public Object recommendDishesByPreferencesWithExplain(Map<String, Object> preferences, int page, int size) {
        preferences = ensurePreferenceShape(preferences);
        String cacheKey = generatePreferencesCacheKey(preferences, page, size);

        Object cachedResult = getCachedValue(cacheKey, Page.class, "从Redis缓存获取偏好推荐结果");
        Page<DishInfo> dishPage;

        System.out.println("提取的口味偏好: " + preferences.get("tastes"));
        System.out.println("提取的忌口: " + preferences.get("taboos"));
        System.out.println("提取的菜品偏好: " + preferences.get("dishes"));

        List<String> tastes = getPreferenceList(preferences, "tastes");
        List<String> taboos = getPreferenceList(preferences, "taboos");
        List<String> preferredDishes = getPreferenceList(preferences, "dishes");
        tastes = removeConflictingTastes(tastes, taboos);
        List<String> rawTastes = getRawPreferenceList(preferences, "tastes");
        List<String> rawPreferredDishes = getRawPreferenceList(preferences, "dishes");

        List<DishInfo> availableDishes = getAvailableDishes();
        List<DishInfo> filteredDishes = filterTabooDishes(availableDishes, taboos);

        if (cachedResult instanceof Page<?> cachedPage) {
            @SuppressWarnings("unchecked")
            Page<DishInfo> casted = (Page<DishInfo>) cachedPage;
            dishPage = casted;
        } else {
            if (!hasAnyTerm(tastes) && !hasAnyTerm(preferredDishes)) {
                dishPage = toPage(sortedByHeat(filteredDishes), page, size);
            } else {
                List<Map.Entry<DishInfo, Double>> filteredScores = calculateSimilarities(filteredDishes, rawTastes, rawPreferredDishes).stream()
                        .filter(entry -> entry.getValue() >= 0.25)
                        .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())
                        .collect(Collectors.toList());
                // 多口味关键词时，确保每个口味都有代表
                // 扩大交错池，减小 injectExploreDishes 把某口味菜品全替换的概率
                final int interleaveSize = Math.max(size * 2, rawTastes.size() * 3);
                if (rawTastes.size() > 1) {
                    filteredScores = interleaveByTaste(filteredScores, filteredDishes, rawTastes, interleaveSize);
                }
                for (Map.Entry<DishInfo, Double> entry : filteredScores) {
                    entry.getKey().setMatchScore((double) Math.round(entry.getValue() * 100.0));
                }
                dishPage = toPage(dishesFromScores(filteredScores), page, size);
            }
            // 混入 30% 跨类别随机菜品作为探索推荐
            dishPage = injectExploreDishes(dishPage, filteredDishes, tastes, page, size);
            cacheValue(cacheKey, dishPage, 30, java.util.concurrent.TimeUnit.MINUTES, "偏好推荐结果已缓存到Redis");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("recommendation", dishPage);

        Map<String, Object> normalized = new HashMap<>();
        normalized.put("tastes", tastes);
        normalized.put("taboos", taboos);
        normalized.put("dishes", preferredDishes);
        detail.put("normalizedPreferences", normalized);
        detail.put("blockedByTaboo", collectBlockedByTaboo(availableDishes, taboos, 20));
        detail.put("matchedFeatures", buildMatchedFeatures(dishPage.getRecords(), tastes, preferredDishes));
        detail.put("scoreBreakdown", buildScoreBreakdown(dishPage.getRecords(), tastes, preferredDishes));

        return detail;
    }

    /**
     * 生成偏好推荐的缓存键
     * @param preferences 用户偏好信息
     * @param page 页码
     * @param size 每页大小
     * @return 缓存键
     */
    private String generatePreferencesCacheKey(Map<String, Object> preferences, int page, int size) {
        List<String> tastes = (List<String>) preferences.getOrDefault("tastes", new ArrayList<>());
        List<String> taboos = (List<String>) preferences.getOrDefault("taboos", new ArrayList<>());
        List<String> preferredDishes = (List<String>) preferences.getOrDefault("dishes", new ArrayList<>());
        
        // 对列表进行排序，确保相同内容生成相同的缓存键
        List<String> sortedTastes = tastes.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toList());
        List<String> sortedTaboos = taboos.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toList());
        List<String> sortedPreferredDishes = preferredDishes.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toList());
        
        Collections.sort(sortedTastes);
        Collections.sort(sortedTaboos);
        Collections.sort(sortedPreferredDishes);

        return "recommendation:v2:" + sortedTastes + ":" + sortedTaboos + ":" + sortedPreferredDishes + ":" + page + ":" + size;
    }

    /**
     * 过滤掉包含忌口的菜品
     *
     * @param dishes 菜品列表
     * @param taboos 忌口列表
     * @return 过滤后的菜品列表
     */
    private List<DishInfo> filterTabooDishes(List<DishInfo> dishes, List<String> taboos) {
        // 如果忌口列表为空，直接返回所有菜品
        if (taboos == null || taboos.isEmpty()) {
            return dishes;
        }

        // 过滤掉包含忌口的菜品
        return dishes.stream()
                .filter(dish -> !containsTaboo(dish, taboos))
                .collect(Collectors.toList());
    }

    /**
     * 检查菜品是否包含忌口
     *
     * @param dish 菜品信息
     * @param taboos 忌口列表
     * @return 如果包含忌口返回true，否则返回false
     */
    private boolean containsTaboo(DishInfo dish, List<String> taboos) {
        if (dish == null || taboos == null || taboos.isEmpty()) {
            return false;
        }

        List<String> normalizedTaboos = normalizeTerms(taboos).stream()
                .flatMap(item -> expandSynonyms(item).stream())
                .distinct()
                .collect(Collectors.toList());
        if (normalizedTaboos.isEmpty()) {
            return false;
        }

        String searchable = (safeLower(dish.getDishName()) + " "
                + safeLower(dish.getIngredient()) + " "
                + safeLower(dish.getTaste()) + " "
                + safeLower(dish.getDescription())).trim();
        for (String taboo : normalizedTaboos) {
            if (searchable.contains(taboo.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        Set<String> ingredientSet = new HashSet<>(parseStringList(dish.getIngredient()));
        if (containsSeafoodToken(searchable)) {
            ingredientSet.add("海鲜");
        }
        addDetectedTasteTerms(ingredientSet, searchable);
        for (String taboo : normalizedTaboos) {
            if (ingredientSet.contains(taboo)) {
                return true;
            }
        }

        return false;
    }


    private void addDetectedTasteTerms(Set<String> terms, String searchable) {
        addDetectedTasteTerm(terms, searchable, "酸");
        addDetectedTasteTerm(terms, searchable, "甜");
        addDetectedTasteTerm(terms, searchable, "苦");
        addDetectedTasteTerm(terms, searchable, "辣");
        addDetectedTasteTerm(terms, searchable, "咸");
    }

    private void addDetectedTasteTerm(Set<String> terms, String searchable, String taste) {
        if (containsTasteToken(searchable, taste)) {
            terms.add(taste);
        }
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    // ==================== 相似度计算 ====================

    /**
     * 批量计算所有菜品与用户偏好的加权余弦相似度。
     * 核心推荐算法入口。
     */
    private List<Map.Entry<DishInfo, Double>> calculateSimilarities(List<DishInfo> dishes, List<String> tastes, List<String> preferredDishes) {
        List<Map.Entry<DishInfo, Double>> dishScores = new ArrayList<>(dishes.size());
        for (DishInfo dish : dishes) {
            dishScores.add(new AbstractMap.SimpleEntry<>(dish, calculateSimilarity(dish, tastes, preferredDishes)));
        }
        return dishScores;
    }

    /**
     * 计算单个菜品与用户偏好的加权余弦相似度。
     *
     * <p>算法要点：
     * - 口味匹配（权重 0.7）：同义词展开后在菜品的 taste / ingredient 字段匹配
     * - 菜品偏好匹配（权重 0.8）：精确匹配菜名，失败时做子串匹配降权
     * - 最终相似度 = 点积 / (用户向量模 × 菜品向量模)
     *
     * @param dish             菜品
     * @param tastes           口味偏好（已归一化）
     * @param preferredDishes  菜品偏好（已归一化）
     * @return [0, 1] 相似度
     */
    private double calculateSimilarity(DishInfo dish, List<String> tastes, List<String> preferredDishes) {
        Map<String, Double> dishFeatures = buildDishFeatures(dish);
        if (tastes.isEmpty() && preferredDishes.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        double userNormSq = 0.0;

        // 口味匹配：同义词展开后在菜品口味/食材字段中查找
        for (String taste : tastes) {
            if (taste == null || taste.trim().isEmpty()) continue;
            String raw = taste.trim();
            userNormSq += 0.7 * 0.7;

            Set<String> expanded = new LinkedHashSet<>(expandSynonyms(raw));
            expanded.add(raw);

            boolean matchedTaste = false;
            for (String exp : expanded) {
                Double w = dishFeatures.get("taste_" + exp);
                if (w != null && w > 0) { dotProduct += 0.7 * w; matchedTaste = true; }
            }
            // 口味字段未命中 → 尝试食材字段（强度减半）
            if (!matchedTaste) {
                for (String exp : expanded) {
                    Double w = dishFeatures.get("ingredient_" + exp);
                    if (w != null && w > 0) { dotProduct += 0.7 * w * 0.5; break; }
                }
            }
        }

        // 菜品偏好匹配：先精确匹配菜名，失败则子串匹配（降权 0.6）
        for (String dishName : preferredDishes) {
            if (dishName == null || dishName.trim().isEmpty()) continue;
            String raw = dishName.trim();
            userNormSq += 0.8 * 0.8;

            Double w = dishFeatures.get("dish_" + raw);
            if (w != null && w > 0) {
                dotProduct += 0.8 * w;
            } else {
                for (Map.Entry<String, Double> feature : dishFeatures.entrySet()) {
                    if (feature.getKey().startsWith("dish_") && feature.getKey().contains(raw)) {
                        dotProduct += 0.8 * feature.getValue() * 0.6;
                        break;
                    }
                }
            }
        }

        if (userNormSq == 0) return 0.0;
        double dishNormSq = 0.0;
        for (double value : dishFeatures.values()) dishNormSq += value * value;
        if (dishNormSq == 0) return 0.0;
        return dotProduct / (Math.sqrt(userNormSq) * Math.sqrt(dishNormSq));
    }

    /**
     * 计算两个带权重特征集合的通用余弦相似度。
     * 用于 UserCF 中的用户-菜品特征匹配。
     */
    private double calculateWeightedCosineSimilarity(Map<String, Double> features1, Map<String, Double> features2) {
        if (features1.isEmpty() || features2.isEmpty()) return 0.0;
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (double v : features1.values()) n1 += v * v;
        for (double v : features2.values()) n2 += v * v;
        for (Map.Entry<String, Double> e : features1.entrySet()) dot += e.getValue() * features2.getOrDefault(e.getKey(), 0.0);
        if (n1 == 0 || n2 == 0) return 0.0;
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }
    private List<Map<String, Object>> collectBlockedByTaboo(List<DishInfo> allDishes, List<String> taboos, int limit) {
        List<String> normalizedTaboos = normalizeTerms(taboos);
        if (normalizedTaboos.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> blocked = new ArrayList<>();
        for (DishInfo dish : allDishes) {
            if (!containsTaboo(dish, normalizedTaboos)) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("dishId", dish.getDishId());
            item.put("dishName", dish.getDishName());
            item.put("matchedTaboos", findMatchedTerms(dish, normalizedTaboos));
            blocked.add(item);
            if (blocked.size() >= limit) {
                break;
            }
        }
        return blocked;
    }

    private List<Map<String, Object>> buildMatchedFeatures(List<DishInfo> dishes, List<String> tastes, List<String> preferredDishes) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (dishes == null || dishes.isEmpty()) {
            return result;
        }

        List<String> normalizedTastes = normalizeTerms(tastes);
        List<String> normalizedPreferredDishes = normalizeTerms(preferredDishes);

        for (DishInfo dish : dishes) {
            Map<String, Object> item = new HashMap<>();
            item.put("dishId", dish.getDishId());
            item.put("dishName", dish.getDishName());
            item.put("tasteMatches", findMatchedTasteTerms(dish, normalizedTastes));
            item.put("dishMatches", findMatchedDishTerms(dish, normalizedPreferredDishes));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildScoreBreakdown(List<DishInfo> dishes, List<String> tastes, List<String> preferredDishes) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (dishes == null || dishes.isEmpty()) {
            return result;
        }

        List<String> normalizedTastes = normalizeTerms(tastes);
        List<String> normalizedPreferredDishes = normalizeTerms(preferredDishes);

        for (DishInfo dish : dishes) {
            Map<String, Double> dishFeatures = buildDishFeatures(dish);
            double tasteScore = calculateFeatureContribution(normalizedTastes, "taste_", 0.7, dishFeatures);
            double dishScore = calculateFeatureContribution(normalizedPreferredDishes, "dish_", 0.8, dishFeatures);
            double similarity = calculateSimilarity(dish, normalizedTastes, normalizedPreferredDishes);

            Map<String, Object> item = new HashMap<>();
            item.put("dishId", dish.getDishId());
            item.put("dishName", dish.getDishName());
            item.put("tasteScore", tasteScore);
            item.put("dishScore", dishScore);
            item.put("similarity", similarity);
            result.add(item);
        }
        return result;
    }

    private double calculateFeatureContribution(List<String> terms, String prefix, double featureWeight, Map<String, Double> dishFeatures) {
        if (terms == null || terms.isEmpty() || dishFeatures == null || dishFeatures.isEmpty()) {
            return 0.0;
        }
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String term : terms) {
            expanded.addAll(expandSynonyms(term));
        }
        double score = 0.0;
        for (String term : expanded) {
            double dishWeight = dishFeatures.getOrDefault(prefix + term, 0.0);
            score += featureWeight * dishWeight;
        }
        return score;
    }

    private List<String> findMatchedTerms(DishInfo dish, List<String> terms) {
        String searchable = (safeLower(dish.getDishName()) + " "
                + safeLower(dish.getIngredient()) + " "
                + safeLower(dish.getTaste()) + " "
                + safeLower(dish.getDescription())).trim();

        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String term : normalizeTerms(terms)) {
            for (String expanded : expandSynonyms(term)) {
                if (searchable.contains(expanded.toLowerCase(Locale.ROOT))) {
                    matched.add(term);
                    break;
                }
            }
        }
        return new ArrayList<>(matched);
    }

    private List<String> findMatchedTasteTerms(DishInfo dish, List<String> tastes) {
        Set<String> dishTasteSet = new LinkedHashSet<>(parseStringList(dish.getTaste()));
        String searchable = safeLower(dish.getDescription());
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String taste : normalizeTerms(tastes)) {
            for (String expanded : expandSynonyms(taste)) {
                if (dishTasteSet.contains(expanded) || searchable.contains(expanded.toLowerCase(Locale.ROOT))) {
                    matched.add(taste);
                    break;
                }
            }
        }
        return new ArrayList<>(matched);
    }

    private List<String> findMatchedDishTerms(DishInfo dish, List<String> preferredDishes) {
        String dishName = safeLower(dish.getDishName());
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String term : normalizeTerms(preferredDishes)) {
            if (dishName.contains(term.toLowerCase(Locale.ROOT))) {
                matched.add(term);
            }
        }
        return new ArrayList<>(matched);
    }

    // ==================== 用户 ID 推荐 ====================

    /**
     * 根据用户 ID 推荐菜品。
     *
     * <p>推荐策略优先级：
     * 1. 协同过滤 UserCF（用户评分 ≥ 2 条时）
     * 2. 偏好内容推荐（从 UserPreference 表读取口味、忌口、食材）
     * 3. 热门多样性推荐（冷启动 / 偏好推荐无结果时回退）
     *
     * <p>偏好评分使用 calculateSimilarityWithPriority，当前偏好权重 1.0/0.9，
     * 历史偏好权重 0.4/0.3。
     */
    @Override
    public Object recommendDishesByUserId(Long userId, int page, int size) {
        String cacheKey = "recommendation:v2:user_id:" + userId + ":" + page + ":" + size;
        Object cachedResult = getCachedValue(cacheKey, Map.class, "从Redis缓存获取用户推荐结果");
        if (cachedResult != null) return cachedResult;

        List<DishInfo> availableDishes = getAvailableDishes();

        // 1) 优先：基于相似用户的协同过滤
        Page<DishInfo> userCfPage = recommendBySimilarUsers(userId, page, size, availableDishes);
        if (userCfPage != null && userCfPage.getRecords() != null && !userCfPage.getRecords().isEmpty()) {
            Map<String, Object> response = buildSourceResponse(userCfPage, SOURCE_USER_CF);
            cacheValue(cacheKey, response, 15, java.util.concurrent.TimeUnit.MINUTES, "用户协同过滤推荐结果已缓存到Redis");
            return response;
        }

        // 2) 冷启动用户直接使用热门+多样性推荐
        if (isColdStartUser(userId)) {
            Object result = recommendPopularDishes(userId, page, size);
            Map<String, Object> response = buildSourceResponse(result, SOURCE_POPULAR);
            cacheValue(cacheKey, response, 10, java.util.concurrent.TimeUnit.MINUTES, "冷启动推荐结果已缓存到Redis");
            return response;
        }

        // 3) 从数据库读取该用户在 UserPreference 表中持久化的偏好
        UserPreference userPreference = userPreferenceService.getUserPreference(userId);
        List<String> taboos = parseStringList(userPreference.getTaboo());
        List<String> tastes = removeConflictingTastes(parseStringList(userPreference.getTaste()), taboos);
        List<String> ingredients = parseStringList(userPreference.getIngredient());

        // 同时读取"历史偏好"（降权使用，避免偏好突变导致推荐结果跳变）
        List<String> historyTastes = Collections.emptyList();
        List<String> historyIngredients = Collections.emptyList();
        try {
            UserPreference historyPreference = userPreferenceService.getHistoryPreference(userId);
            if (historyPreference != null) {
                historyTastes = parseStringList(historyPreference.getTaste());
                historyIngredients = parseStringList(historyPreference.getIngredient());
            }
        } catch (Exception e) { e.printStackTrace(); }
        final List<String> hTastes = historyTastes;
        final List<String> hIngredients = historyIngredients;

        // 4) 过滤忌口后，用优先权重计算相似度
        List<DishInfo> filteredDishes = filterTabooDishes(availableDishes, taboos);
        List<Map.Entry<DishInfo, Double>> filteredScores = filteredDishes.stream()
                .map(dish -> new AbstractMap.SimpleEntry<>(dish, calculateSimilarityWithPriority(dish, tastes, ingredients, hTastes, hIngredients)))
                .filter(entry -> entry.getValue() >= 0.1)
                .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        for (Map.Entry<DishInfo, Double> entry : filteredScores)
            entry.getKey().setMatchScore((double) Math.round(entry.getValue() * 100.0));

        if (filteredScores.isEmpty()) {
            Object fallback = recommendPopularDishes(userId, page, size);
            Map<String, Object> response = buildSourceResponse(fallback, SOURCE_POPULAR);
            cacheValue(cacheKey, response, 15, java.util.concurrent.TimeUnit.MINUTES, "用户推荐为空，回退热门推荐并缓存");
            return response;
        }

        Page<DishInfo> dishPage = toPage(dishesFromScores(filteredScores), page, size);
        Map<String, Object> response = buildSourceResponse(dishPage, SOURCE_CONTENT);
        cacheValue(cacheKey, response, 15, java.util.concurrent.TimeUnit.MINUTES, "用户推荐结果已缓存到Redis");
        return response;
    }

    /**
     * 计算菜品与用户的相似度，当前偏好权重高，历史偏好权重低。
     *
     * <p>权重策略：
     * - 当前口味: 1.0, 当前食材: 0.9
     * - 历史口味: 0.4（不与当前重复时）, 历史食材: 0.3
     * 当前和历史之间用户已调整过的偏好不会同时计入（去重）。
     */
    private double calculateSimilarityWithPriority(DishInfo dish, List<String> currentTastes, List<String> currentIngredients,
                                                 List<String> historyTastes, List<String> historyIngredients) {
        Map<String, Double> userFeatures = new HashMap<>();
        Set<String> currentTasteSet = new HashSet<>(normalizeTerms(currentTastes));
        Set<String> currentIngredientSet = new HashSet<>(normalizeTerms(currentIngredients));

        addWeightedFeatures(userFeatures, "taste_", currentTasteSet, 1.0);
        addWeightedFeatures(userFeatures, "ingredient_", currentIngredientSet, 0.9);

        for (String taste : normalizeTerms(historyTastes)) {
            if (!currentTasteSet.contains(taste)) userFeatures.put("taste_" + taste, 0.4);
        }
        for (String ingredient : normalizeTerms(historyIngredients)) {
            if (!currentIngredientSet.contains(ingredient)) userFeatures.put("ingredient_" + ingredient, 0.3);
        }
        return calculateWeightedCosineSimilarity(userFeatures, buildDishFeatures(dish));
    }

    /**
     * 基于相似用户(UserCF)的推荐。
     *
     * <p>升级点：
     * 1) 相似度改为“中心化余弦”(Adjusted Cosine)：先减去用户平均分，降低“有人普遍打高分/低分”的偏置；
     * 2) 显著性修正(Significance Weighting)：共同评分越少，相似度越被衰减；
     * 3) Top-K邻居：只使用最相似的前K个邻居，减少噪声与计算量。
     */
    private Page<DishInfo> recommendBySimilarUsers(Long userId, int page, int size, List<DishInfo> availableDishes) {
        if (dishRatingMapper == null || userId == null || availableDishes == null || availableDishes.isEmpty()) {
            return null;
        }

        List<DishRating> allRatings = dishRatingMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        if (allRatings == null || allRatings.isEmpty()) {
            return null;
        }

        Map<Long, List<DishRating>> ratingsByUser = allRatings.stream()
                .filter(r -> r.getUserId() != null && r.getDishId() != null && r.getScore() != null)
                .collect(Collectors.groupingBy(DishRating::getUserId));

        List<DishRating> currentUserRatings = ratingsByUser.getOrDefault(userId, Collections.emptyList());
        if (currentUserRatings.size() < CF_MIN_COMMON_RATINGS) {
            return null;
        }

        Map<Long, Integer> currentScoreMap = currentUserRatings.stream()
                .collect(Collectors.toMap(DishRating::getDishId, DishRating::getScore, (a, b) -> b));
        if (currentScoreMap.size() < CF_MIN_COMMON_RATINGS) {
            return null;
        }

        double currentUserMean = averageScore(currentScoreMap.values());

        List<Map.Entry<Long, CfNeighborSimilarity>> rankedNeighbors = ratingsByUser.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), userId))
                .map(entry -> {
                    Long otherUserId = entry.getKey();
                    Map<Long, Integer> otherScoreMap = entry.getValue().stream()
                            .collect(Collectors.toMap(DishRating::getDishId, DishRating::getScore, (a, b) -> b));

                    double otherUserMean = averageScore(otherScoreMap.values());
                    CfNeighborSimilarity sim = centeredCosineWithSignificance(currentScoreMap, otherScoreMap, currentUserMean, otherUserMean);
                    return new AbstractMap.SimpleEntry<>(otherUserId, sim);
                })
                .filter(entry -> entry.getValue().score() > 0.0)
                .sorted((a, b) -> Double.compare(b.getValue().score(), a.getValue().score()))
                .limit(CF_TOP_K_NEIGHBORS)
                .collect(Collectors.toList());

        if (rankedNeighbors.isEmpty()) {
            return null;
        }

        Set<Long> availableDishIds = availableDishes.stream()
                .map(DishInfo::getDishId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Double> numerator = new HashMap<>();
        Map<Long, Double> denominator = new HashMap<>();

        for (Map.Entry<Long, CfNeighborSimilarity> neighbor : rankedNeighbors) {
            Long neighborUserId = neighbor.getKey();
            double sim = neighbor.getValue().score();
            List<DishRating> neighborRatings = ratingsByUser.getOrDefault(neighborUserId, Collections.emptyList());
            Map<Long, Integer> neighborScoreMap = neighborRatings.stream()
                    .collect(Collectors.toMap(DishRating::getDishId, DishRating::getScore, (a, b) -> b));
            double neighborMean = averageScore(neighborScoreMap.values());

            for (DishRating rating : neighborRatings) {
                Long dishId = rating.getDishId();
                Integer score = rating.getScore();
                if (dishId == null || score == null) {
                    continue;
                }
                if (currentScoreMap.containsKey(dishId)) {
                    continue;
                }
                if (!availableDishIds.contains(dishId)) {
                    continue;
                }

                // 使用邻居“去均值分”参与加权，最后再加回目标用户均值
                numerator.merge(dishId, sim * (score - neighborMean), Double::sum);
                denominator.merge(dishId, Math.abs(sim), Double::sum);
            }
        }

        if (numerator.isEmpty()) {
            return null;
        }

        Map<Long, DishInfo> dishMap = availableDishes.stream()
                .collect(Collectors.toMap(DishInfo::getDishId, d -> d, (a, b) -> a));

        List<Map.Entry<DishInfo, Double>> predicted = numerator.entrySet().stream()
                .map(entry -> {
                    Long dishId = entry.getKey();
                    double den = denominator.getOrDefault(dishId, 0.0);
                    if (den <= 0.0) {
                        return null;
                    }
                    DishInfo dish = dishMap.get(dishId);
                    if (dish == null) {
                        return null;
                    }

                    // 预测分 = 目标用户均值 + 邻居去均值加权贡献
                    double predictedScore = currentUserMean + (entry.getValue() / den);
                    return new AbstractMap.SimpleEntry<>(dish, predictedScore);
                })
                .filter(Objects::nonNull)
                .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        if (predicted.isEmpty()) {
            return null;
        }

        for (Map.Entry<DishInfo, Double> entry : predicted) {
            entry.getKey().setMatchScore((double) Math.round(Math.min(1.0, entry.getValue() / 5.0) * 100.0));
        }

        return toPage(dishesFromScores(predicted), page, size);
    }

    /**
     * 中心化余弦 + 显著性修正。
     *
     * <p>步骤：
     * 1) 只在共同评分菜上计算；
     * 2) 每个评分减去对应用户平均分，得到去偏差后的向量；
     * 3) 计算余弦相似度；
     * 4) 乘以显著性权重 common/(common+shrinkage)。
     */
    private CfNeighborSimilarity centeredCosineWithSignificance(Map<Long, Integer> left,
                                                                Map<Long, Integer> right,
                                                                double leftMean,
                                                                double rightMean) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return new CfNeighborSimilarity(0.0, 0);
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        int common = 0;

        for (Map.Entry<Long, Integer> entry : left.entrySet()) {
            Long dishId = entry.getKey();
            Integer rv = right.get(dishId);
            if (rv == null) {
                continue;
            }
            double lv = entry.getValue() - leftMean;
            double rvd = rv - rightMean;
            dot += lv * rvd;
            leftNorm += lv * lv;
            rightNorm += rvd * rvd;
            common++;
        }

        if (common < CF_MIN_COMMON_RATINGS || leftNorm == 0.0 || rightNorm == 0.0) {
            return new CfNeighborSimilarity(0.0, common);
        }

        double cosine = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        double significance = common / (double) (common + CF_SIGNIFICANCE_SHRINKAGE);
        double adjusted = cosine * significance;
        return new CfNeighborSimilarity(adjusted, common);
    }

    private double averageScore(Collection<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * 邻居相似度及其统计信息。
     */
    private record CfNeighborSimilarity(double score, int commonCount) {
    }

    private Map<String, Object> buildSourceResponse(Object recommendation, String source) {
        Map<String, Object> response = new HashMap<>();
        response.put("recommendation", recommendation);
        response.put("source", source);
        return response;
    }

    private String resolveInputRecommendationSource(boolean hasTemporaryPreferences, boolean clearedTemporaryPreferences) {
        if (clearedTemporaryPreferences) {
            return SOURCE_POPULAR;
        }
        return hasTemporaryPreferences ? SOURCE_CONTENT : SOURCE_POPULAR;
    }

    /**
     * 检查用户是否为冷启动情况（新用户或没有偏好数据）
     * @param userId 用户ID
     * @return true表示冷启动，false表示有足够数据
     */
    private boolean isColdStartUser(Long userId) {
        try {
            UserPreference userPreference = userPreferenceService.getUserPreference(userId);
            return userPreference == null
                    || (!hasNonEmptyPreference(userPreference.getTaste()) && !hasNonEmptyPreference(userPreference.getIngredient()));
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 冷启动情况下的热门菜品推荐
     * 结合评分、销量和菜品多样性进行推荐
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 推荐的菜品列表或分页结果
     */
    private Object recommendPopularDishes(Long userId, int page, int size) {
        String cacheKey = "recommendation:v2:popular:" + page + ":" + size;

        Object cachedResult = getCachedValue(cacheKey, Page.class, "从Redis缓存获取热门推荐结果");
        if (cachedResult != null) {
            return cachedResult;
        }

        List<Map.Entry<DishInfo, Double>> dishScores = getAvailableDishes().stream()
                .map(dish -> new AbstractMap.SimpleEntry<>(dish, calculatePopularityScore(dish)))
                .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        for (Map.Entry<DishInfo, Double> entry : dishScores) {
            entry.getKey().setMatchScore((double) Math.round(entry.getValue() * 100.0));
        }

        Page<DishInfo> dishPage = toPage(dishesFromScores(ensureDiversity(dishScores, 20)), page, size);
        cacheValue(cacheKey, dishPage, 1, java.util.concurrent.TimeUnit.HOURS, "热门推荐结果已缓存到Redis");
        return dishPage;
    }

    /**
     * 计算菜品的综合评分（结合评分和销量）
     * @param dish 菜品信息
     * @return 综合评分
     */
    private double calculatePopularityScore(DishInfo dish) {
        double ratingScore = 0.5;
        if (dishRatingService != null) {
            try {
                Double avgRating = dishRatingService.getAverageRating(dish.getDishId());
                if (avgRating != null) {
                    ratingScore = avgRating / 5.0;
                }
            } catch (Exception ignored) {
            }
        }

        double salesScore = dish.getHeat() != null && dish.getHeat() > 0
                ? Math.min(dish.getHeat() / 1000.0, 1.0)
                : 0.5;
        return 0.6 * ratingScore + 0.4 * salesScore;
    }

    /**
     * 去除与忌口冲突的口味。
     * 如用户说"喜欢辣，但不能吃辣" → 移除"辣"口味。
     */
    private List<String> removeConflictingTastes(List<String> tastes, List<String> taboos) {
        Set<String> tabooSet = new HashSet<>(normalizeTerms(taboos));
        if (tabooSet.isEmpty()) return normalizeTerms(tastes);
        return normalizeTerms(tastes).stream()
                .filter(taste -> !tabooSet.contains(taste))
                .collect(Collectors.toList());
    }

    /**
     * 多口味关键词时按口味交错排序。
     *
     * 确保每种口味至少有一个代表进入前 N 名。
     * 匹配多种口味的菜优先排在前面。
     * 之后各口味轮询选菜，保证覆盖面。
     */
    private List<Map.Entry<DishInfo, Double>> interleaveByTaste(
            List<Map.Entry<DishInfo, Double>> sorted,
            List<DishInfo> allDishes,
            List<String> tastes,
            int size) {
        if (sorted.size() <= tastes.size()) return sorted;

        java.util.function.BiPredicate<DishInfo, String> matchesTaste = (dish, taste) -> {
            Set<String> expanded = new LinkedHashSet<>(expandSynonyms(taste));
            expanded.add(taste);
            String searchable = safeLower(dish.getDishName()) + " " + safeLower(dish.getTaste())
                    + " " + safeLower(dish.getIngredient()) + " " + safeLower(dish.getDescription());
            for (String exp : expanded) {
                if (searchable.contains(exp.toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        };

        Map<String, List<Map.Entry<DishInfo, Double>>> byTaste = new LinkedHashMap<>();
        for (String taste : tastes) byTaste.put(taste, new ArrayList<>());
        List<Map.Entry<DishInfo, Double>> multi = new ArrayList<>();

        for (Map.Entry<DishInfo, Double> entry : sorted) {
            DishInfo dish = entry.getKey();
            List<String> matched = new ArrayList<>();
            for (String taste : tastes) {
                if (matchesTaste.test(dish, taste)) matched.add(taste);
            }
            if (matched.size() > 1) multi.add(entry);
            else if (matched.size() == 1) byTaste.get(matched.get(0)).add(entry);
        }

        // 轮询各口味，确保全覆盖
        List<Map.Entry<DishInfo, Double>> result = new ArrayList<>(multi);
        Set<Long> seen = new HashSet<>();
        for (Map.Entry<DishInfo, Double> e : result) {
            if (e.getKey().getDishId() != null) seen.add(e.getKey().getDishId());
        }
        boolean anyLeft = true;
        while (anyLeft && result.size() < size) {
            anyLeft = false;
            for (String taste : tastes) {
                List<Map.Entry<DishInfo, Double>> candidates = byTaste.get(taste);
                if (candidates == null || candidates.isEmpty()) continue;
                for (Map.Entry<DishInfo, Double> e : candidates) {
                    Long dishId = e.getKey().getDishId();
                    if (dishId != null && seen.contains(dishId)) continue;
                    result.add(e);
                    if (dishId != null) seen.add(dishId);
                    anyLeft = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 按菜品类别分组，每个类别取前几名，避免推荐全来自同一类别。
     * 用于热门推荐的多样性保证。
     */
    private List<Map.Entry<DishInfo, Double>> ensureDiversity(List<Map.Entry<DishInfo, Double>> dishScores, int maxDishes) {
        Map<Long, List<Map.Entry<DishInfo, Double>>> categoryMap = dishScores.stream()
                .collect(Collectors.groupingBy(entry -> {
                    Long categoryId = entry.getKey().getCategoryId();
                    return categoryId == null ? 0L : categoryId;
                }));
        List<Map.Entry<DishInfo, Double>> diverseList = new ArrayList<>();
        int dishesPerCategory = Math.max(1, maxDishes / Math.max(1, categoryMap.size()));
        for (List<Map.Entry<DishInfo, Double>> categoryDishes : categoryMap.values())
            categoryDishes.stream().limit(dishesPerCategory).forEach(diverseList::add);
        diverseList.sort(Map.Entry.<DishInfo, Double>comparingByValue().reversed());
        return diverseList.size() > maxDishes ? diverseList.subList(0, maxDishes) : diverseList;
    }

    /**
     * 在推荐结果中混入约 30% 的跨类别菜品，作为"探索推荐"。
     * 仅在首页生效（page=1），后续分页保持原排序。
     * 探索菜品以"[探索推荐]"标签标记在 description 末尾。
     */
    private Page<DishInfo> injectExploreDishes(Page<DishInfo> dishPage, List<DishInfo> allDishes,
                                                List<String> tastes, int page, int size) {
        if (dishPage == null || dishPage.getRecords() == null || dishPage.getRecords().isEmpty()) return dishPage;
        if (page > 1) return dishPage; // 只在首页注入探索菜品
        List<DishInfo> current = new ArrayList<>(dishPage.getRecords());
        if (current.size() < 4) return dishPage;

        int keepCount = (int) Math.ceil(current.size() * 0.7); // 保留 70% 的高分菜
        List<DishInfo> kept = new ArrayList<>(current.subList(0, Math.min(keepCount, current.size())));

        // 寻找已展示菜品中未出现的类别
        Set<Long> shownCategories = kept.stream().map(DishInfo::getCategoryId).filter(c -> c != null).collect(Collectors.toSet());
        Set<Long> shownIds = kept.stream().map(DishInfo::getDishId).collect(Collectors.toSet());
        List<DishInfo> explorePool = allDishes.stream()
                .filter(d -> d.getCategoryId() != null && !shownCategories.contains(d.getCategoryId()))
                .filter(d -> !shownIds.contains(d.getDishId()))
                .collect(Collectors.toList());
        Collections.shuffle(explorePool);

        int exploreCount = size - kept.size();
        List<DishInfo> explorers = explorePool.stream().limit(Math.max(1, exploreCount)).collect(Collectors.toList());

        List<DishInfo> mixed = new ArrayList<>(kept);
        mixed.addAll(explorers);
        for (DishInfo d : explorers) {
            if (d.getDescription() == null) d.setDescription("");
            d.setDescription(d.getDescription() + " [探索推荐]");
        }

        dishPage.setRecords(mixed);
        dishPage.setTotal(mixed.size());
        return dishPage;
    }
}