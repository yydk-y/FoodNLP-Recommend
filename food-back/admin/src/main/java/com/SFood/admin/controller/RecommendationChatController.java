package com.SFood.admin.controller;

import com.SFood.common.dto.ResultDTO;
import com.SFood.system.entity.CartInfo;
import com.SFood.system.entity.ChatRecord;
import com.SFood.system.entity.DishInfo;
import com.SFood.system.entity.OrderDetail;
import com.SFood.system.entity.OrderMain;
import com.SFood.system.service.CartService;
import com.SFood.system.service.ChatRecordService;
import com.SFood.system.service.DishInfoService;
import com.SFood.system.service.NLPService;
import com.SFood.system.service.OrderService;
import com.SFood.system.service.RecommendationExplanationService;
import com.SFood.system.service.RecommendationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天推荐控制器。
 *
 * <p>提供多轮对话式推荐、下单、点菜功能。
 * 用户通过自然语言输入，控制器按意图分类路由到不同处理逻辑：
 * - RECOMMEND: 根据输入推荐菜品
 * - REFINE: 调整/翻页/刷新推荐
 * - ORDER: 对话内下单
 * - EXPLAIN: 解释推荐原因
 * - ASK_DISH: 菜品信息查询
 * - CHAT: 闲聊回复
 *
 * <p>对话状态（临时偏好、已展示菜品、待确认订单）保存在 Redis 中，
 * 以 sessionId 为隔离维度，支持多轮累积。
 */
@RestController
@RequestMapping("/recommendation-chat")
public class RecommendationChatController {
    /** Redis key 前缀：会话级临时偏好 */
    private static final String TEMP_PREFERENCE_KEY_PREFIX = "chat_temp_preference:";
    /** Redis key 前缀：待确认订单 */
    private static final String PENDING_ORDER_KEY_PREFIX = "chat:pending_order:";
    /** 临时偏好的 TTL */
    private static final long TEMP_PREFERENCE_TTL_HOURS = 1;
    /** 待确认订单的 TTL */
    private static final long PENDING_ORDER_TTL_MINUTES = 5;

