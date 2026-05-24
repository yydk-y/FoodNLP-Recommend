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

    // ==================== 静态常量 ====================

    /** 单次推荐返回的最大菜品数 */
    private static final int TOP_RECOMMENDATION_SIZE = 9;

    /** 推荐来源标识：协同过滤（根据相似用户评分预测） */
    private static final String SOURCE_USER_CF = "USER_CF";
    /** 推荐来源标识：内容推荐（基于用户偏好的余弦相似度） */
    private static final String SOURCE_CONTENT = "CONTENT_BASED";
    /** 推荐来源标识：热门+多样性（冷启动回退） */
    private static final String SOURCE_POPULAR = "POPULAR_DIVERSIFIED";

    /** 协同过滤——用户至少有2条评分才启动CF，数据太少无意义 */
    private static final int CF_MIN_COMMON_RATINGS = 2;
    /** 协同过滤——最多取20个最相似邻居，减少噪声和计算量 */
    private static final int CF_TOP_K_NEIGHBORS = 20;
    /**
     * 协同过滤显著性修正分母。
     * 共同评分越少，相似度被衰减得越厉害。
     * 公式：significance = common / (common + 8)，common=2时权重仅0.2
     */
    private static final int CF_SIGNIFICANCE_SHRINKAGE = 8;

    /** Redis缓存key：全量菜品列表（1小时过期） */
    private static final String DISHES_ALL_CACHE_KEY = "dishes:all";
    /** Redis缓存key前缀：用户输入的临时偏好 */
    private static final String USER_INPUT_TEMP_PREFERENCE_KEY_PREFIX = "recommendation:temp_preference:user_input:";
    /** 临时偏好缓存在Redis中的过期时间（分钟） */
    private static final long TEMP_PREFERENCE_TTL_MINUTES = 30;
    /** 偏好文本的分隔符：支持中文顿号、逗号、斜杠、空白 */
    private static final Pattern TERM_SPLIT_PATTERN = Pattern.compile("[、,，/\\\\s]+");

    /**
     * 同义词映射表。
     * key=标准口味词，value=所有口语化别名。
     * 用户说"想吃酸的"，同义词中有"糖醋"，"糖醋排骨"就能被匹配到。
     * 用于偏好提取后的归一化匹配、忌口检测。
     */
    private static final Map<String, List<String>> TERM_SYNONYMS = createSynonymMap();


    // ==================== 注入的依赖服务 ====================

    /** NLP服务：从用户输入的自然语言中提取出口味、忌口、菜品偏好 */
    @Autowired
    private NLPService nlpService;

    /** 菜品信息服务：提供菜品数据的CRUD操作 */
    @Autowired
    private DishInfoService dishInfoService;

    /** 用户偏好服务：根据用户ID查数据库中的持久化偏好（口味、忌口、食材） */
    @Autowired
    private UserPreferenceService userPreferenceService;

    /** 菜品评分服务：用于冷启动推荐中的热门评分计算（入required=false，因为可能没配） */
    @Autowired(required = false)
    private DishRatingService dishRatingService;

    /** 菜品评分Mapper：直接查评分表，用于协同过滤的原始数据获取 */
    @Autowired(required = false)
    private DishRatingMapper dishRatingMapper;

    /** 推荐解释服务：为推荐结果生成自然语言的解释文本（可能未配置） */
    @Autowired(required = false)
    private RecommendationExplanationService recommendationExplanationService;

    /** Jackson JSON解析器：用于解析JSON格式的偏好数据（如["辣","酸"]字符串→List） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Redis模板：缓存推荐结果和临时偏好，减少重复计算 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    // ========================================================================
    //  入口1：根据用户输入文本推荐（前端聊天输入的主入口）
    // ========================================================================

    /**
     * 用户输入推荐——主入口。
     * 流程：提取偏好 → 有偏好则内容推荐，无则热门推荐 → 结果缓存
     *
     * @param userInput 用户说的话，如"我喜欢辣的，不要香菜"
     * @param page      页码
     * @param size      每页大小
     * @return Map，含 preferences（偏好）、recommendation（分页菜品）、source（来源）、explanation（解释）
     */
    @Override
    public Object recommendDishes(String userInput, int page, int size) {
        // 1) 预处理：去空去前后空白
        String normalizedInput = userInput == null ? "" : userInput.trim();

        // 2) 生成Redis缓存key，相同输入+分页直接取缓存
        String cacheKey = "recommendation:v2:user_input:" + normalizedInput.toLowerCase() + ":" + page + ":" + size;

        // 3) 尝试从Redis读取缓存。如果缓存命中且非空，直接返回。
        //    如果缓存是空推荐（之前算出来就是空的），删掉缓存重新算，避免一直返回空。
        try {
            Object cachedResponse = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResponse instanceof Map<?, ?> cachedMap) {
                if (!isRecommendationEmpty(cachedMap)) {
                    System.out.println("从Redis缓存获取推荐结果");
                    return cachedResponse;
                }
                redisTemplate.delete(cacheKey);
                System.out.println("命中空推荐缓存，已删除并重新计算推荐");
            }
        } catch (Exception e) {
            System.out.println("Redis缓存读取失败: " + e.getMessage());
        }

        // 4) 调用NLP服务从用户输入中提取偏好信息
        //    NLP返回一个Map，包含三个key："tastes"（口味）、"taboos"（忌口）、"dishes"（菜品偏好）
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
        // 确保tastes/taboos/dishes三个key都存在（缺失时补空列表），避免后续判空
        preferences = ensurePreferenceShape(preferences);

        // 打印提取结果，方便调试
        System.out.println("用户输入: " + normalizedInput);
        System.out.println("提取的口味偏好: " + preferences.get("tastes"));
        System.out.println("提取的忌口: " + preferences.get("taboos"));
        System.out.println("提取的菜品偏好: " + preferences.get("dishes"));

        // 5) 判断是否有实质性偏好（口味/菜品/忌口至少一项非空）
        boolean hasTemporaryPreferences = hasTemporaryPreferences(preferences);
        String tempPreferenceKey = USER_INPUT_TEMP_PREFERENCE_KEY_PREFIX + normalizedInput.toLowerCase();

        Object recommendationResult;
        boolean clearedTemporaryPreferences = false;

        if (hasTemporaryPreferences) {
            // 有偏好 → 存临时偏好评Redis（30分钟过期），然后走内容推荐
            saveTempPreference(tempPreferenceKey, preferences);
            recommendationResult = recommendDishesByPreferences(preferences, 1, TOP_RECOMMENDATION_SIZE);

            // 如果内容推荐结果是空的（偏好太偏门，所有菜都过滤掉了）
            // 就删掉临时偏好和缓存，回退到热门+多样性推荐
            if (isRecommendationEmpty(recommendationResult)) {
                deleteTempPreference(tempPreferenceKey);
                deleteRecommendationCache(cacheKey);
                preferences = ensurePreferenceShape(new HashMap<>());
                recommendationResult = recommendPopularDishes(null, 1, TOP_RECOMMENDATION_SIZE);
                clearedTemporaryPreferences = true;
                System.out.println("临时偏好推荐为空，已删除临时偏好与推荐缓存，并回退到冷启动推荐");
            }
        } else {
            // 无任何偏好 → 直接走热门多样性推荐（冷启动）
            recommendationResult = recommendPopularDishes(null, 1, TOP_RECOMMENDATION_SIZE);
        }

        // 6) 解析推荐来源：CONTENT_BASED or POPULAR_DIVERSIFIED
        String recommendationSource = resolveInputRecommendationSource(hasTemporaryPreferences, clearedTemporaryPreferences);

        // 7) 组装返回结果
        Map<String, Object> response = new HashMap<>();
        response.put("preferences", preferences);            // 提取到的用户偏好（前端展示用）
        response.put("recommendation", recommendationResult); // 菜品分页结果
        response.put("source", recommendationSource);         // 推荐来源标签

        // 8) 生成推荐解释文本
        if (recommendationResult instanceof Page<?> resultPage) {
            @SuppressWarnings("unchecked")
            Page<DishInfo> dishPage = (Page<DishInfo>) resultPage;
            List<DishInfo> recommendedDishes = dishPage.getRecords();
            // 三种解释模板：清空偏好回退 | 有偏好的内容推荐 | 无偏好热门推荐
            String explanation = clearedTemporaryPreferences
                    ? "根据你本次输入提取到的临时偏好未匹配到可推荐菜品，已自动清空本次临时偏好，并为你切换到热门多样化推荐。"
                    : (hasTemporaryPreferences
                    ? buildRecommendationExplanation(normalizedInput, preferences, recommendedDishes)
                    : "你还没有提供明确偏好，先为你推荐当前热门且多样化的菜品。");
            response.put("explanation", explanation);
        } else {
            response.put("explanation", "推荐解释暂不可用。");
        }

        // 9) 缓存结果到Redis（30分钟过期），空结果不缓存
        if (!clearedTemporaryPreferences) {
            cacheValue(cacheKey, response, 30, java.util.concurrent.TimeUnit.MINUTES, "推荐结果已缓存到Redis");
        }

        return response;
    }


    // ========================================================================
    //  偏好数据预处理工具方法
    // ========================================================================

    /**
     * 确保偏好Map有tastes/taboos/dishes三个键，缺失的补空列表。
     * 这样后续代码可以直接get()不用判空。
     */
    private Map<String, Object> ensurePreferenceShape(Map<String, Object> preferences) {
        Map<String, Object> normalized = preferences == null ? new HashMap<>() : new HashMap<>(preferences);
        normalized.computeIfAbsent("tastes", k -> new ArrayList<String>());
        normalized.computeIfAbsent("taboos", k -> new ArrayList<String>());
        normalized.computeIfAbsent("dishes", k -> new ArrayList<String>());
        return normalized;
    }

    /**
     * 判断用户是否说了实质性内容。
     * 口味/菜品/忌口至少有一项非空 → 有偏好。
     * 用于区分"我想吃辣的"和"随便看看"两种场景。
     */
    private boolean hasTemporaryPreferences(Map<String, Object> preferences) {
        if (preferences == null) return false;

        // 从偏好Map中取出三个列表，没有的话用空列表默认值
        List<String> tastes = (List<String>) preferences.getOrDefault("tastes", Collections.emptyList());
        List<String> dishes = (List<String>) preferences.getOrDefault("dishes", Collections.emptyList());
        List<String> taboos = (List<String>) preferences.getOrDefault("taboos", Collections.emptyList());

        // 只要有一个非空字符串就认为有偏好
        boolean hasTaste = tastes.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
        boolean hasDish = dishes.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
        boolean hasTaboo = taboos.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
        return hasTaste || hasDish || hasTaboo;
    }


    // ========================================================================
    //  推荐结果判空（兼容多种反序列化形态）
    // ========================================================================

    /**
     * 检查推荐结果是否为空。
     * 因为Redis缓存后的Jackson反序列化可能会导致不同类型的对象，
     * 所以需要兼容 Page、Map{"records":[]}、Map{"recommendation":Page/Map} 多种形态。
     */
    private boolean isRecommendationEmpty(Object recommendationResult) {
        if (recommendationResult == null) return true;

        // 形态1：直接是Page对象
        if (recommendationResult instanceof Page<?> page) {
            return page.getRecords() == null || page.getRecords().isEmpty();
        }

        // 形态2：是Map（可能是Page反序列化后的结构，或者包装Map）
        if (recommendationResult instanceof Map<?, ?> map) {
            // 子形态2a：Page反序列化成了Map，含有records字段
            if (map.containsKey("records")) {
                Object records = map.get("records");
                return !(records instanceof Collection<?>) || ((Collection<?>) records).isEmpty();
            }
            // 子形态2b：包装结构 {"recommendation": Page/Map}
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


    // ========================================================================
    //  推荐解释生成（使用LLM或回退规则）
    // ========================================================================

    /**
     * 生成推荐结果的自然语言解释。
     * 先尝试调LLM服务（如GPT），如果未配置则返回固定模板文字。
     */
    private String buildRecommendationExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        // 如果推荐解释服务未注入（required=false），返回默认文案
        if (recommendationExplanationService == null) {
            return "已根据你提取到的口味、忌口和偏好菜品关键词完成推荐。";
        }
        return recommendationExplanationService.generateExplanation(userInput, preferences, recommendedDishes);
    }


    // ========================================================================
    //  Redis缓存工具方法
    // ========================================================================

    /**
     * 从Redis读取缓存，兼容空结果的删除逻辑。
     * 如果缓存是空推荐，直接删掉，避免反复返回空。
     *
     * @param cacheKey     Redis键
     * @param expectedType 期望的类型（如Page.class）
     * @param hitMessage   命中时打印的日志
     * @return 缓存对象，未命中或空缓存返回null
     */
    private Object getCachedValue(String cacheKey, Class<?> expectedType, String hitMessage) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) return null;
            // 空推荐缓存 → 删除并返回null，让上层重新计算
            if (isRecommendationEmpty(cached)) {
                redisTemplate.delete(cacheKey);
                System.out.println("命中空推荐缓存，已删除: " + cacheKey);
                return null;
            }
            // 类型匹配或反序列化后的Map形态都接受
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
     * 写入Redis缓存，空结果不缓存。
     * 如果写入空结果，反而会把旧的正常缓存删掉。
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

    /** 保存用户输入的临时偏好到Redis，30分钟自动过期 */
    private void saveTempPreference(String tempPreferenceKey, Map<String, Object> preferences) {
        try {
            redisTemplate.opsForValue().set(tempPreferenceKey, preferences, TEMP_PREFERENCE_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("临时偏好缓存写入失败: " + e.getMessage());
        }
    }

    /** 删除Redis中的临时偏好 */
    private void deleteTempPreference(String tempPreferenceKey) {
        try { redisTemplate.delete(tempPreferenceKey); } catch (Exception e) { System.out.println("临时偏好缓存删除失败: " + e.getMessage()); }
    }

    /** 删除Redis中的推荐结果缓存 */
    private void deleteRecommendationCache(String cacheKey) {
        try { redisTemplate.delete(cacheKey); } catch (Exception e) { System.out.println("推荐缓存删除失败: " + e.getMessage()); }
    }


    // ========================================================================
    //  菜品数据获取（带Redis缓存）
    // ========================================================================

    /**
     * 从Redis或DB获取全量菜品列表（包含已下架的）。
     * Redis TTL 1小时，菜品增删不频繁的场景适用。
     * 如果Redis缓存没有，查DB并回写缓存。
     */
    private List<DishInfo> getAllDishesWithCache() {
        try {
            Object cachedDishes = redisTemplate.opsForValue().get(DISHES_ALL_CACHE_KEY);
            // Redis中存的是List，但反序列化后可能是List<DishInfo>或List<LinkedHashMap>，需要转换
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
        // 缓存未命中 → 从DB查
        List<DishInfo> dishes = dishInfoService.getAllDishes();
        cacheValue(DISHES_ALL_CACHE_KEY, dishes, 1, java.util.concurrent.TimeUnit.HOURS, "菜品列表已缓存到Redis");
        return dishes;
    }

    /**
     * 转换Redis反序列化的菜品列表。
     * Jackson序列化List<DishInfo>到Redis时，读出可能是DishInfo对象，也可能是LinkedHashMap。
     * 两种都处理，保证反序列化兼容。
     */
    @SuppressWarnings("unchecked")
    private List<DishInfo> convertCachedDishList(List<?> cachedList) {
        if (cachedList.isEmpty()) return new ArrayList<>();
        Object first = cachedList.get(0);
        // 情况1：直接是DishInfo对象
        if (first instanceof DishInfo) {
            return cachedList.stream()
                    .filter(DishInfo.class::isInstance)
                    .map(DishInfo.class::cast)
                    .collect(Collectors.toList());
        }
        // 情况2：是LinkedHashMap（Jackson默认反序列化成Map），需要手动映射字段
        if (first instanceof LinkedHashMap<?, ?>) {
            return cachedList.stream()
                    .filter(LinkedHashMap.class::isInstance)
                    .map(item -> mapToDishInfo((LinkedHashMap<?, ?>) item))
                    .collect(Collectors.toList());
        }
        return null;
    }

    /**
     * 将LinkedHashMap手动映射成DishInfo实体。
     * 因为Jackson反序列化List时，如果泛型信息丢失，元素会变成LinkedHashMap。
     * 通过这个方法把所有字段一个个set回去。
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

    // 类型转换辅助函数：安全地将Object转为需要的类型
    private String toStringOrNull(Object value) { return value == null ? null : value.toString(); }
    private Long numberToLong(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private Integer numberToInteger(Object value) { return value instanceof Number n ? n.intValue() : null; }
    private BigDecimal numberToBigDecimal(Object value) { return value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null; }

    /** 从全量菜品中过滤出所有上架菜品（status=1表示上架） */
    private List<DishInfo> getAvailableDishes() {
        return getAllDishesWithCache().stream()
                .filter(dish -> Objects.equals(dish.getStatus(), 1))
                .collect(Collectors.toList());
    }


    // ========================================================================
    //  内存分页工具（不查数据库，纯List切片）
    // ========================================================================

    /**
     * 将内存中的List按page/size切片成分页对象。
     * 不重新查库，适合推荐结果已算好只需要截取前N条的场景。
     */
    private Page<DishInfo> toPage(List<DishInfo> dishes, int page, int size) {
        int total = dishes.size();
        int safeSize = Math.max(size, 1);                     // 防止size=0时除零
        int start = Math.max(0, (Math.max(page, 1) - 1) * safeSize);  // 起始索引
        int end = Math.min(start + safeSize, total);                   // 结束索引
        List<DishInfo> pageDishes = start < total
                ? new ArrayList<>(dishes.subList(start, end))
                : new ArrayList<>();
        Page<DishInfo> dishPage = new Page<>(page, size);
        dishPage.setRecords(pageDishes);
        dishPage.setTotal(total);
        dishPage.setPages((total + safeSize - 1) / safeSize);
        return dishPage;
    }


    // ========================================================================
    //  偏好文本解析与同义词处理
    // ========================================================================

    /**
     * 从偏好Map中提取指定key的列表，结果做同义词归一化展开。
     * 用于相似度计算的匹配——展开后匹配范围更广。
     * 如用户说"辣"→ 展开成["辣","麻辣","香辣","辣椒"...]，提高匹配率。
     */
    private List<String> getPreferenceList(Map<String, Object> preferences, String key) {
        Object value = preferences == null ? null : preferences.get(key);
        if (value instanceof List<?> list) return normalizeTerms(list);
        if (value instanceof String text) return parseStringList(text);  // JSON数组字符串→List
        return new ArrayList<>();
    }

    /**
     * 提取原始偏好值，不做同义词展开。
     * 用于生成缓存key（保持key确定性）和前端展示（展示用户原始输入）。
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
     * 解析JSON数组字符串为List<String>。
     * 比如DB里存的是 "["辣","麻"]"，解析成 ["辣","麻"]。
     * 如果JSON解析失败，按分隔符（顿号/逗号/斜杠/空白）拆分。
     * 结果做同义词归一化。
     */
    private List<String> parseStringList(String text) {
        if (text == null || text.trim().isEmpty() || "[]".equals(text.trim())) return new ArrayList<>();
        try {
            return normalizeTerms(objectMapper.readValue(text, List.class));
        } catch (Exception ignored) {
            // JSON解析失败，当普通文本按分隔符拆分
            return normalizeTerms(Arrays.asList(TERM_SPLIT_PATTERN.split(text)));
        }
    }

    /**
     * 解析JSON数组字符串，保持原始值（不做同义词展开）。
     * 用于不需要展开的场景。
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
     * 对术语列表做同义词归一化。
     * 输入["辣"] → 输出["辣","麻辣","香辣","辣椒","花椒"...]
     * 使用LinkedHashSet去重并保持顺序。
     */
    private List<String> normalizeTerms(Collection<?> terms) {
        if (terms == null) return new ArrayList<>();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object termObj : terms) {
            if (termObj == null) continue;
            String term = termObj.toString().trim();
            if (term.isEmpty()) continue;
            normalized.add(term);                        // 保留原始词
            normalized.addAll(expandSynonyms(term));     // 加入所有同义词
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 同义词展开——核心归一化逻辑。
     * 如果term匹配同义词映射的key或任一别名，返回完整集合（key + 所有别名）。
     * 否则仅返回原始term本身。
     * 例：输入"醋"→ 匹配到"酸"的别名 → 返回["醋","酸","酸味","糖醋","番茄"..."柠檬"]
     */
    private List<String> expandSynonyms(String term) {
        if (term == null) return Collections.emptyList();
        String normalized = term.trim();
        if (normalized.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        expanded.add(normalized);  // 原始词先放进去

        // 遍历同义词映射表，看term是否匹配某个标准口味或其别名
        for (Map.Entry<String, List<String>> entry : TERM_SYNONYMS.entrySet()) {
            String canonical = entry.getKey();
            List<String> aliases = entry.getValue();
            // term匹配标准key 或 匹配任一别名 → 把整个同义词集合加入
            if (canonical.equals(normalized) || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized))) {
                expanded.add(canonical);
                expanded.addAll(aliases);
            }
        }
        return new ArrayList<>(expanded);
    }

    /**
     * 同义词映射表定义。
     * key=标准口味/食材，value=所有口语化别名、常见相关食材。
     * 覆盖六大基础味型+海鲜+清淡，基本覆盖中文饮食常见表达。
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

    /** 判断一个字符串集合是否包含非空内容 */
    private boolean hasAnyTerm(Collection<String> terms) {
        return terms != null && terms.stream().anyMatch(item -> item != null && !item.trim().isEmpty());
    }

    /** 按热度（销量）降序排序，热度为null的当0处理 */
    private List<DishInfo> sortedByHeat(List<DishInfo> dishes) {
        return dishes.stream()
                .sorted(Comparator.comparingInt((DishInfo dish) -> dish.getHeat() == null ? 0 : dish.getHeat()).reversed())
                .collect(Collectors.toList());
    }

    /** 从 菜品↔相似度分数 的Map.Entry列表中提取菜品列表（丢弃分数） */
    private List<DishInfo> dishesFromScores(List<Map.Entry<DishInfo, Double>> scores) {
        return scores.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    /**
     * 将一组术语以指定权重加入用户特征向量。
     * 每个术语加上prefix前缀作为特征键，weight作为权重值。
     * 用于构建UserCF中用户偏好特征。
     */
    private void addWeightedFeatures(Map<String, Double> features, String prefix, Collection<?> values, double weight) {
        for (String value : normalizeTerms(values)) {
            features.put(prefix + value, weight);
        }
    }


    // ========================================================================
    //  菜品特征向量构建（余弦相似度基础）
    // ========================================================================

    /**
     * 构建一道菜的特征向量。
     * 将菜品的各个字段（菜名、口味、食材、描述）映射为带前缀的键值对。
     * 每道菜最终变成一个词袋Map：key="dish_菜名"/"taste_辣"/"ingredient_鱼"等，value=对应权重。
     *
     * 各维度权重：
     * - 菜名：0.8（用户指名道姓点菜时最重要）
     * - 口味：0.8（用户说"想吃辣"时，口味匹配最关键）
     * - 食材：0.65（食材偏好的重要性略低于口味）
     * - 描述：0.25（描述文本较为模糊，权重最低）
     */
    private Map<String, Double> buildDishFeatures(DishInfo dish) {
        Map<String, Double> dishFeatures = new HashMap<>();
        addRawFeature(dishFeatures, "dish_", dish.getDishName(), 0.8);
        addRawFeatures(dishFeatures, "taste_", parseStringListRaw(dish.getTaste()), 0.8);
        addRawFeatures(dishFeatures, "ingredient_", parseStringListRaw(dish.getIngredient()), 0.65);
        // 描述按分隔符分词后加入
        addRawFeatures(dishFeatures, "desc_",
                dish.getDescription() == null ? Collections.emptyList() : Arrays.asList(TERM_SPLIT_PATTERN.split(dish.getDescription())),
                0.25);
        return dishFeatures;
    }

    /** 批量添加特征：把集合中的每个词加上前缀作为key */
    private void addRawFeatures(Map<String, Double> features, String prefix, Collection<?> values, double weight) {
        for (Object v : values) {
            if (v == null) continue;
            String term = v.toString().trim();
            if (term.isEmpty()) continue;
            features.put(prefix + term, weight);
        }
    }

    /** 添加单个特征值 */
    private void addRawFeature(Map<String, Double> features, String prefix, String value, double weight) {
        if (value != null && !value.trim().isEmpty()) {
            features.put(prefix + value.trim(), weight);
        }
    }

    /**
     * 检查文本中是否包含海鲜相关的词语。
     * 在忌口检测中使用——如果菜品名/食材中没有显式写"海鲜"，
     * 但包含了"虾""鱼""蟹"等，也被认为含有海鲜。
     */
    private boolean containsSeafoodToken(String text) {
        if (text == null || text.isEmpty()) return false;
        return TERM_SYNONYMS.getOrDefault("海鲜", Collections.emptyList()).stream().anyMatch(text::contains)
                || text.contains("海鲜");
    }

    /**
     * 检测文本中隐含的基础口味并加入菜品特征。
     * 某些菜品的taste字段可能没写"辣"，但菜名或描述中有"水煮"、"椒麻"等。
     * 通过这个函数把隐含的口味检测出来，加入到特征向量中。
     */
    private void addDetectedTasteFeatures(Map<String, Double> dishFeatures, String searchable) {
        addDetectedTasteFeature(dishFeatures, searchable, "酸");
        addDetectedTasteFeature(dishFeatures, searchable, "甜");
        addDetectedTasteFeature(dishFeatures, searchable, "苦");
        addDetectedTasteFeature(dishFeatures, searchable, "辣");
        addDetectedTasteFeature(dishFeatures, searchable, "咸");
    }

    /** 如果文本中包含指定口味的相关关键词，加入特征向量，权重0.9 */
    private void addDetectedTasteFeature(Map<String, Double> dishFeatures, String searchable, String taste) {
        if (containsTasteToken(searchable, taste)) {
            dishFeatures.put("taste_" + taste, 0.9);
        }
    }

    /**
     * 判断一段文本是否包含某个口味的同义词。
     * 搜索方式：同义词列表逐个contains匹配 + 直接文字匹配。
     */
    private boolean containsTasteToken(String text, String taste) {
        if (text == null || text.isEmpty()) return false;
        return TERM_SYNONYMS.getOrDefault(taste, Collections.emptyList()).stream().anyMatch(text::contains)
                || text.contains(taste);
    }

    /** 判断偏好值是否非空（解析后有不为空的内容） */
    private boolean hasNonEmptyPreference(String value) {
        return value != null && !parseStringList(value).isEmpty();
    }


    // ========================================================================
    //  入口2：根据用户偏好Map推荐（含详细解释）
    // ========================================================================

    /**
     * 根据用户偏好信息推荐菜品（简化版，只返回分页菜品）。
     * 实际委托给 recommendDishesByPreferencesWithExplain，然后从中提取 recommendation 字段。
     */
    @Override
    public Object recommendDishesByPreferences(Map<String, Object> preferences, int page, int size) {
        Object detailed = recommendDishesByPreferencesWithExplain(preferences, page, size);
        if (detailed instanceof Map<?, ?> map && map.containsKey("recommendation")) {
            return map.get("recommendation");
        }
        return detailed;
    }

    /**
     * 根据用户偏好信息推荐菜品（完整版，含debug信息）。
     *
     * 核心流程：
     * 1. 修复偏好Map结构（补全三个key）
     * 2. 查Redis缓存
     * 3. 提取并归一化偏好（口味/忌口/菜品）
     * 4. 忌口去重：如果口味和忌口有冲突（如"喜欢辣"+"不能吃辣"），去掉冲突的口味
     * 5. 过滤掉所有含忌口的菜品
     * 6. 无偏好的话按热度排序；有偏好则计算余弦相似度并保留≥0.25的
     * 7. 多口味时做交错排序确保每种口味都有菜上榜
     * 8. 混入30%跨类别探索菜品（首页有效）
     * 9. 返回详细数据（含被忌口屏蔽的菜、匹配的特征、分数明细）
     */
    @Override
    public Object recommendDishesByPreferencesWithExplain(Map<String, Object> preferences, int page, int size) {
        // 确保三个标准键都存在
        preferences = ensurePreferenceShape(preferences);
        String cacheKey = generatePreferencesCacheKey(preferences, page, size);

        // 尝试读缓存
        Object cachedResult = getCachedValue(cacheKey, Page.class, "从Redis缓存获取偏好推荐结果");
        Page<DishInfo> dishPage;

        System.out.println("提取的口味偏好: " + preferences.get("tastes"));
        System.out.println("提取的忌口: " + preferences.get("taboos"));
        System.out.println("提取的菜品偏好: " + preferences.get("dishes"));

        // 从偏好Map中提取并归一化三个维度
        List<String> tastes = getPreferenceList(preferences, "tastes");          // 口味（已展开同义词）
        List<String> taboos = getPreferenceList(preferences, "taboos");          // 忌口（已展开同义词）
        List<String> preferredDishes = getPreferenceList(preferences, "dishes"); // 菜品偏好（已展开同义词）
        // 去掉与忌口冲突的口味：比如"喜欢辣"但"不能吃辣"→去掉辣
        tastes = removeConflictingTastes(tastes, taboos);
        // 原始值（不做同义词展开），用于缓存key生成和debug
        List<String> rawTastes = getRawPreferenceList(preferences, "tastes");
        List<String> rawPreferredDishes = getRawPreferenceList(preferences, "dishes");

        // 获取所有上架菜品，再过滤掉含忌口的
        List<DishInfo> availableDishes = getAvailableDishes();
        List<DishInfo> filteredDishes = filterTabooDishes(availableDishes, taboos);

        if (cachedResult instanceof Page<?> cachedPage) {
            // 缓存命中，直接取缓存
            @SuppressWarnings("unchecked")
            Page<DishInfo> casted = (Page<DishInfo>) cachedPage;
            dishPage = casted;
        } else {
            if (!hasAnyTerm(tastes) && !hasAnyTerm(preferredDishes)) {
                // 没有口味偏好也没有菜品偏好 → 按热度排序
                dishPage = toPage(sortedByHeat(filteredDishes), page, size);
            } else {
                // 有偏好 → 计算余弦相似度，保留≥0.25的，降序排列
                List<Map.Entry<DishInfo, Double>> filteredScores = calculateSimilarities(filteredDishes, rawTastes, rawPreferredDishes).stream()
                        .filter(entry -> entry.getValue() >= 0.25)            // 相似度太低的不推荐
                        .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())  // 高分在前
                        .collect(Collectors.toList());

                // 多口味时（如"想吃辣的甜的"），交错排序让每种口味都有菜上榜
                // 扩大交错池大小，避免后面 injectExploreDishes 把某口味的菜全替换掉
                final int interleaveSize = Math.max(size * 2, rawTastes.size() * 3);
                if (rawTastes.size() > 1) {
                    filteredScores = interleaveByTaste(filteredScores, filteredDishes, rawTastes, interleaveSize);
                }

                // 将相似度转换为前端可展示的百分制分数（四舍五入）
                for (Map.Entry<DishInfo, Double> entry : filteredScores) {
                    entry.getKey().setMatchScore((double) Math.round(entry.getValue() * 100.0));
                }
                dishPage = toPage(dishesFromScores(filteredScores), page, size);
            }

            // 混入30%跨类别菜品作为"探索推荐"（仅首页page=1生效）
            dishPage = injectExploreDishes(dishPage, filteredDishes, tastes, page, size);
            // 缓存计算结果（30分钟过期）
            cacheValue(cacheKey, dishPage, 30, java.util.concurrent.TimeUnit.MINUTES, "偏好推荐结果已缓存到Redis");
        }

        // 组装详细返回结果（用于debug和前端展示）
        Map<String, Object> detail = new HashMap<>();
        detail.put("recommendation", dishPage);           // 推荐的分页菜品

        Map<String, Object> normalized = new HashMap<>();
        normalized.put("tastes", tastes);
        normalized.put("taboos", taboos);
        normalized.put("dishes", preferredDishes);
        detail.put("normalizedPreferences", normalized);  // 归一化后的偏好（前端用）
        detail.put("blockedByTaboo", collectBlockedByTaboo(availableDishes, taboos, 20));  // 被忌口屏蔽了哪些菜
        detail.put("matchedFeatures", buildMatchedFeatures(dishPage.getRecords(), tastes, preferredDishes));  // 每道菜匹配了哪些特征
        detail.put("scoreBreakdown", buildScoreBreakdown(dishPage.getRecords(), tastes, preferredDishes));    // 每道菜的分数明细

        return detail;
    }


    // ========================================================================
    //  缓存key生成
    // ========================================================================

    /**
     * 生成偏好推荐的Redis缓存key。
     * 把口味/忌口/菜品偏好各自排序后拼接，确保相同内容生成相同key。
     * 排序很重要——否则 ["辣","酸"] 和 ["酸","辣"] 会生成不同的key。
     */
    private String generatePreferencesCacheKey(Map<String, Object> preferences, int page, int size) {
        List<String> tastes = (List<String>) preferences.getOrDefault("tastes", new ArrayList<>());
        List<String> taboos = (List<String>) preferences.getOrDefault("taboos", new ArrayList<>());
        List<String> preferredDishes = (List<String>) preferences.getOrDefault("dishes", new ArrayList<>());

        // 过滤空值并排序，保证key的确定性
        List<String> sortedTastes = tastes.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toList());
        List<String> sortedTaboos = taboos.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toList());
        List<String> sortedPreferredDishes = preferredDishes.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toList());

        Collections.sort(sortedTastes);
        Collections.sort(sortedTaboos);
        Collections.sort(sortedPreferredDishes);

        return "recommendation:v2:" + sortedTastes + ":" + sortedTaboos + ":" + sortedPreferredDishes + ":" + page + ":" + size;
    }


    // ========================================================================
    //  忌口过滤
    // ========================================================================

    /**
     * 从菜品列表中过滤掉所有包含用户忌口的菜品。
     * 如果忌口列表为空，直接返回全部菜品。
     */
    private List<DishInfo> filterTabooDishes(List<DishInfo> dishes, List<String> taboos) {
        if (taboos == null || taboos.isEmpty()) {
            return dishes;
        }
        return dishes.stream()
                .filter(dish -> !containsTaboo(dish, taboos))
                .collect(Collectors.toList());
    }

    /**
     * 检查一道菜是否包含用户忌口。
     * 分两个层次检测：
     * 层次1：全文搜索——把菜名+食材+口味+描述拼成字符串，找忌口关键词
     * 层次2：结构化匹配——拆解食材集合，检测隐含口味（如菜名含"水煮"隐含辣）
     *
     * @return true=该菜含忌口，应被过滤掉
     */
    private boolean containsTaboo(DishInfo dish, List<String> taboos) {
        if (dish == null || taboos == null || taboos.isEmpty()) {
            return false;
        }

        // 将忌口列表做同义词展开，如"辣"→["辣","麻辣","香辣","辣椒"...]
        List<String> normalizedTaboos = normalizeTerms(taboos).stream()
                .flatMap(item -> expandSynonyms(item).stream())
                .distinct()
                .collect(Collectors.toList());
        if (normalizedTaboos.isEmpty()) {
            return false;
        }

        // 层次1：把菜品的所有文本字段拼成一个长字符串，逐个忌口词全文搜索
        String searchable = (safeLower(dish.getDishName()) + " "
                + safeLower(dish.getIngredient()) + " "
                + safeLower(dish.getTaste()) + " "
                + safeLower(dish.getDescription())).trim();
        for (String taboo : normalizedTaboos) {
            if (searchable.contains(taboo.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        // 层次2：拆解食材集合做精确匹配
        // 先从菜品字段解析出食材集合
        Set<String> ingredientSet = new HashSet<>(parseStringList(dish.getIngredient()));
        // 隐性检测：菜品中是否含海鲜关键词（如"虾""鱼"），有则把"海鲜"也加入食材集合
        if (containsSeafoodToken(searchable)) {
            ingredientSet.add("海鲜");
        }
        // 隐性检测：菜品文本中是否含有口味关键词，有则对应口味加入食材集合
        addDetectedTasteTerms(ingredientSet, searchable);
        // 在食材集合中精确匹配忌口词
        for (String taboo : normalizedTaboos) {
            if (ingredientSet.contains(taboo)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 隐式口味检测——将菜名/描述等文本中隐含的基础口味识别出来。
     * 比如"水煮肉片"虽然taste字段可能没写"辣"，
     * 但"水煮"关联"麻辣"，通过此方法将"辣"加入检测集。
     * 用于忌口检测的层次2匹配。
     */
    private void addDetectedTasteTerms(Set<String> terms, String searchable) {
        addDetectedTasteTerm(terms, searchable, "酸");
        addDetectedTasteTerm(terms, searchable, "甜");
        addDetectedTasteTerm(terms, searchable, "苦");
        addDetectedTasteTerm(terms, searchable, "辣");
        addDetectedTasteTerm(terms, searchable, "咸");
    }

    /** 如果文本中含有指定口味的关键词，把该口味加入集合 */
    private void addDetectedTasteTerm(Set<String> terms, String searchable, String taste) {
        if (containsTasteToken(searchable, taste)) {
            terms.add(taste);
        }
    }

    /** 安全的转小写（防止null） */
    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }


    // ========================================================================
    //  核心：加权余弦相似度计算
    // ========================================================================

    /**
     * 批量计算所有候选菜品与用户偏好的加权余弦相似度。
     * 输入：已过滤忌口的菜品列表，用户的口味和菜品偏好
     * 输出：每个菜品及其相似度分数的配对列表
     */
    private List<Map.Entry<DishInfo, Double>> calculateSimilarities(List<DishInfo> dishes, List<String> tastes, List<String> preferredDishes) {
        List<Map.Entry<DishInfo, Double>> dishScores = new ArrayList<>(dishes.size());
        for (DishInfo dish : dishes) {
            dishScores.add(new AbstractMap.SimpleEntry<>(dish, calculateSimilarity(dish, tastes, preferredDishes)));
        }
        return dishScores;
    }

    /**
     * ★★★ 核心推荐算法：加权余弦相似度 ★★★
     *
     * 把用户偏好和菜品特征都看作向量，计算它们之间的余弦夹角。
     * 夹角越小（值越接近1），说明这道菜越符合用户的口味。
     *
     * 向量维度：
     * - 用户偏好向量：每个口味偏好权重0.7，每个菜品偏好权重0.8
     * - 菜品特征向量：菜名维度权重0.8，口味维度0.8，食材维度0.65，描述维度0.25
     *
     * 公式：similarity = 点积 / (用户向量模 × 菜品向量模)
     *
     * @param dish             候选菜品
     * @param tastes           用户的口味偏好（已归一化展开）
     * @param preferredDishes  用户指定的菜品偏好（已归一化展开）
     * @return [0, 1] 之间的相似度
     */
    private double calculateSimilarity(DishInfo dish, List<String> tastes, List<String> preferredDishes) {
        // 构建菜品的特征向量：把菜名/口味/食材/描述映射为{前缀+词 → 权重}的Map
        Map<String, Double> dishFeatures = buildDishFeatures(dish);

        // 用户没有任何偏好，相似度直接为0
        if (tastes.isEmpty() && preferredDishes.isEmpty()) return 0.0;

        double dotProduct = 0.0;   // 分子：用户向量与菜品向量的点积
        double userNormSq = 0.0;   // 分母一部分：用户偏好向量模长的平方

        // ── 第一部分：口味匹配（用户偏好权重0.7） ──
        // 遍历用户说的每个口味，在菜品特征中查找匹配
        for (String taste : tastes) {
            if (taste == null || taste.trim().isEmpty()) continue;
            String raw = taste.trim();
            // 每个口味在用户偏好向量中贡献固定的模长：0.7²
            userNormSq += 0.7 * 0.7;

            // 同义词展开：比如"酸"→["酸","酸味","醋","糖醋","番茄"...]
            Set<String> expanded = new LinkedHashSet<>(expandSynonyms(raw));
            expanded.add(raw);

            boolean matchedTaste = false;

            // 优先在菜品的taste（口味）字段中匹配
            for (String exp : expanded) {
                Double w = dishFeatures.get("taste_" + exp);
                if (w != null && w > 0) {
                    dotProduct += 0.7 * w;    // 口味权重0.7 × 菜品特征权重
                    matchedTaste = true;
                }
            }

            // 口味字段没匹配到 → 尝试食材字段（强度减半）
            // 如"酸"不是菜品的标注口味，但菜品含"番茄"（食材），也算部分匹配
            if (!matchedTaste) {
                for (String exp : expanded) {
                    Double w = dishFeatures.get("ingredient_" + exp);
                    if (w != null && w > 0) {
                        dotProduct += 0.7 * w * 0.5;  // 食材匹配强度减半
                        break;
                    }
                }
            }
        }

        // ── 第二部分：菜品偏好匹配（用户偏好权重0.8） ──
        // 遍历用户提到的每个菜名，在菜品特征中查找匹配
        for (String dishName : preferredDishes) {
            if (dishName == null || dishName.trim().isEmpty()) continue;
            String raw = dishName.trim();
            // 每个菜品偏好贡献固定模长：0.8²
            userNormSq += 0.8 * 0.8;

            // 策略1：精确匹配菜名——用户说"水煮鱼"，菜名正好是"水煮鱼"
            Double w = dishFeatures.get("dish_" + raw);
            if (w != null && w > 0) {
                dotProduct += 0.8 * w;
            } else {
                // 策略2：精确匹配失败，尝试子串匹配（降权0.6）
                // 用户说"鱼"，菜名是"麻辣水煮鱼"→也能匹配上，但权重降低
                for (Map.Entry<String, Double> feature : dishFeatures.entrySet()) {
                    if (feature.getKey().startsWith("dish_") && feature.getKey().contains(raw)) {
                        dotProduct += 0.8 * feature.getValue() * 0.6;
                        break;
                    }
                }
            }
        }

        // 防御：用户向量模为0（无偏好）
        if (userNormSq == 0) return 0.0;

        // ── 第三部分：计算菜品向量模长 ──
        double dishNormSq = 0.0;
        for (double value : dishFeatures.values()) {
            dishNormSq += value * value;
        }
        if (dishNormSq == 0) return 0.0;  // 理论上不会发生

        // ── 最终余弦公式 ──
        return dotProduct / (Math.sqrt(userNormSq) * Math.sqrt(dishNormSq));
    }

    /**
     * 计算两个带权重特征集合的通用余弦相似度。
     * 比 calculateSimilarity 更通用，适用于UserCF中的用户-菜品特征匹配。
     * 不做维度拆分，直接全量匹配。
     */
    private double calculateWeightedCosineSimilarity(Map<String, Double> features1, Map<String, Double> features2) {
        if (features1.isEmpty() || features2.isEmpty()) return 0.0;
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        // 分别计算两个向量的模长平方
        for (double v : features1.values()) n1 += v * v;
        for (double v : features2.values()) n2 += v * v;
        // 计算点积：对features1每个维度，看features2中是否有相同key
        for (Map.Entry<String, Double> e : features1.entrySet()) dot += e.getValue() * features2.getOrDefault(e.getKey(), 0.0);
        if (n1 == 0 || n2 == 0) return 0.0;
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }


    // ========================================================================
    //  Debug信息收集：被屏蔽的菜、匹配特征、分数明细
    // ========================================================================

    /**
     * 收集被忌口过滤掉的菜品，供前端展示"为什么XX菜没推荐给你"。
     * 最多返回limit条，避免数据量过大。
     */
    private List<Map<String, Object>> collectBlockedByTaboo(List<DishInfo> allDishes, List<String> taboos, int limit) {
        List<String> normalizedTaboos = normalizeTerms(taboos);
        if (normalizedTaboos.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> blocked = new ArrayList<>();
        for (DishInfo dish : allDishes) {
            if (!containsTaboo(dish, normalizedTaboos)) {
                continue;  // 没被过滤掉的跳过
            }
            Map<String, Object> item = new HashMap<>();
            item.put("dishId", dish.getDishId());
            item.put("dishName", dish.getDishName());
            item.put("matchedTaboos", findMatchedTerms(dish, normalizedTaboos));  // 具体匹配了哪个忌口词
            blocked.add(item);
            if (blocked.size() >= limit) {
                break;
            }
        }
        return blocked;
    }

    /**
     * 构建推荐菜品的匹配特征详情：每道菜匹配了哪些口味和菜品偏好。
     * 用于前端展示"推荐理由"。
     */
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
            item.put("tasteMatches", findMatchedTasteTerms(dish, normalizedTastes));   // 匹配了哪些口味
            item.put("dishMatches", findMatchedDishTerms(dish, normalizedPreferredDishes)); // 匹配了哪些菜品偏好
            result.add(item);
        }
        return result;
    }

    /**
     * 构建每道推荐菜品的分数明细。
     * 包含：口味得分、菜品偏好转账、总相似度。
     * 用于debug和调优。
     */
    private List<Map<String, Object>> buildScoreBreakdown(List<DishInfo> dishes, List<String> tastes, List<String> preferredDishes) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (dishes == null || dishes.isEmpty()) {
            return result;
        }

        List<String> normalizedTastes = normalizeTerms(tastes);
        List<String> normalizedPreferredDishes = normalizeTerms(preferredDishes);

        for (DishInfo dish : dishes) {
            Map<String, Double> dishFeatures = buildDishFeatures(dish);
            // 口味维度的贡献分（不计入菜品偏好）
            double tasteScore = calculateFeatureContribution(normalizedTastes, "taste_", 0.7, dishFeatures);
            // 菜品偏好维度的贡献分
            double dishScore = calculateFeatureContribution(normalizedPreferredDishes, "dish_", 0.8, dishFeatures);
            // 总余弦相似度
            double similarity = calculateSimilarity(dish, normalizedTastes, normalizedPreferredDishes);

            Map<String, Object> item = new HashMap<>();
            item.put("dishId", dish.getDishId());
            item.put("dishName", dish.getDishName());
            item.put("tasteScore", tasteScore);      // 口味匹配得分
            item.put("dishScore", dishScore);         // 菜品偏好匹配得分
            item.put("similarity", similarity);       // 总余弦相似度
            result.add(item);
        }
        return result;
    }

    /**
     * 计算某维度的特征贡献分。
     * 比如用户说了"辣"和"酸"，计算这道菜在"taste_"维度上总共得了多少分。
     */
    private double calculateFeatureContribution(List<String> terms, String prefix, double featureWeight, Map<String, Double> dishFeatures) {
        if (terms == null || terms.isEmpty() || dishFeatures == null || dishFeatures.isEmpty()) {
            return 0.0;
        }
        // 同义词展开
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

    /** 查找菜品中具体匹配了哪些忌口词（用于前端展示"被哪个忌口号挡住了"） */
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

    /** 查找这道菜匹配了用户说的哪些口味 */
    private List<String> findMatchedTasteTerms(DishInfo dish, List<String> tastes) {
        Set<String> dishTasteSet = new LinkedHashSet<>(parseStringList(dish.getTaste()));
        String searchable = safeLower(dish.getDescription());
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String taste : normalizeTerms(tastes)) {
            for (String expanded : expandSynonyms(taste)) {
                // 在口味字段中精确匹配，或在描述中全文匹配
                if (dishTasteSet.contains(expanded) || searchable.contains(expanded.toLowerCase(Locale.ROOT))) {
                    matched.add(taste);
                    break;
                }
            }
        }
        return new ArrayList<>(matched);
    }

    /** 查找这道菜的菜名中是否包含用户说的偏好菜品关键词 */
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


    // ========================================================================
    //  入口3：根据用户ID推荐（已注册用户，从DB读偏好）
    // ========================================================================

    /**
     * 根据用户ID推荐菜品。
     *
     * 推荐策略优先级（从高到低）：
     * 1. 协同过滤 UserCF → 如果该用户有≥2条评分数据
     * 2. 冷启动 → 如果是新用户（无偏好），直接返回热门多样性推荐
     * 3. 内容推荐（default）→ 从UserPreference表读持久化偏好 + 历史偏好（降权）
     *
     * 历史偏好降权的目的：防止用户临时修改偏好后推荐结果剧烈跳变。
     * 当前偏好权重1.0/0.9，历史偏好权重0.4/0.3。
     */
    @Override
    public Object recommendDishesByUserId(Long userId, int page, int size) {
        String cacheKey = "recommendation:v2:user_id:" + userId + ":" + page + ":" + size;
        Object cachedResult = getCachedValue(cacheKey, Map.class, "从Redis缓存获取用户推荐结果");
        if (cachedResult != null) return cachedResult;

        List<DishInfo> availableDishes = getAvailableDishes();

        // ── 策略1：协同过滤 ──
        // 找跟当前用户评分习惯相似的其他用户，推荐他们喜欢的菜
        Page<DishInfo> userCfPage = recommendBySimilarUsers(userId, page, size, availableDishes);
        if (userCfPage != null && userCfPage.getRecords() != null && !userCfPage.getRecords().isEmpty()) {
            Map<String, Object> response = buildSourceResponse(userCfPage, SOURCE_USER_CF);
            cacheValue(cacheKey, response, 15, java.util.concurrent.TimeUnit.MINUTES, "用户协同过滤推荐结果已缓存到Redis");
            return response;
        }

        // ── 策略2：冷启动用户 ──
        // 判断标准：UserPreference表里没有该用户记录，或口味/食材字段全为空
        if (isColdStartUser(userId)) {
            Object result = recommendPopularDishes(userId, page, size);
            Map<String, Object> response = buildSourceResponse(result, SOURCE_POPULAR);
            cacheValue(cacheKey, response, 10, java.util.concurrent.TimeUnit.MINUTES, "冷启动推荐结果已缓存到Redis");
            return response;
        }

        // ── 策略3（默认）：基于偏好的内容推荐 ──
        // 从DB读取用户的持久化偏好
        UserPreference userPreference = userPreferenceService.getUserPreference(userId);
        List<String> taboos = parseStringList(userPreference.getTaboo());          // 持久化的忌口
        List<String> tastes = removeConflictingTastes(
                parseStringList(userPreference.getTaste()), taboos);               // 持久化的口味（去冲突）
        List<String> ingredients = parseStringList(userPreference.getIngredient()); // 持久化的食材偏好

        // 同时读取"历史偏好"——这是用户之前的偏好记录，降权使用
        // 目的：如果用户今天突然改了偏好（比如从"辣"改成"清淡"），
        // 推荐结果不会完全突变，仍然保留部分历史偏好的影响
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

        // 过滤忌口后，用带优先级的相似度计算
        List<DishInfo> filteredDishes = filterTabooDishes(availableDishes, taboos);
        List<Map.Entry<DishInfo, Double>> filteredScores = filteredDishes.stream()
                .map(dish -> new AbstractMap.SimpleEntry<>(dish,
                        calculateSimilarityWithPriority(dish, tastes, ingredients, hTastes, hIngredients)))
                .filter(entry -> entry.getValue() >= 0.1)       // 相似度太低不推荐
                .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())  // 降序
                .collect(Collectors.toList());

        // 设置百分制得分
        for (Map.Entry<DishInfo, Double> entry : filteredScores)
            entry.getKey().setMatchScore((double) Math.round(entry.getValue() * 100.0));

        // 如果没有匹配结果，回退热门推荐
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


    // ========================================================================
    //  优先级相似度（当前偏好高权重 + 历史偏好低权重）
    // ========================================================================

    /**
     * 计算菜品与用户偏好的相似度，区分当前偏好和历史偏好的权重。
     *
     * 权重设计：
     * - 当前口味：1.0（最高，用户当前的选择最重要）
     * - 当前食材：0.9
     * - 历史口味：0.4（仅当当前偏好中没有该口味时才加入）
     * - 历史食材：0.3（仅当当前偏好中没有该食材时才加入）
     *
     * 通过去重避免同一个口味被重复计算（当前和历史各算一次）。
     */
    private double calculateSimilarityWithPriority(DishInfo dish,
                                                   List<String> currentTastes, List<String> currentIngredients,
                                                   List<String> historyTastes, List<String> historyIngredients) {
        // 构建用户特征向量
        Map<String, Double> userFeatures = new HashMap<>();
        Set<String> currentTasteSet = new HashSet<>(normalizeTerms(currentTastes));
        Set<String> currentIngredientSet = new HashSet<>(normalizeTerms(currentIngredients));

        // 当前偏好：权重高
        addWeightedFeatures(userFeatures, "taste_", currentTasteSet, 1.0);
        addWeightedFeatures(userFeatures, "ingredient_", currentIngredientSet, 0.9);

        // 历史偏好：权重低，且只在当前偏好中没有时才加入（去重）
        for (String taste : normalizeTerms(historyTastes)) {
            if (!currentTasteSet.contains(taste))
                userFeatures.put("taste_" + taste, 0.4);
        }
        for (String ingredient : normalizeTerms(historyIngredients)) {
            if (!currentIngredientSet.contains(ingredient))
                userFeatures.put("ingredient_" + ingredient, 0.3);
        }

        // 与菜品特征向量做加权余弦
        return calculateWeightedCosineSimilarity(userFeatures, buildDishFeatures(dish));
    }


    // ========================================================================
    //  协同过滤：User-Based Collaborative Filtering
    // ========================================================================

    /**
     * 基于相似用户(UserCF)的推荐。
     *
     * 原理：找和当前用户评分习惯相似的其他用户，
     * 把他们喜欢但当前用户没吃过的菜推荐过来。
     *
     * 三个升级点：
     * 1) 中心化余弦(Adjusted Cosine)：减去用户平均分，消除"有人给分松有人给分紧"的偏差
     * 2) 显著性修正(Significance Weighting)：共同评分越少，相似度越被调低
     * 3) Top-K邻居：只取最相似的前20个用户，减少噪声
     */
    private Page<DishInfo> recommendBySimilarUsers(Long userId, int page, int size, List<DishInfo> availableDishes) {
        // 防御检查：如果没有评分Mapper或菜品数据，直接返回null
        if (dishRatingMapper == null || userId == null || availableDishes == null || availableDishes.isEmpty()) {
            return null;
        }

        // 获取全部用户的评分数据
        List<DishRating> allRatings = dishRatingMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        if (allRatings == null || allRatings.isEmpty()) {
            return null;
        }

        // 按userId分组，方便后面按用户查找
        Map<Long, List<DishRating>> ratingsByUser = allRatings.stream()
                .filter(r -> r.getUserId() != null && r.getDishId() != null && r.getScore() != null)
                .collect(Collectors.groupingBy(DishRating::getUserId));

        // 当前用户的评分数据
        List<DishRating> currentUserRatings = ratingsByUser.getOrDefault(userId, Collections.emptyList());
        if (currentUserRatings.size() < CF_MIN_COMMON_RATINGS) {
            return null;  // 评分太少，做CF没有意义
        }

        // 当前用户的评分Map：dishId → score
        Map<Long, Integer> currentScoreMap = currentUserRatings.stream()
                .collect(Collectors.toMap(DishRating::getDishId, DishRating::getScore, (a, b) -> b));
        if (currentScoreMap.size() < CF_MIN_COMMON_RATINGS) {
            return null;
        }

        // 当前用户的评分均值（用于中心化去偏）
        double currentUserMean = averageScore(currentScoreMap.values());

        // ── 找相似用户（邻居） ──
        // 对其他每个用户计算与当前用户的相似度
        List<Map.Entry<Long, CfNeighborSimilarity>> rankedNeighbors = ratingsByUser.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), userId))  // 排除自己
                .map(entry -> {
                    Long otherUserId = entry.getKey();
                    Map<Long, Integer> otherScoreMap = entry.getValue().stream()
                            .collect(Collectors.toMap(DishRating::getDishId, DishRating::getScore, (a, b) -> b));

                    double otherUserMean = averageScore(otherScoreMap.values());
                    CfNeighborSimilarity sim = centeredCosineWithSignificance(
                            currentScoreMap, otherScoreMap, currentUserMean, otherUserMean);
                    return new AbstractMap.SimpleEntry<>(otherUserId, sim);
                })
                .filter(entry -> entry.getValue().score() > 0.0)   // 相似度>0才有参考价值
                .sorted((a, b) -> Double.compare(b.getValue().score(), a.getValue().score()))  // 按相似度降序
                .limit(CF_TOP_K_NEIGHBORS)  // 只取Top 20
                .collect(Collectors.toList());

        if (rankedNeighbors.isEmpty()) {
            return null;
        }

        // 可推荐菜品的ID集合（只推上架菜）
        Set<Long> availableDishIds = availableDishes.stream()
                .map(DishInfo::getDishId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // ── 聚合邻居的评分，做加权预测 ──
        // numerator[dishId] = Σ(邻居相似度 × 邻居去均值评分)
        // denominator[dishId] = Σ(|邻居相似度|)
        Map<Long, Double> numerator = new HashMap<>();
        Map<Long, Double> denominator = new HashMap<>();

        for (Map.Entry<Long, CfNeighborSimilarity> neighbor : rankedNeighbors) {
            Long neighborUserId = neighbor.getKey();
            double sim = neighbor.getValue().score();  // 邻居与当前用户的相似度
            List<DishRating> neighborRatings = ratingsByUser.getOrDefault(neighborUserId, Collections.emptyList());
            Map<Long, Integer> neighborScoreMap = neighborRatings.stream()
                    .collect(Collectors.toMap(DishRating::getDishId, DishRating::getScore, (a, b) -> b));
            double neighborMean = averageScore(neighborScoreMap.values());

            for (DishRating rating : neighborRatings) {
                Long dishId = rating.getDishId();
                Integer score = rating.getScore();
                if (dishId == null || score == null) continue;
                if (currentScoreMap.containsKey(dishId)) continue;   // 跳过当前用户已评过分的菜
                if (!availableDishIds.contains(dishId)) continue;    // 只推荐当前上架的菜

                // 邻居的评分去均值后，乘以相似度作为权重
                numerator.merge(dishId, sim * (score - neighborMean), Double::sum);
                denominator.merge(dishId, Math.abs(sim), Double::sum);
            }
        }

        if (numerator.isEmpty()) {
            return null;
        }

        // 建立 dishId → DishInfo 的映射
        Map<Long, DishInfo> dishMap = availableDishes.stream()
                .collect(Collectors.toMap(DishInfo::getDishId, d -> d, (a, b) -> a));

        // ── 计算每个菜品的预测分 ──
        // 预测分 = 当前用户评分均值 + 邻居加权去均值贡献
        // 即：预测分 = 当前用户均值 + Σ(sim × (邻居评分 - 邻居均值)) / Σ(|sim|)
        List<Map.Entry<DishInfo, Double>> predicted = numerator.entrySet().stream()
                .map(entry -> {
                    Long dishId = entry.getKey();
                    double den = denominator.getOrDefault(dishId, 0.0);
                    if (den <= 0.0) return null;
                    DishInfo dish = dishMap.get(dishId);
                    if (dish == null) return null;
                    double predictedScore = currentUserMean + (entry.getValue() / den);
                    return new AbstractMap.SimpleEntry<>(dish, predictedScore);
                })
                .filter(Objects::nonNull)
                .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        if (predicted.isEmpty()) {
            return null;
        }

        // 将预测分（0-5分制）转为百分制展示
        for (Map.Entry<DishInfo, Double> entry : predicted) {
            entry.getKey().setMatchScore((double) Math.round(Math.min(1.0, entry.getValue() / 5.0) * 100.0));
        }

        return toPage(dishesFromScores(predicted), page, size);
    }


    // ========================================================================
    //  中心化余弦（Adjusted Cosine）+ 显著性修正
    // ========================================================================

    /**
     * 中心化余弦相似度 + 显著性修正。
     *
     * 中心化余弦（Adjusted Cosine）：
     * 普通余弦相似度直接用评分计算，但每个人评分标准不同——
     * 有人觉得"还行"就给5分，有人觉得"超好吃"才给4分。
     * 中心化就是减去用户评分均值，只看"相对于该用户平均水平的偏差"。
     *
     * 显著性修正：
     * 两个用户只共同评过1-2道菜，算出来的相似度不可靠。
     * 用 common/(common+8) 作为权重，共同评分越少，相似度越被衰减。
     *
     * @param left      当前用户的评分Map（dishId→score）
     * @param right     另一个用户的评分Map
     * @param leftMean  当前用户的评分均值
     * @param rightMean 另一个用户的评分均值
     * @return 修正后的相似度
     */
    private CfNeighborSimilarity centeredCosineWithSignificance(Map<Long, Integer> left,
                                                                Map<Long, Integer> right,
                                                                double leftMean,
                                                                double rightMean) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return new CfNeighborSimilarity(0.0, 0);
        }

        double dot = 0.0;        // 中心化后的点积
        double leftNorm = 0.0;   // 左向量模平方
        double rightNorm = 0.0;  // 右向量模平方
        int common = 0;          // 共同评分的菜品数量

        // 只在两用户都评过分的菜品上计算
        for (Map.Entry<Long, Integer> entry : left.entrySet()) {
            Long dishId = entry.getKey();
            Integer rv = right.get(dishId);  // 另一位用户的评分
            if (rv == null) continue;        // 没评过这道菜，跳过

            // 中心化：减去各自用户的评分均值
            double lv = entry.getValue() - leftMean;
            double rvd = rv - rightMean;
            dot += lv * rvd;          // 中心化后的点积
            leftNorm += lv * lv;      // 左向量模
            rightNorm += rvd * rvd;   // 右向量模
            common++;
        }

        // 共同评分太少或向量为零 → 相似度0
        if (common < CF_MIN_COMMON_RATINGS || leftNorm == 0.0 || rightNorm == 0.0) {
            return new CfNeighborSimilarity(0.0, common);
        }

        // 余弦公式
        double cosine = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        // 显著性修正：共同评分越多，越可信
        double significance = common / (double) (common + CF_SIGNIFICANCE_SHRINKAGE);
        double adjusted = cosine * significance;

        return new CfNeighborSimilarity(adjusted, common);
    }

    /** 计算一组评分的平均值 */
    private double averageScore(Collection<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * 邻居相似度的记录类。
     * score：修正后的相似度
     * commonCount：共同评分的菜品数
     */
    private record CfNeighborSimilarity(double score, int commonCount) {
    }


    // ========================================================================
    //  工具：构建带来源标签的响应
    // ========================================================================

    /** 包装推荐结果和来源信息 */
    private Map<String, Object> buildSourceResponse(Object recommendation, String source) {
        Map<String, Object> response = new HashMap<>();
        response.put("recommendation", recommendation);
        response.put("source", source);
        return response;
    }

    /**
     * 解析用户输入推荐的来源。
     * 如果临时偏好推荐清空退回了→POPULAR
     * 如果有偏好→CONTENT
     * 如果一开始就无偏好→POPULAR
     */
    private String resolveInputRecommendationSource(boolean hasTemporaryPreferences, boolean clearedTemporaryPreferences) {
        if (clearedTemporaryPreferences) {
            return SOURCE_POPULAR;
        }
        return hasTemporaryPreferences ? SOURCE_CONTENT : SOURCE_POPULAR;
    }


    // ========================================================================
    //  冷启动检测
    // ========================================================================

    /**
     * 判断用户是否为冷启动用户（新用户或没有偏好数据）。
     * 冷启动用户直接走热门多样性推荐，不做内容匹配。
     */
    private boolean isColdStartUser(Long userId) {
        try {
            UserPreference userPreference = userPreferenceService.getUserPreference(userId);
            // 用户没有偏好记录，或者偏好中的口味和食材都为空 → 冷启动
            return userPreference == null
                    || (!hasNonEmptyPreference(userPreference.getTaste())
                    && !hasNonEmptyPreference(userPreference.getIngredient()));
        } catch (Exception e) {
            return true;  // 查询异常也视为冷启动
        }
    }


    // ========================================================================
    //  热门多样性推荐（冷启动回退）
    // ========================================================================

    /**
     * 冷启动/无偏好时推荐热门菜品。
     * 结合评分和销量计算热度，并用 ensureDiversity 保证类别多样性。
     *
     * @param userId 可null，仅用于日志，不影响推荐逻辑
     */
    private Object recommendPopularDishes(Long userId, int page, int size) {
        String cacheKey = "recommendation:v2:popular:" + page + ":" + size;

        Object cachedResult = getCachedValue(cacheKey, Page.class, "从Redis缓存获取热门推荐结果");
        if (cachedResult != null) {
            return cachedResult;
        }

        // 对所有上架菜品按热度分排序
        List<Map.Entry<DishInfo, Double>> dishScores = getAvailableDishes().stream()
                .map(dish -> new AbstractMap.SimpleEntry<>(dish, calculatePopularityScore(dish)))
                .sorted(Map.Entry.<DishInfo, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        // 设置百分制得分
        for (Map.Entry<DishInfo, Double> entry : dishScores) {
            entry.getKey().setMatchScore((double) Math.round(entry.getValue() * 100.0));
        }

        // 确保多样性（每类取前几名），取前20个候选，再分页
        Page<DishInfo> dishPage = toPage(dishesFromScores(ensureDiversity(dishScores, 20)), page, size);
        cacheValue(cacheKey, dishPage, 1, java.util.concurrent.TimeUnit.HOURS, "热门推荐结果已缓存到Redis");
        return dishPage;
    }

    /**
     * 计算菜品的综合热度分。
     * 评分分（60%）+ 销量分（40%）
     *
     * 评分分：平均评分/5.0，无评分默认0.5
     * 销量分：min(heat/1000, 1.0)，无销量默认0.5
     */
    private double calculatePopularityScore(DishInfo dish) {
        // 评分分：有评分的按平均分算，无评分给中等分0.5
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

        // 销量分：销量越高分越高，上限1.0（销量1000以上即满分）
        double salesScore = dish.getHeat() != null && dish.getHeat() > 0
                ? Math.min(dish.getHeat() / 1000.0, 1.0)
                : 0.5;

        // 评分权重60%，销量权重40%
        return 0.6 * ratingScore + 0.4 * salesScore;
    }


    // ========================================================================
    //  口味与忌口冲突处理
    // ========================================================================

    /**
     * 去除与忌口冲突的口味。
     * 比如用户说"我喜欢吃辣的，但最近不能吃辣了"，
     * NLP提取出口味=["辣"]，忌口=["辣"]，
     * 如果不处理会导致又推荐辣菜又过滤辣菜的矛盾结果。
     * 这个函数会把"辣"从口味列表中移除。
     */
    private List<String> removeConflictingTastes(List<String> tastes, List<String> taboos) {
        Set<String> tabooSet = new HashSet<>(normalizeTerms(taboos));
        if (tabooSet.isEmpty()) return normalizeTerms(tastes);
        // 只保留不在忌口集合中的口味
        return normalizeTerms(tastes).stream()
                .filter(taste -> !tabooSet.contains(taste))
                .collect(Collectors.toList());
    }


    // ========================================================================
    //  多口味交错排序
    // ========================================================================

    /**
     * 当用户提到多个口味时（如"想吃辣的、酸的、甜的"），
     * 按口味交错排序，确保每种口味至少有一个代表进入结果前列。
     *
     * 排序规则：
     * 1. 匹配了多种口味的菜优先排前面（最贴近用户需求）
     * 2. 然后各口味轮询取菜，保证覆盖面
     * 3. 结果不超过size个
     */
    private List<Map.Entry<DishInfo, Double>> interleaveByTaste(
            List<Map.Entry<DishInfo, Double>> sorted,
            List<DishInfo> allDishes,
            List<String> tastes,
            int size) {
        // 如果菜品数量还没口味多，不用交错
        if (sorted.size() <= tastes.size()) return sorted;

        // 定义一个函数：判断一道菜是否匹配某个口味（含同义词展开）
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

        // 按口味分组
        Map<String, List<Map.Entry<DishInfo, Double>>> byTaste = new LinkedHashMap<>();
        for (String taste : tastes) byTaste.put(taste, new ArrayList<>());
        List<Map.Entry<DishInfo, Double>> multi = new ArrayList<>();  // 匹配多口味的菜

        for (Map.Entry<DishInfo, Double> entry : sorted) {
            DishInfo dish = entry.getKey();
            List<String> matched = new ArrayList<>();
            for (String taste : tastes) {
                if (matchesTaste.test(dish, taste)) matched.add(taste);
            }
            if (matched.size() > 1) multi.add(entry);          // 匹配多种口味 → 优先排
            else if (matched.size() == 1) byTaste.get(matched.get(0)).add(entry);  // 匹配单一口味
        }

        // 轮询各口味，保证每个口味都有菜上榜
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
                    if (dishId != null && seen.contains(dishId)) continue;  // 已存在去重
                    result.add(e);
                    if (dishId != null) seen.add(dishId);
                    anyLeft = true;
                    break;
                }
            }
        }
        return result;
    }


    // ========================================================================
    //  多样性保证与探索推荐
    // ========================================================================

    /**
     * 按菜品类别分组，每类取前几名，避免推荐结果全是同一类菜。
     * 比如不能推荐10个菜全是"川菜"类，每类均衡选取。
     * 用于热门推荐的多样性保证。
     */
    private List<Map.Entry<DishInfo, Double>> ensureDiversity(List<Map.Entry<DishInfo, Double>> dishScores, int maxDishes) {
        // 按categoryId分组
        Map<Long, List<Map.Entry<DishInfo, Double>>> categoryMap = dishScores.stream()
                .collect(Collectors.groupingBy(entry -> {
                    Long categoryId = entry.getKey().getCategoryId();
                    return categoryId == null ? 0L : categoryId;
                }));
        List<Map.Entry<DishInfo, Double>> diverseList = new ArrayList<>();
        // 每类平均分配
        int dishesPerCategory = Math.max(1, maxDishes / Math.max(1, categoryMap.size()));
        for (List<Map.Entry<DishInfo, Double>> categoryDishes : categoryMap.values())
            categoryDishes.stream().limit(dishesPerCategory).forEach(diverseList::add);
        // 再按分数降序排列
        diverseList.sort(Map.Entry.<DishInfo, Double>comparingByValue().reversed());
        return diverseList.size() > maxDishes ? diverseList.subList(0, maxDishes) : diverseList;
    }

    /**
     * 在推荐结果中混入约30%的跨类别菜品，作为"探索推荐"。
     * 只在首页（page=1）生效，后续分页保持原排序。
     *
     * 目的：防止推荐结果"信息茧房"，让用户发现新的菜品类别。
     * 探索菜品在description末尾标记"[探索推荐]"。
     */
    private Page<DishInfo> injectExploreDishes(Page<DishInfo> dishPage, List<DishInfo> allDishes,
                                                List<String> tastes, int page, int size) {
        if (dishPage == null || dishPage.getRecords() == null || dishPage.getRecords().isEmpty()) return dishPage;
        if (page > 1) return dishPage;  // 只在首页做探索注入

        List<DishInfo> current = new ArrayList<>(dishPage.getRecords());
        if (current.size() < 4) return dishPage;  // 菜太少不注入

        // 保留70%的高分推荐菜
        int keepCount = (int) Math.ceil(current.size() * 0.7);
        List<DishInfo> kept = new ArrayList<>(current.subList(0, Math.min(keepCount, current.size())));

        // 找出已展示的菜品类别和ID集合
        Set<Long> shownCategories = kept.stream().map(DishInfo::getCategoryId).filter(c -> c != null).collect(Collectors.toSet());
        Set<Long> shownIds = kept.stream().map(DishInfo::getDishId).collect(Collectors.toSet());

        // 构建探索池：区别于已展示类别的其他菜品
        List<DishInfo> explorePool = allDishes.stream()
                .filter(d -> d.getCategoryId() != null && !shownCategories.contains(d.getCategoryId()))  // 不同类别
                .filter(d -> !shownIds.contains(d.getDishId()))  // 没展示过
                .collect(Collectors.toList());
        Collections.shuffle(explorePool);  // 随机选取

        // 补满到size个
        int exploreCount = size - kept.size();
        List<DishInfo> explorers = explorePool.stream().limit(Math.max(1, exploreCount)).collect(Collectors.toList());

        // 混合并标记探索菜品
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