    /** 解析点单数量用：如"2份"、"一碗" */
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+)\\s*(份|个|碗|盘|只|条|杯|斤|两|块)");
    /** 兜底菜品名称提取：如"帮我点一份宫保鸡丁" */
    private static final Pattern DISH_FALLBACK_PATTERN = Pattern.compile("([来帮要点]+)\\s*(\\d+)?\\s*(份|个|碗|盘|只|条|杯)?\\s*([\\u4e00-\\u9fa5]{2,10})");

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private NLPService nlpService;

    @Autowired
    private DishInfoService dishInfoService;

    /** 推荐解释服务（可选，不可用时有 fallback） */
    @Autowired(required = false)
    private RecommendationExplanationService recommendationExplanationService;

    /** 聊天记录持久化服务 */
    @Autowired
    private ChatRecordService chatRecordService;

    /** Redis 操作：临时偏好、分页跟踪、已展示菜品去重、待确认订单 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Main Chat Entry ====================

    @PostMapping("/chat")
    public ResultDTO chat(@RequestBody Map<String, Object> request) {
        try {
            String userInput = String.valueOf(request.getOrDefault("userInput", "")).trim();
            if (userInput.isEmpty()) {
                return ResultDTO.error("用户输入不能为空");
            }

            String sessionId = resolveSessionId(request.get("sessionId"));
            Long userId = parseUserId(request.get("userId"));
            String intent = classifyIntent(userInput, sessionId);

            return switch (intent) {
                case "ORDER" -> handleOrderIntent(userInput, sessionId, userId);
                case "CONFIRM_ORDER" -> handleConfirmOrder(sessionId, userId);
                case "CANCEL_ORDER" -> handleCancelOrder(sessionId, userId);
                case "ADD_TO_CART" -> handleAddToCartIntent(userInput, sessionId, userId);
                case "ASK_DISH" -> handleAskDishIntent(userInput, sessionId, userId);
                case "EXPLAIN" -> handleExplainIntent(userInput, sessionId, userId);
                case "REFINE" -> handleRefineIntent(userInput, sessionId, userId);
                case "RECOMMEND" -> handleRecommendIntent(userInput, sessionId, userId);
                default -> handleChatIntent(userInput, sessionId, userId);
            };
        } catch (Exception e) {
            return ResultDTO.error("聊天处理失败: " + e.getMessage());
        }
    }

    // ==================== 意图分类 ====================

    /** 下单意图关键词 */
    private static final String[] ORDER_KW = {"帮我点", "来一份", "来一", "下单", "要一个", "要一份",
            "就要这个", "就这个", "点一份", "点一", "来一个", "给我来", "我要点"};
    /** 加购物车意图关键词 */
    private static final String[] CART_KW = {"加入购物车", "加到购物车", "先存着", "收藏", "加购物车"};
    /** 推荐解释意图关键词 */
    private static final String[] EXPLAIN_KW = {"为什么推荐", "为什么选这些", "解释一下", "原因"};
    /** 推荐意图关键词 — 触发完整的推荐管线 */
    private static final String[] RECOMMEND_KW = {"推荐", "推荐一下", "想吃", "想吃什么", "吃什么",
            "来点", "有什么", "有没有", "找一", "看看有什么", "介绍","不要有","不想吃"};
    /** 闲聊匹配用食物词 — 仅用于丰富闲聊回复，不触发推荐 */
    private static final String[] FOOD_KW = {"忌口", "清淡", "辣", "甜", "咸", "酸",
            "便宜", "下饭", "汤", "海鲜", "鸡", "牛", "猪", "素", "减脂", "口味", "菜"};
    /** 口味关键词 */
    private static final String[] TASTE_KW = {"辣", "甜", "酸", "咸", "苦", "清淡", "麻辣", "香辣", "酸甜"};
    /** 忌口表达关键词 */
    private static final String[] TABOO_KW = {"不要", "忌", "忌口", "不能吃", "过敏", "不吃"};

    private String classifyIntent(String input, String sessionId) {
        String text = input == null ? "" : input.trim().toLowerCase();

        // 1. Hard: pending order confirmation
        Map<String, Object> pendingOrder = getPendingOrder(sessionId);
        if (pendingOrder != null && !pendingOrder.isEmpty()) {
            if (matchesConfirmKeywords(text)) return "CONFIRM_ORDER";
            if (matchesCancelKeywords(text)) return "CANCEL_ORDER";
        }

        // 2. Hard: explicit action keywords win over everything
        if (scoreAny(text, ORDER_KW) > 0) return "ORDER";
        if (scoreAny(text, CART_KW) > 0) return "ADD_TO_CART";

        // 3. Ask dish: question pattern + known dish name
        if (isAskDishIntent(text)) return "ASK_DISH";

        // 4. Soft: score-based for the rest
        int refineScore  = scoreRefine(text);
        int explainScore = scoreAny(text, EXPLAIN_KW);
        int recommendScore = scoreAny(text, RECOMMEND_KW);

        // Only explicit recommendation-request words trigger RECOMMEND.
        // Food keywords alone do NOT — they go to CHAT for a helpful nudge instead.
        if (refineScore >= 8)  return "REFINE";
        if (explainScore > 0)  return "EXPLAIN";
        if (recommendScore > 0) return "RECOMMEND";
        // 已有累积偏好 + 输入含口味/忌口关键词 → 视为偏好补充，触发重新推荐
        if (!isPreferenceEmpty(getTempPreference(sessionId))
                && (scoreAny(text, TASTE_KW) > 0 || scoreAny(text, TABOO_KW) > 0)) {
            return "RECOMMEND";
        }
        return "CHAT";
    }

    private int scoreAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return 1;
        }
        return 0;
    }

    /** Detect pattern-based refinement intent. */
    private int scoreRefine(String text) {
        int score = 0;
        if (Pattern.compile("太.{1,3}[了啦]").matcher(text).find()) score += 8;
        if (Pattern.compile("有点.{1,3}").matcher(text).find()) score += 6;
        if (text.contains("换一批") || text.contains("还有吗") || text.contains("再看看")) score += 10;
        if (text.contains("不要这个") || text.contains("换一个") || text.contains("换一道")) score += 10;
        if (text.contains("不太") || text.contains("有点不")) score += 4;
        if (text.contains("能不能") && (text.contains("不") || text.contains("别"))) score += 5;
        return score;
    }

    private boolean matchesConfirmKeywords(String text) {
        String[] keywords = {"确认", "好", "好的", "可以", "行", "下单", "没问题", "ok", "嗯", "对"};
        for (String kw : keywords) {
            if (text.equals(kw) || text.contains(kw)) return true;
        }
        return false;
    }

    private boolean matchesCancelKeywords(String text) {
        String[] keywords = {"取消", "算了", "不要了", "不了", "别", "放弃", "撤销"};
        for (String kw : keywords) {
            if (text.equals(kw) || text.contains(kw)) return true;
        }
        return false;
    }

    // ==================== Intent Handlers ====================

    private Map<String, Object> extractPreferences(String userInput) {
        return nlpService.extractFoodPreferences(userInput);
    }

    private ResultDTO handleRecommendIntent(String userInput, String sessionId, Long userId) {
        Map<String, Object> currentPreferences = extractPreferences(userInput);
        Map<String, Object> redisTempPreferences = getTempPreference(sessionId);
        boolean hasTempPreference = !isPreferenceEmpty(redisTempPreferences);
        Map<String, Object> usedPreferences = hasTempPreference
                ? sanitizePreferences(mergePreferences(redisTempPreferences, currentPreferences))
                : currentPreferences;

        int page = 1; // 推荐始终从最佳匹配开始，"换一批"流程处理分页
        Object recommendResult = recommendationService.recommendDishesByPreferencesWithExplain(usedPreferences, page, 9);
        List<DishInfo> recommendedDishes = dedupDishes(sessionId, extractDishListFromExplain(recommendResult));

        boolean cleared = false;
        if (recommendedDishes.isEmpty() && hasTempPreference) {
            // 累积偏好无结果 → 先试 page 1（可能是分页空，不是偏好错误）
            Object retryResult = recommendationService.recommendDishesByPreferencesWithExplain(usedPreferences, 1, 9);
            List<DishInfo> retryDishes = dedupDishes(sessionId, extractDishListFromExplain(retryResult));
            if (!retryDishes.isEmpty()) {
                recommendResult = retryResult;
                recommendedDishes = retryDishes;
                saveTempPreference(sessionId, usedPreferences);
                initRecommendPage(sessionId);
            } else {
                // 累积偏好确实无匹配 → 尝试只用本轮输入
                usedPreferences = currentPreferences;
                recommendResult = recommendationService.recommendDishesByPreferencesWithExplain(usedPreferences, 1, 9);
                recommendedDishes = dedupDishes(sessionId, extractDishListFromExplain(recommendResult));
                if (!recommendedDishes.isEmpty()) {
                    clearTempPreference(sessionId); // 本轮能出 → 替换累积
                }
            }
            cleared = true;
        } else if (!recommendedDishes.isEmpty()) {
            saveTempPreference(sessionId, usedPreferences);
            // 初始化 page 计数器，确保"换一批"从 page 2 开始
            initRecommendPage(sessionId);
        }

        String reply = buildExplanation(userInput, usedPreferences, recommendedDishes);
        Map<String, Object> response = buildChatResponse(sessionId, usedPreferences, recommendedDishes,
                currentPreferences, !cleared && hasTempPreference, reply, recommendResult);

        saveChatRecord(userId, sessionId, userInput, "RECOMMEND", currentPreferences, reply);
        return ResultDTO.success(response);
    }

    private ResultDTO handleOrderIntent(String userInput, String sessionId, Long userId) {
        Map<String, Object> preferences = sanitizePreferences(nlpService.extractFoodPreferences(userInput));
        OrderSlots slots = parseOrderSlots(userInput, preferences);

        if (slots.dishName == null || slots.dishName.isEmpty()) {
            String reply = "请问您想点哪道菜呢？可以告诉我具体的菜名，例如：帮我点一份麻婆豆腐。";
            saveChatRecord(userId, sessionId, userInput, "ORDER_FAILED", preferences, reply);
            return ResultDTO.success(chatReply(sessionId, reply, preferences));
        }

        DishInfo dish = dishInfoService.getDishByName(slots.dishName);
        if (dish == null) {
            // Try fuzzy search
            List<DishInfo> fuzzyDishes = dishInfoService.searchDishesByName(slots.dishName);
            if (fuzzyDishes == null || fuzzyDishes.isEmpty()) {
                String reply = "抱歉，没有找到「" + slots.dishName + "」。请确认菜名是否正确，或尝试输入完整菜名。";
                saveChatRecord(userId, sessionId, userInput, "ORDER_FAILED", preferences, reply);
                return ResultDTO.success(chatReply(sessionId, reply, preferences));
            }
            dish = fuzzyDishes.get(0);
        }

        int quantity = slots.quantity > 0 ? slots.quantity : 1;
        BigDecimal unitPrice = dish.getPrice() != null ? dish.getPrice() : BigDecimal.ZERO;
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

        Map<String, Object> pendingOrder = new HashMap<>();
        pendingOrder.put("dishId", dish.getDishId());
        pendingOrder.put("dishName", dish.getDishName());
        pendingOrder.put("dishPrice", unitPrice);
        pendingOrder.put("dishImage", dish.getImageUrl());
        pendingOrder.put("quantity", quantity);
        pendingOrder.put("total", totalPrice);
        pendingOrder.put("createdAt", System.currentTimeMillis());
        savePendingOrder(sessionId, pendingOrder);

        String reply = String.format("确认下单：%s × %d份，共 ¥%s。回复「确认」下单，或「取消」放弃。",
                dish.getDishName(), quantity, String.format("%.2f", totalPrice));
        Map<String, Object> response = chatReply(sessionId, reply, preferences);
        response.put("pendingOrder", pendingOrder);
        saveChatRecord(userId, sessionId, userInput, "ORDER_PENDING", preferences, reply);
        return ResultDTO.success(response);
    }

    private ResultDTO handleConfirmOrder(String sessionId, Long userId) {
        Map<String, Object> pendingOrder = getPendingOrder(sessionId);
        if (pendingOrder == null || pendingOrder.isEmpty()) {
            String reply = "当前没有待确认的订单。您可以告诉我口味偏好或直接下单，例如：帮我点一份麻婆豆腐。";
            saveChatRecord(userId, sessionId, "确认下单", "CONFIRM_EMPTY", getTempPreference(sessionId), reply);
            return ResultDTO.success(chatReply(sessionId, reply, getTempPreference(sessionId)));
        }

        try {
            Long dishId = toLong(pendingOrder.get("dishId"));
            int quantity = Math.max(1, toInteger(pendingOrder.get("quantity")));
            DishInfo dish = dishInfoService.getDishById(dishId);
            if (dish == null) {
                clearPendingOrder(sessionId);
                return ResultDTO.success(chatReply(sessionId, "抱歉，该菜品已下架。", getTempPreference(sessionId)));
            }

            // Add to cart first
            CartInfo cartInfo = new CartInfo();
            cartInfo.setUserId(userId);
            cartInfo.setDishId(dish.getDishId());
            cartInfo.setDishName(dish.getDishName());
            cartInfo.setDishPrice(dish.getPrice());
            cartInfo.setQuantity(quantity);
            cartInfo.setSubtotal(dish.getPrice() != null
                    ? dish.getPrice().multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO);
            CartInfo savedCart = cartService.addToCart(cartInfo);

            // Batch checkout
            OrderMain order = cartService.batchCheckout(userId, Collections.singletonList(savedCart.getCartId()));
            clearPendingOrder(sessionId);

            String reply = String.format("已为您下单！订单号 #%s，共 ¥%s。",
                    order.getOrderNo() != null ? order.getOrderNo() : String.valueOf(order.getOrderId()),
                    order.getTotalPrice() != null ? String.format("%.2f", order.getTotalPrice()) : "0.00");
            Map<String, Object> response = chatReply(sessionId, reply, getTempPreference(sessionId));
            response.put("orderResult", order);
            saveChatRecord(userId, sessionId, "确认下单", "ORDER_CONFIRMED",
                    getTempPreference(sessionId), reply);
            return ResultDTO.success(response);
        } catch (Exception e) {
            clearPendingOrder(sessionId);
            return ResultDTO.error("下单失败: " + e.getMessage());
        }
    }

    private ResultDTO handleCancelOrder(String sessionId, Long userId) {
        Map<String, Object> pendingOrder = getPendingOrder(sessionId);
        if (pendingOrder == null || pendingOrder.isEmpty()) {
            return handleChatIntent("取消", sessionId, userId);
        }
        clearPendingOrder(sessionId);
        String dishName = String.valueOf(pendingOrder.getOrDefault("dishName", "菜品"));
        String reply = "已取消「" + dishName + "」的订单。";
        saveChatRecord(userId, sessionId, "取消下单", "ORDER_CANCELED", getTempPreference(sessionId), reply);
        return ResultDTO.success(chatReply(sessionId, reply, getTempPreference(sessionId)));
    }

    private ResultDTO handleAddToCartIntent(String userInput, String sessionId, Long userId) {
        Map<String, Object> preferences = sanitizePreferences(nlpService.extractFoodPreferences(userInput));
        OrderSlots slots = parseOrderSlots(userInput, preferences);

        if (slots.dishName == null || slots.dishName.isEmpty()) {
            String reply = "请问您想把哪道菜加入购物车呢？";
            saveChatRecord(userId, sessionId, userInput, "ADD_CART_FAILED", preferences, reply);
            return ResultDTO.success(chatReply(sessionId, reply, preferences));
        }

        DishInfo dish = dishInfoService.getDishByName(slots.dishName);
        if (dish == null) {
            String reply = "抱歉，没有找到「" + slots.dishName + "」。";
            saveChatRecord(userId, sessionId, userInput, "ADD_CART_FAILED", preferences, reply);
            return ResultDTO.success(chatReply(sessionId, reply, preferences));
        }

        int quantity = slots.quantity > 0 ? slots.quantity : 1;
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userId);
        cartInfo.setDishId(dish.getDishId());
        cartInfo.setDishName(dish.getDishName());
        cartInfo.setDishPrice(dish.getPrice());
        cartInfo.setQuantity(quantity);
        cartInfo.setSubtotal(dish.getPrice() != null
                ? dish.getPrice().multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO);
        cartService.addToCart(cartInfo);

        String reply = "已将「" + dish.getDishName() + "」× " + quantity + " 添加到购物车！";
        saveChatRecord(userId, sessionId, userInput, "ADD_TO_CART", preferences, reply);
        return ResultDTO.success(chatReply(sessionId, reply, preferences));
    }

    private ResultDTO handleExplainIntent(String userInput, String sessionId, Long userId) {
        Map<String, Object> preferences = getTempPreference(sessionId);
        if (isPreferenceEmpty(preferences)) {
            preferences = sanitizePreferences(nlpService.extractFoodPreferences(userInput));
        }

        String reply;
        if (preferences != null && !isPreferenceEmpty(preferences)) {
            List<?> tastes = (List<?>) preferences.getOrDefault("tastes", Collections.emptyList());
            List<?> taboos = (List<?>) preferences.getOrDefault("taboos", Collections.emptyList());
            reply = "根据对话中提到的偏好：口味=[" + String.join("、", tastes.stream().map(Object::toString).toList())
                    + "]，忌口=[" + String.join("、", taboos.stream().map(Object::toString).toList())
                    + "]。系统会优先推荐符合口味、避开忌口的菜品。";
        } else {
            reply = "请先告诉我您的口味偏好或点单需求，我会为您智能推荐合适的菜品。";
        }
        saveChatRecord(userId, sessionId, userInput, "EXPLAIN", preferences, reply);
        return ResultDTO.success(chatReply(sessionId, reply, preferences));
    }

    private ResultDTO handleRefineIntent(String userInput, String sessionId, Long userId) {
        String text = userInput.trim().toLowerCase();

        // Extract what user wants less/more of
        String dislike = extractDislike(text);
        List<String> freshTaboos = new ArrayList<>();
        List<String> freshTastes = new ArrayList<>();

        if (dislike != null && !dislike.isEmpty()) {
            freshTaboos.add(dislike);
            String opposite = oppositeTaste(dislike);
            if (opposite != null) freshTastes.add(opposite);
        }

        Map<String, Object> redisPrefs = getTempPreference(sessionId);
        boolean hasPrefs = !isPreferenceEmpty(redisPrefs);

        // "换一批" / "还有吗" / "再看看" — paginate with accumulated prefs
        if (text.contains("换一批") || text.contains("还有吗") || text.contains("再看看")) {
            Map<String, Object> usedPrefs = hasPrefs ? redisPrefs : createEmptyPreference();
            int page = nextRecommendPage(sessionId);
            Object result = recommendationService.recommendDishesByPreferencesWithExplain(usedPrefs, page, 9);
            List<DishInfo> dishes = dedupDishes(sessionId, extractDishListFromExplain(result));
            String reply = dishes.isEmpty()
                    ? "没有更多推荐了，试试调整口味偏好?"
                    : buildExplanation(userInput, usedPrefs, dishes);
            Map<String, Object> resp = buildChatResponse(sessionId, usedPrefs, dishes, usedPrefs,
                    hasPrefs, reply, result);
            saveChatRecord(userId, sessionId, userInput, "REFINE_REFRESH", usedPrefs, reply);
            return ResultDTO.success(resp);
        }

        // "不要这个" / "换一个" / "换一道" — next page with accumulated prefs
        if (text.contains("不要这个") || text.contains("换一个") || text.contains("换一道")) {
            Map<String, Object> usedPrefs = hasPrefs ? redisPrefs : createEmptyPreference();
            int page = nextRecommendPage(sessionId);
            Object result = recommendationService.recommendDishesByPreferencesWithExplain(usedPrefs, page, 9);
            List<DishInfo> dishes = dedupDishes(sessionId, extractDishListFromExplain(result));
            String reply = "好的，换一批推荐：";
            Map<String, Object> resp = buildChatResponse(sessionId, usedPrefs, dishes, usedPrefs,
                    hasPrefs, reply, result);
            saveChatRecord(userId, sessionId, userInput, "REFINE_SWAP", usedPrefs, reply);
            return ResultDTO.success(resp);
        }

        // "太X了" / "有点X" / "不太X" — adjust preferences, merge with accumulated
        Map<String, Object> currentPrefs = extractPreferences(userInput);
        if (!freshTaboos.isEmpty()) {
            currentPrefs = mergeTaboos(currentPrefs, freshTaboos);
        }
        if (!freshTastes.isEmpty()) {
            currentPrefs = mergePreferencesField(currentPrefs, "tastes", freshTastes);
        }

        Map<String, Object> usedPrefs = hasPrefs
                ? sanitizePreferences(mergePreferences(redisPrefs, currentPrefs))
                : currentPrefs;
        Object result = recommendationService.recommendDishesByPreferencesWithExplain(usedPrefs, 1, 9);
        List<DishInfo> dishes = extractDishListFromExplain(result);

        if (dishes.isEmpty() && hasPrefs) {
            clearTempPreference(sessionId);
            usedPrefs = currentPrefs;
            result = recommendationService.recommendDishesByPreferencesWithExplain(usedPrefs, 1, 9);
            dishes = extractDishListFromExplain(result);
        } else if (!dishes.isEmpty()) {
            saveTempPreference(sessionId, usedPrefs);
        }

        String reply;
        if (dislike != null && !dislike.isEmpty()) {
            reply = "已为您避开「" + dislike + "」口味";
            if (!freshTastes.isEmpty()) reply += "，试试「" + freshTastes.get(0) + "」的";
            reply += "：";
        } else {
            reply = "好的，已根据您的反馈调整推荐：";
        }

        Map<String, Object> resp = buildChatResponse(sessionId, usedPrefs, dishes, currentPrefs, hasPrefs, reply, result);
        saveChatRecord(userId, sessionId, userInput, "REFINE", currentPrefs, reply);
        return ResultDTO.success(resp);
    }

    private String extractDislike(String text) {
        java.util.regex.Matcher m = Pattern.compile("太([\\u4e00-\\u9fa5]{1,3})[了啦]").matcher(text);
        if (m.find()) return m.group(1);
        m = Pattern.compile("有点([\\u4e00-\\u9fa5]{1,3})").matcher(text);
        if (m.find()) return m.group(1);
        if (text.contains("不要太")) {
            m = Pattern.compile("不要太([\\u4e00-\\u9fa5]{1,3})").matcher(text);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private String oppositeTaste(String taste) {
        Map<String, String> opposites = new HashMap<>();
        opposites.put("辣", "清淡");
        opposites.put("麻辣", "清淡");
        opposites.put("麻", "清淡");
        opposites.put("咸", "清淡");
        opposites.put("甜", "微甜");
        opposites.put("油", "清淡");
        opposites.put("腻", "清爽");
        opposites.put("油腻", "清爽");
        opposites.put("酸", "微酸");
        return opposites.getOrDefault(taste, null);
    }

    private boolean isAskDishIntent(String text) {
        // Must contain a question marker
        boolean isQuestion = text.contains("吗") || text.contains("？") || text.contains("?")
                || text.contains("多少") || text.contains("怎么样") || text.contains("如何")
                || text.contains("是什么") || text.contains("有什么") || Pattern.compile(".{1,3}不.{1,3}").matcher(text).find();
        if (!isQuestion) return false;

        // Try to find a known dish name in the input
        List<DishInfo> dishes = dishInfoService.searchDishesByName(
                text.replaceAll("[？?吗啊呀呢吧！!，。,.]", "").trim());
        return dishes != null && !dishes.isEmpty();
    }

    private ResultDTO handleAskDishIntent(String userInput, String sessionId, Long userId) {
        String clean = userInput.replaceAll("[？?吗啊呀呢吧！!，。,.]", "").trim();
        List<DishInfo> dishes = dishInfoService.searchDishesByName(clean);
        Map<String, Object> prefs = getTempPreference(sessionId);

        if (dishes == null || dishes.isEmpty()) {
            String reply = "抱歉，我不太了解这道菜。试试问我菜单上的菜品，比如「宫保鸡丁辣不辣？」";
            saveChatRecord(userId, sessionId, userInput, "ASK_DISH_NOTFOUND", prefs, reply);
            return ResultDTO.success(chatReply(sessionId, reply, prefs));
        }

        DishInfo d = dishes.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("「").append(d.getDishName()).append("」");
        if (d.getTaste() != null && !d.getTaste().isEmpty())
            sb.append(" 口味：").append(d.getTaste());
        if (d.getIngredient() != null && !d.getIngredient().isEmpty())
            sb.append("，食材：").append(d.getIngredient());
        if (d.getPrice() != null)
            sb.append("，价格 ¥").append(String.format("%.0f", d.getPrice()));
        if (d.getDescription() != null && !d.getDescription().isEmpty())
            sb.append("。").append(d.getDescription());
        sb.append("。");

        // Direct question answering
        if (userInput.contains("辣")) {
            boolean hasSpicy = d.getTaste() != null && (d.getTaste().contains("辣") || d.getTaste().contains("麻"));
            sb.append(hasSpicy ? "这道菜偏辣。" : "这道菜不辣。");
        }
        if (userInput.contains("钱") || userInput.contains("贵") || userInput.contains("价格") || userInput.contains("多少")) {
            sb.append("价格 ¥").append(String.format("%.0f", d.getPrice())).append("。");
        }

        saveChatRecord(userId, sessionId, userInput, "ASK_DISH", prefs, sb.toString());
        return ResultDTO.success(chatReply(sessionId, sb.toString(), prefs));
    }

    private List<DishInfo> applyPriceSort(String userInput, List<DishInfo> dishes) {
        if (dishes == null || dishes.size() <= 1) return dishes;
        String text = userInput.toLowerCase();
        boolean cheap = text.contains("便宜") || text.contains("实惠") || text.contains("经济") || text.contains("省钱");
        boolean premium = text.contains("贵") || text.contains("好一点") || text.contains("高级") || text.contains("精致");
        if (!cheap && !premium) return dishes;

        List<DishInfo> sorted = new ArrayList<>(dishes);
        if (cheap) {
            sorted.sort(Comparator.comparing(d -> d.getPrice() != null ? d.getPrice() : BigDecimal.ZERO));
        } else {
            sorted.sort((a, b) -> {
                BigDecimal pa = a.getPrice() != null ? a.getPrice() : BigDecimal.ZERO;
                BigDecimal pb = b.getPrice() != null ? b.getPrice() : BigDecimal.ZERO;
                return pb.compareTo(pa);
            });
        }
        return sorted;
    }

    // Session-scoped dedup: prevent same dish appearing again on "换一批"
    private List<DishInfo> dedupDishes(String sessionId, List<DishInfo> dishes) {
        if (dishes == null || dishes.isEmpty()) return dishes;
        String key = "chat:shown_dishes:" + sessionId;
        List<DishInfo> fresh = new ArrayList<>();
        for (DishInfo d : dishes) {
            Long dishId = d.getDishId();
            if (dishId != null && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, dishId))) continue;
            fresh.add(d);
        }
        // All shown before — reset and return original
        if (fresh.isEmpty()) {
            redisTemplate.delete(key);
            return dishes;
        }
        // Mark as shown
        for (DishInfo d : fresh) {
            if (d.getDishId() != null) redisTemplate.opsForSet().add(key, d.getDishId());
        }
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        return fresh;
    }

    /** 初始化 page 计数器为 1，推荐成功后调用，确保"换一批"从 page 2 开始 */
    private void initRecommendPage(String sessionId) {
        String key = "chat:rec_page:" + sessionId;
        redisTemplate.opsForValue().set(key, 1, 10, TimeUnit.MINUTES);
    }

    /**
     * 返回会话的下一个推荐页码并递增计数器。
     * 计数器存储在 Redis，10 分钟 TTL。
     * 首次调用返回 1，后续递增。
     */
    private int nextRecommendPage(String sessionId) {
        String key = "chat:rec_page:" + sessionId;
        Object val = redisTemplate.opsForValue().get(key);
        int page = (val instanceof Number n) ? n.intValue() : 0;
        page++;
        redisTemplate.opsForValue().set(key, page, 10, TimeUnit.MINUTES);
        return page;
    }

    private Map<String, Object> mergeTaboos(Map<String, Object> prefs, List<String> newTaboos) {
        return mergePreferencesField(prefs, "taboos", newTaboos);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergePreferencesField(Map<String, Object> prefs, String field, List<String> additions) {
        Map<String, Object> merged = prefs == null ? createEmptyPreference() : new HashMap<>(prefs);
        Object existing = merged.getOrDefault(field, new ArrayList<>());
        List<String> list = existing instanceof List ? new ArrayList<>((List<String>) existing) : new ArrayList<>();
        for (String item : additions) {
            if (item != null && !item.isEmpty() && !list.contains(item)) list.add(item);
        }
        merged.put(field, list);
        return merged;
    }

    private ResultDTO handleChatIntent(String userInput, String sessionId, Long userId) {
        Map<String, Object> preferences = getTempPreference(sessionId);
        String reply;
        if (scoreAny(userInput.toLowerCase().trim(), FOOD_KW) > 0) {
            reply = "您提到了口味偏好，需要我推荐菜品吗？试试说「推荐辣的」或「推荐清淡的」～";
        } else {
            reply = "您好！我是AI点餐助手，可以为您推荐菜品、回答菜品问题、帮你下单。"
                    + "试试说「推荐辣的」「宫保鸡丁多少钱」或「帮我点一份麻婆豆腐」。";
        }
        saveChatRecord(userId, sessionId, userInput, "CHAT", preferences, reply);
        return ResultDTO.success(chatReply(sessionId, reply, preferences));
    }

    // ==================== Order Slots Parsing ====================

    private OrderSlots parseOrderSlots(String userInput, Map<String, Object> preferences) {
        OrderSlots slots = new OrderSlots();

        // 1. Use BERT NER extracted dishes
        Object dishesObj = preferences.get("dishes");
        if (dishesObj instanceof List<?> dishes && !dishes.isEmpty()) {
            slots.dishName = dishes.get(0).toString();
        }

        // 2. Fallback: regex extraction
        if (slots.dishName == null) {
            Matcher dishMatcher = DISH_FALLBACK_PATTERN.matcher(userInput);
            if (dishMatcher.find()) {
                String candidate = dishMatcher.group(4);
                if (candidate != null && candidate.length() >= 2) {
                    slots.dishName = candidate;
                }
            }
        }

        // 3. Extract quantity
        Matcher qtyMatcher = QUANTITY_PATTERN.matcher(userInput);
        if (qtyMatcher.find()) {
            slots.quantity = Integer.parseInt(qtyMatcher.group(1));
        } else if (userInput.contains("一份") || userInput.contains("一个")) {
            slots.quantity = 1;
        }

        return slots;
    }

    static class OrderSlots {
        String dishName;
        int quantity = 1;
    }

    // ==================== Pending Order Redis ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPendingOrder(String sessionId) {
        String key = PENDING_ORDER_KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private void savePendingOrder(String sessionId, Map<String, Object> order) {
        String key = PENDING_ORDER_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, order, PENDING_ORDER_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private void clearPendingOrder(String sessionId) {
        String key = PENDING_ORDER_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
    }

    // ==================== 响应组装 ====================

    private Map<String, Object> chatReply(String sessionId, String reply, Map<String, Object> preferences) {
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("reply", reply);
        response.put("recommendations", new ArrayList<>());
        response.put("currentPreferences", createEmptyPreference());
        response.put("usedPreferences", preferences != null ? preferences : createEmptyPreference());
        response.put("updatedTempPreferences", preferences != null ? preferences : createEmptyPreference());
        response.put("hasTempPreference", !isPreferenceEmpty(preferences));
        response.put("tempPreferenceSummary", buildPreferenceSummary(preferences != null ? preferences : createEmptyPreference()));
        return response;
    }

    private Map<String, Object> buildChatResponse(String sessionId, Map<String, Object> usedPreferences,
                                                   List<DishInfo> recommendedDishes, Map<String, Object> currentPreferences,
                                                   boolean hasTempPreference, String reply, Object recommendResult) {
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("reply", reply);
        response.put("recommendations", recommendedDishes);
        response.put("recommendation", recommendResult);
        response.put("currentPreferences", currentPreferences);
        response.put("usedPreferences", usedPreferences);
        response.put("updatedTempPreferences", usedPreferences);
        response.put("hasTempPreference", hasTempPreference || !isPreferenceEmpty(currentPreferences));
        response.put("tempPreferenceSummary", buildPreferenceSummary(usedPreferences));
        return response;
    }

    // ==================== Existing Explain Endpoints ====================

    @PostMapping("/explain-by-input")
    public ResultDTO explainByInput(@RequestBody Map<String, Object> request) {
        try {
            String userInput = String.valueOf(request.getOrDefault("userInput", "")).trim();
            if (userInput.isEmpty()) return ResultDTO.error("用户输入不能为空");

            String sessionId = resolveSessionId(request.get("sessionId"));
            Long userId = parseUserId(request.get("userId"));
            Map<String, Object> currentPreferences = sanitizePreferences(nlpService.extractFoodPreferences(userInput));

            Object recommendResult = recommendationService.recommendDishesByPreferencesWithExplain(currentPreferences, 1, 9);
            List<DishInfo> recommendedDishes = extractDishListFromExplain(recommendResult);

            String assistantReply = buildExplanation(userInput, currentPreferences, recommendedDishes);

            Map<String, Object> response = new HashMap<>();
            response.put("userInput", userInput);
            response.put("sessionId", sessionId);
            response.put("currentPreferences", currentPreferences);
            response.put("usedPreferences", currentPreferences);
            response.put("updatedTempPreferences", currentPreferences);
            response.put("hasTempPreference", false);
            response.put("clearedTempPreference", false);
            response.put("recommendation", recommendResult);
            response.put("assistantReply", assistantReply);
            saveChatRecord(userId, sessionId, userInput, "RECOMMEND_EXPLAIN", currentPreferences, assistantReply);
            return ResultDTO.success(response);
        } catch (Exception e) {
            return ResultDTO.error("聊天解释失败: " + e.getMessage());
        }
    }

    @PostMapping("/explain-selected-dishes")
    public ResultDTO explainSelectedDishes(@RequestBody Map<String, Object> request) {
        try {
            String userInput = String.valueOf(request.getOrDefault("userInput", "")).trim();
            if (userInput.isEmpty()) return ResultDTO.error("用户输入不能为空");
            String sessionId = resolveSessionId(request.get("sessionId"));
            Long userId = parseUserId(request.get("userId"));

            Object dishNamesObj = request.get("dishNames");
            if (!(dishNamesObj instanceof List<?> dishNamesRaw) || dishNamesRaw.isEmpty()) {
                return ResultDTO.error("dishNames不能为空");
            }

            List<DishInfo> selectedDishes = new ArrayList<>();
            List<String> notFoundNames = new ArrayList<>();
            for (Object item : dishNamesRaw) {
                String dishName = item == null ? "" : item.toString().trim();
                if (dishName.isEmpty()) continue;
                DishInfo dish = dishInfoService.getDishByName(dishName);
                if (dish != null) selectedDishes.add(dish);
                else notFoundNames.add(dishName);
            }
            if (selectedDishes.isEmpty()) return ResultDTO.error("未找到可解释的菜品");

            Map<String, Object> currentPreferences = sanitizePreferences(nlpService.extractFoodPreferences(userInput));
            Map<String, Object> redisTempPreferences = getTempPreference(sessionId);
            boolean hasTempPreference = !isPreferenceEmpty(redisTempPreferences);
            Map<String, Object> usedPreferences = hasTempPreference
                    ? sanitizePreferences(mergePreferences(redisTempPreferences, currentPreferences))
                    : currentPreferences;
            Map<String, Object> updatedTempPreferences = hasTempPreference
                    ? sanitizePreferences(mergePreferences(redisTempPreferences, currentPreferences))
                    : currentPreferences;
            saveTempPreference(sessionId, updatedTempPreferences);
            String explanation = buildExplanation(userInput, usedPreferences, selectedDishes);

            Map<String, Object> response = new HashMap<>();
            response.put("userInput", userInput);
            response.put("sessionId", sessionId);
            response.put("currentPreferences", currentPreferences);
            response.put("usedPreferences", usedPreferences);
            response.put("updatedTempPreferences", updatedTempPreferences);
            response.put("hasTempPreference", hasTempPreference);
            response.put("selectedDishes", selectedDishes);
            response.put("assistantReply", explanation);
            response.put("notFoundDishNames", notFoundNames);
            saveChatRecord(userId, sessionId, userInput, "SELECTED_DISH_EXPLAIN", currentPreferences, explanation);
            return ResultDTO.success(response);
        } catch (Exception e) {
            return ResultDTO.error("解释指定菜品失败: " + e.getMessage());
        }
    }

    @GetMapping("/temp-preference")
    public ResultDTO getTempPreferenceBySession(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) return ResultDTO.error("sessionId不能为空");
        return ResultDTO.success(getTempPreference(sessionId.trim()));
    }

    // ==================== Utility Methods ====================

    private String buildExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return "暂时没有匹配的菜品，请调整偏好试试。";
        }
        if (recommendationExplanationService != null) {
            return recommendationExplanationService.generateExplanation(userInput, preferences, dishes);
        }
        return "已根据您提供的信息完成推荐。";
    }

    private String resolveSessionId(Object sessionIdObj) {
        if (sessionIdObj == null) return "chat_" + UUID.randomUUID();
        String sessionId = sessionIdObj.toString().trim();
        return sessionId.isEmpty() ? "chat_" + UUID.randomUUID() : sessionId;
    }

    private Long parseUserId(Object userIdObj) {
        if (userIdObj == null) return null;
        try {
            if (userIdObj instanceof Number number) return number.longValue();
            return Long.parseLong(userIdObj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 推荐结果提取 ====================

    /** 从 WithExplain 的返回 Map 中提取菜品列表，去掉包装 */
    private List<DishInfo> extractDishListFromExplain(Object recommendResult) {
        if (recommendResult instanceof Map<?, ?> map && map.containsKey("recommendation")) {
            return extractDishList(map.get("recommendation"));
        }
        return extractDishList(recommendResult);
    }

    private List<DishInfo> extractDishList(Object recommendResult) {
        if (recommendResult instanceof Page<?> rawPage) {
            Page<DishInfo> dishPage = (Page<DishInfo>) rawPage;
            return dishPage.getRecords() == null ? new ArrayList<>() : dishPage.getRecords();
        }
        if (recommendResult instanceof Map<?, ?> map) {
            Object recordsObj = map.get("records");
            if (recordsObj instanceof List<?> records) {
                List<DishInfo> dishes = new ArrayList<>();
                for (Object item : records) {
                    if (item instanceof DishInfo dish) dishes.add(dish);
                    else if (item instanceof Map<?, ?> dishMap) dishes.add(mapToDishInfo(dishMap));
                }
                return dishes;
            }
        }
        return new ArrayList<>();
    }

    private DishInfo mapToDishInfo(Map<?, ?> map) {
        DishInfo dish = new DishInfo();
        dish.setDishId(toLong(map.get("dishId")));
        dish.setDishName(toString(map.get("dishName")));
        dish.setCategoryId(toLong(map.get("categoryId")));
        dish.setPrice(toBigDecimal(map.get("price")));
        dish.setTaste(toString(map.get("taste")));
        dish.setIngredient(toString(map.get("ingredient")));
        dish.setImageUrl(toString(map.get("imageUrl")));
        dish.setHeat(toInteger(map.get("heat")));
        dish.setDescription(toString(map.get("description")));
        dish.setStatus(toInteger(map.get("status")));
        return dish;
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }

    private String toString(Object value) {
        return value == null ? null : value.toString();
    }

    // ==================== Preference Management ====================

    private Map<String, Object> mergePreferences(Map<String, Object> history, Map<String, Object> current) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("tastes", mergeList(history.get("tastes"), current.get("tastes")));
        merged.put("taboos", mergeList(history.get("taboos"), current.get("taboos")));
        merged.put("dishes", mergeList(history.get("dishes"), current.get("dishes")));
        return merged;
    }

    private List<String> mergeList(Object first, Object second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        append(set, first);
        append(set, second);
        return new ArrayList<>(set);
    }

    private void append(LinkedHashSet<String> set, Object listObj) {
        if (!(listObj instanceof List<?> list)) return;
        for (Object item : list) {
            if (item != null) {
                String value = item.toString().trim();
                if (!value.isEmpty()) set.add(value);
            }
        }
    }

    // ==================== Redis 会话状态管理 ====================

    /**
     * 读取 Redis 中存储的会话级临时偏好。
     * 返回包含 tastes / taboos / dishes 三个非空列表的 Map。
     * 用于多轮对话累积用户偏好。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getTempPreference(String sessionId) {
        String key = TEMP_PREFERENCE_KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Map<?, ?> tempMap) {
            Map<String, Object> preference = new HashMap<>();
            Object t = tempMap.get("tastes");
            Object b = tempMap.get("taboos");
            Object d = tempMap.get("dishes");
            preference.put("tastes", t != null ? t : new ArrayList<String>());
            preference.put("taboos", b != null ? b : new ArrayList<String>());
            preference.put("dishes", d != null ? d : new ArrayList<String>());
            return sanitizePreferences(preference);
        }
        return createEmptyPreference();
    }

    /** 将会话级临时偏好保存到 Redis，1 小时 TTL */
    private void saveTempPreference(String sessionId, Map<String, Object> preference) {
        String key = TEMP_PREFERENCE_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, preference, TEMP_PREFERENCE_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 删除会话级临时偏好 */
    private void clearTempPreference(String sessionId) {
        redisTemplate.delete(TEMP_PREFERENCE_KEY_PREFIX + sessionId);
    }

    /** 判断偏好 Map 是否无有效内容 */
    private boolean isPreferenceEmpty(Map<String, Object> preference) {
        if (preference == null) return true;
        return mergeList(preference.get("tastes"), null).isEmpty()
                && mergeList(preference.get("taboos"), null).isEmpty()
                && mergeList(preference.get("dishes"), null).isEmpty();
    }

    /** 创建一个空偏好结构（三个空列表） */
    private Map<String, Object> createEmptyPreference() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("tastes", new ArrayList<String>());
        preferences.put("taboos", new ArrayList<String>());
        preferences.put("dishes", new ArrayList<String>());
        return preferences;
    }

    private Map<String, Object> buildPreferenceSummary(Map<String, Object> preference) {
        Map<String, Object> safe = sanitizePreferences(preference);
        Map<String, Object> summary = new HashMap<>();
        summary.put("tastes", safe.get("tastes"));
        summary.put("taboos", safe.get("taboos"));
        summary.put("dishes", safe.get("dishes"));
        summary.put("labels", buildPreferenceLabels(safe));
        summary.put("empty", isPreferenceEmpty(safe));
        return summary;
    }

    private List<Map<String, String>> buildPreferenceLabels(Map<String, Object> preference) {
        List<Map<String, String>> labels = new ArrayList<>();
        appendPreferenceLabels(labels, "口味", "taste", preference.get("tastes"));
        appendPreferenceLabels(labels, "忌口", "taboo", preference.get("taboos"));
        appendPreferenceLabels(labels, "菜品", "dish", preference.get("dishes"));
        return labels;
    }

    private void appendPreferenceLabels(List<Map<String, String>> labels, String label, String type, Object value) {
        for (String item : mergeList(value, null)) {
            Map<String, String> tag = new HashMap<>();
            tag.put("label", label);
            tag.put("type", type);
            tag.put("value", item);
            labels.add(tag);
        }
    }

    private Map<String, Object> sanitizePreferences(Map<String, Object> preference) {
        Map<String, Object> safe = preference == null ? createEmptyPreference() : new HashMap<>(preference);
        List<String> tastes = mergeList(safe.get("tastes"), null);
        List<String> taboos = mergeList(safe.get("taboos"), null);
        List<String> dishes = mergeList(safe.get("dishes"), null);
        LinkedHashSet<String> tabooSet = new LinkedHashSet<>(taboos);
        tastes.removeIf(tabooSet::contains);
        safe.put("tastes", tastes);
        safe.put("taboos", taboos);
        safe.put("dishes", dishes);
        return safe;
    }

    // ==================== Chat Record Persistence ====================

    private void saveChatRecord(Long userId, String sessionId, String userInput, String bertIntent,
                                Map<String, Object> bertExtract, String systemReply) {
        try {
            ChatRecord chatRecord = new ChatRecord();
            chatRecord.setUserId(userId == null ? 0L : userId);
            chatRecord.setSessionId(sessionId);
            chatRecord.setUserInput(userInput);
            chatRecord.setBertIntent(bertIntent);
            chatRecord.setBertExtract(objectMapper.writeValueAsString(bertExtract));
            chatRecord.setSystemReply(systemReply);
            chatRecord.setChatTime(LocalDateTime.now());
            chatRecordService.saveChatRecord(chatRecord);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
