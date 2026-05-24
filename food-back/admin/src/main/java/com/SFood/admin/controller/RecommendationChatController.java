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
 * 聊天推荐控制器 — 多轮对话式推荐、下单、点菜的核心入口。
 *
 * <p>用户通过自然语言与系统对话，控制器按意图分类路由到不同处理逻辑：
 * <ul>
 *   <li>RECOMMEND — 根据用户输入推荐菜品</li>
 *   <li>REFINE — 调整推荐（太辣了/换一批/不要这个）</li>
 *   <li>ORDER — 对话内下单（帮我点一份麻婆豆腐）</li>
 *   <li>CONFIRM_ORDER — 确认待确认的订单</li>
 *   <li>CANCEL_ORDER — 取消待确认的订单</li>
 *   <li>ADD_TO_CART — 加入购物车</li>
 *   <li>ASK_DISH — 查询菜品信息（宫保鸡丁辣不辣）</li>
 *   <li>EXPLAIN — 解释推荐原因</li>
 *   <li>CHAT — 闲聊/引导</li>
 * </ul>
 *
 * <p>对话状态（临时偏好、已展示菜品去重、待确认订单）保存在 Redis 中，
 * 以 sessionId 为隔离维度，支持多轮累积偏好 + 分页跟踪 + 订单确认流程。
 */
@RestController
@RequestMapping("/recommendation-chat")
public class RecommendationChatController {

    // ==================== 静态常量 ====================

    /** Redis key 前缀：会话级临时偏好（存储本轮累积的口味/忌口/菜品偏好） */
    private static final String TEMP_PREFERENCE_KEY_PREFIX = "chat_temp_preference:";
    /** Redis key 前缀：待确认订单（下单确认流程中使用） */
    private static final String PENDING_ORDER_KEY_PREFIX = "chat:pending_order:";
    /** 临时偏好的 Redis TTL：1小时 */
    private static final long TEMP_PREFERENCE_TTL_HOURS = 1;
    /** 待确认订单的 Redis TTL：5分钟（过期自动取消） */
    private static final long PENDING_ORDER_TTL_MINUTES = 5;

    /** 解析点单数量用正则：匹配"2份"、"一碗"、"3个"等 */
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+)\\s*(份|个|碗|盘|只|条|杯|斤|两|块)");
    /** 兜底菜品名称提取正则：匹配"帮我点一份宫保鸡丁"等句式，提取菜名 */
    private static final Pattern DISH_FALLBACK_PATTERN = Pattern.compile("([来帮要点]+)\\s*(\\d+)?\\s*(份|个|碗|盘|只|条|杯)?\\s*([\\u4e00-\\u9fa5]{2,10})");


    // ==================== 注入的依赖服务 ====================

    /** 推荐服务：执行推荐算法（内容/协同/热门） */
    @Autowired
    private RecommendationService recommendationService;

    /** NLP 服务：从自然语言中提取口味/忌口/菜品偏好 */
    @Autowired
    private NLPService nlpService;

    /** 菜品信息服务：查菜品详情、按名搜索 */
    @Autowired
    private DishInfoService dishInfoService;

    /** 推荐解释服务（可选）：用LLM生成推荐原因 */
    @Autowired(required = false)
    private RecommendationExplanationService recommendationExplanationService;

    /** 聊天记录持久化服务：记录每轮对话到数据库 */
    @Autowired
    private ChatRecordService chatRecordService;

    /**
     * Redis 模板：承担多项会话状态存储——
     * - 临时偏好（跨轮累积）
     * - 已展示菜品去重（避免"换一批"重复推荐）
     * - 分页计数器（跟踪推荐到了第几页）
     * - 待确认订单（下单确认流程）
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 购物车服务：加购和结算 */
    @Autowired
    private CartService cartService;

    /** 订单服务：创建订单 */
    @Autowired
    private OrderService orderService;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();


    // ========================================================================
    //  ★★★ 主入口：聊天 / 推荐 / 下单 ★★★
    // ========================================================================

    /**
     * 多轮对话的主端点。
     *
     * 接收用户自然语言输入，三步处理：
     * 1. 解析 sessionId（新会话自动生成）
     * 2. 分类意图（classifyIntent）
     * 3. 路由到对应的意图处理器
     *
     * @param request 请求体：{userInput, sessionId(可选), userId(可选)}
     * @return ResultDTO，内含 reply（回复文本）、recommendations（推荐菜品）、
     *         currentPreferences（本轮偏好）、usedPreferences（实际使用的偏好）等
     */
    @PostMapping("/chat")
    public ResultDTO chat(@RequestBody Map<String, Object> request) {
        try {
            String userInput = String.valueOf(request.getOrDefault("userInput", "")).trim();
            if (userInput.isEmpty()) {
                return ResultDTO.error("用户输入不能为空");
            }

            String sessionId = resolveSessionId(request.get("sessionId"));
            Long userId = parseUserId(request.get("userId"));

            // 意图分类：根据用户输入文本和当前会话状态判断用户想做什么
            String intent = classifyIntent(userInput, sessionId);

            // switch 表达式分发到不同的意图处理函数
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


    // ========================================================================
    //  意图分类（规则引擎）
    // ========================================================================

    // ── 各类意图的关键词定义 ──

    /** 下单意图关键词：用户明确说要下单/点菜 */
    private static final String[] ORDER_KW = {"帮我点", "来一份", "来一", "下单", "要一个", "要一份",
            "就要这个", "就这个", "点一份", "点一", "来一个", "给我来", "我要点"};
    /** 加购物车意图关键词 */
    private static final String[] CART_KW = {"加入购物车", "加到购物车", "先存着", "收藏", "加购物车"};
    /** 解释推荐原因意图关键词 */
    private static final String[] EXPLAIN_KW = {"为什么推荐", "为什么选这些", "解释一下", "原因"};
    /** 推荐意图关键词：触发完整的推荐管线 */
    private static final String[] RECOMMEND_KW = {"推荐", "推荐一下", "想吃", "想吃什么", "吃什么",
            "来点", "有什么", "有没有", "找一", "看看有什么", "介绍", "不要有", "不想吃"};
    /** 闲聊中用到的食物词——仅用来丰富闲聊回复，本身不触发推荐 */
    private static final String[] FOOD_KW = {"忌口", "清淡", "辣", "甜", "咸", "酸",
            "便宜", "下饭", "汤", "海鲜", "鸡", "牛", "猪", "素", "减脂", "口味", "菜"};
    /** 用于检测用户是否补充了口味信息 */
    private static final String[] TASTE_KW = {"辣", "甜", "酸", "咸", "苦", "清淡", "麻辣", "香辣", "酸甜"};
    /** 用于检测用户是否补充了忌口信息 */
    private static final String[] TABOO_KW = {"不要", "忌", "忌口", "不能吃", "过敏", "不吃"};


    /**
     * ★★★ 意图分类核心 ★★★
     *
     * 按优先级从高到低：
     * 1. 有待确认订单 → 判断是确认还是取消
     * 2. 明显操作词 → ORDER / ADD_TO_CART
     * 3. 疑问句式 + 已知菜名 → ASK_DISH
     * 4. 基于分数 → REFINE（8分以上）> EXPLAIN > RECOMMEND
     * 5. 已有累积偏好 + 输入含口味/忌口 → 偏好补充（触发推荐）
     * 6. 默认 → CHAT（闲聊引导）
     */
    private String classifyIntent(String input, String sessionId) {
        String text = input == null ? "" : input.trim().toLowerCase();

        // 1. 如果用户有待确认订单，优先判断确认/取消
        Map<String, Object> pendingOrder = getPendingOrder(sessionId);
        if (pendingOrder != null && !pendingOrder.isEmpty()) {
            if (matchesConfirmKeywords(text)) return "CONFIRM_ORDER";
            if (matchesCancelKeywords(text)) return "CANCEL_ORDER";
        }

        // 2. 明确操作词优先级最高
        if (scoreAny(text, ORDER_KW) > 0) return "ORDER";
        if (scoreAny(text, CART_KW) > 0) return "ADD_TO_CART";

        // 3. 疑问句式 + 匹配到已知菜名 → 询问菜品信息
        if (isAskDishIntent(text)) return "ASK_DISH";

        // 4. 基于分数判断
        int refineScore  = scoreRefine(text);
        int explainScore = scoreAny(text, EXPLAIN_KW);
        int recommendScore = scoreAny(text, RECOMMEND_KW);

        if (refineScore >= 8)  return "REFINE";      // "太辣了"/"换一批"等
        if (explainScore > 0)  return "EXPLAIN";     // "为什么推荐这些"
        if (recommendScore > 0) return "RECOMMEND";  // "推荐辣的"

        // 5. 已有累积偏好 + 用户新输入含口味/忌口词 → 视为补充偏好，重新推荐
        if (!isPreferenceEmpty(getTempPreference(sessionId))
                && (scoreAny(text, TASTE_KW) > 0 || scoreAny(text, TABOO_KW) > 0)) {
            return "RECOMMEND";
        }

        // 6. 无法判断意图 → CHAT，引导用户
        return "CHAT";
    }


    // ── 分数/匹配工具函数 ──

    /** 在文本中搜索关键词，找到任意一个返回1 */
    private int scoreAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return 1;
        }
        return 0;
    }

    /**
     * 检测精细化调整意图的分数。
     * 分数越高，越可能是 REFINE 意图：
     * - "太X了/啦" → 8分
     * - "有点X" → 6分
     * - "换一批/还有吗/再看看" → 10分
     * - "不要这个/换一个" → 10分
     * - "不太/有点不" → 4分
     * - "能不能别/不" → 5分
     */
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

    /** 匹配确认下单的关键词 */
    private boolean matchesConfirmKeywords(String text) {
        String[] keywords = {"确认", "好", "好的", "可以", "行", "下单", "没问题", "ok", "嗯", "对"};
        for (String kw : keywords) {
            if (text.equals(kw) || text.contains(kw)) return true;
        }
        return false;
    }

    /** 匹配取消下单的关键词 */
    private boolean matchesCancelKeywords(String text) {
        String[] keywords = {"取消", "算了", "不要了", "不了", "别", "放弃", "撤销"};
        for (String kw : keywords) {
            if (text.equals(kw) || text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 检测是否为询问菜品信息的意图。
     * 条件：必须是疑问句式（含吗/？/怎么样等）+ 输入匹配到某个现有菜名。
     * 如"宫保鸡丁辣不辣？"→匹配菜名"宫保鸡丁"→ASK_DISH
     */
    private boolean isAskDishIntent(String text) {
        boolean isQuestion = text.contains("吗") || text.contains("？") || text.contains("?")
                || text.contains("多少") || text.contains("怎么样") || text.contains("如何")
                || text.contains("是什么") || text.contains("有什么")
                || Pattern.compile(".{1,3}不.{1,3}").matcher(text).find();
        if (!isQuestion) return false;

        // 去掉标点符号后搜索菜品名
        List<DishInfo> dishes = dishInfoService.searchDishesByName(
                text.replaceAll("[？?吗啊呀呢吧！!，。,.]", "").trim());
        return dishes != null && !dishes.isEmpty();
    }


    // ========================================================================
    //  意图处理函数
    // ========================================================================

    // ── 辅助：偏好提取 ──

    /** 委托 NLP 服务从用户输入中提取偏好 */
    private Map<String, Object> extractPreferences(String userInput) {
        return nlpService.extractFoodPreferences(userInput);
    }


    // ── RECOMMEND：推荐 ──

    /**
     * 处理推荐意图。
     *
     * 核心逻辑：
     * 1. 用 NLP 提取本轮输入中的偏好
     * 2. 如果 Redis 中有历史累积偏好，合并（历史偏好 + 本轮偏好）
     * 3. 调 RecommendationService 计算推荐结果
     * 4. 去重：过滤掉当前会话已展示过的菜品
     * 5. 结果为空时的降级处理（先重试→再清空累积尝试→最终回退仅用本轮）
     * 6. 保存本轮偏讲到 Redis
     * 7. 初始化分页计数器（后续"换一批"从page=2开始）
     */
    private ResultDTO handleRecommendIntent(String userInput, String sessionId, Long userId) {
        // 用 NLP 从本轮输入中提取偏好
        Map<String, Object> currentPreferences = extractPreferences(userInput);
        // 从 Redis 读取历史累积偏好
        Map<String, Object> redisTempPreferences = getTempPreference(sessionId);
        boolean hasTempPreference = !isPreferenceEmpty(redisTempPreferences);

        // 合并历史+本轮偏好，同时做 sanitize（去除口味与忌口的冲突）
        Map<String, Object> usedPreferences = hasTempPreference
                ? sanitizePreferences(mergePreferences(redisTempPreferences, currentPreferences))
                : currentPreferences;

        // 推荐始终从 page=1 开始（最佳匹配），"换一批"流程处理翻页
        int page = 1;
        Object recommendResult = recommendationService.recommendDishesByPreferencesWithExplain(usedPreferences, page, 9);
        List<DishInfo> recommendedDishes = dedupDishes(sessionId, extractDishListFromExplain(recommendResult));

        boolean cleared = false;
        if (recommendedDishes.isEmpty() && hasTempPreference) {
            // 情况A：累积偏好无结果 → 先试page=1兜底（可能是分页空，不是偏好错误）
            Object retryResult = recommendationService.recommendDishesByPreferencesWithExplain(usedPreferences, 1, 9);
            List<DishInfo> retryDishes = dedupDishes(sessionId, extractDishListFromExplain(retryResult));
            if (!retryDishes.isEmpty()) {
                // 重试成功：保留累积偏好
                recommendResult = retryResult;
                recommendedDishes = retryDishes;
                saveTempPreference(sessionId, usedPreferences);
                initRecommendPage(sessionId);
            } else {
                // 情况B：累积偏好确实无匹配 → 尝试只用本轮输入
                usedPreferences = currentPreferences;
                recommendResult = recommendationService.recommendDishesByPreferencesWithExplain(usedPreferences, 1, 9);
                recommendedDishes = dedupDishes(sessionId, extractDishListFromExplain(recommendResult));
                if (!recommendedDishes.isEmpty()) {
                    // 本轮能出结果 → 替换累积偏好
                    clearTempPreference(sessionId);
                }
            }
            cleared = true;
        } else if (!recommendedDishes.isEmpty()) {
            // 正常有结果：保存偏好到 Redis，初始化分页
            saveTempPreference(sessionId, usedPreferences);
            initRecommendPage(sessionId);
        }

        // 构建回复文本（推荐解释）
        String reply = buildExplanation(userInput, usedPreferences, recommendedDishes);
        // 组装完整响应
        Map<String, Object> response = buildChatResponse(sessionId, usedPreferences, recommendedDishes,
                currentPreferences, !cleared && hasTempPreference, reply, recommendResult);

        saveChatRecord(userId, sessionId, userInput, "RECOMMEND", currentPreferences, reply);
        return ResultDTO.success(response);
    }


    // ── ORDER：对话内下单 ──

    /**
     * 处理下单意图。
     *
     * 流程：
     * 1. NLP 提取的菜品名（优先）→ 正则兜底提取菜名和数量
     * 2. 查数据库获取完整的菜品信息
     * 3. 精确匹配失败 → 模糊搜索（searchDishesByName）
     * 4. 数量默认1份
     * 5. 创建待确认订单保存到 Redis（5分钟过期）
     * 6. 等待用户"确认"或"取消"
     *
     * 不直接下单，先让用户确认，避免误下单。
     */
    private ResultDTO handleOrderIntent(String userInput, String sessionId, Long userId) {
        Map<String, Object> preferences = sanitizePreferences(nlpService.extractFoodPreferences(userInput));
        OrderSlots slots = parseOrderSlots(userInput, preferences);

        // 没提取到菜名 → 引导用户输入菜名
        if (slots.dishName == null || slots.dishName.isEmpty()) {
            String reply = "请问您想点哪道菜呢？可以告诉我具体的菜名，例如：帮我点一份麻婆豆腐。";
            saveChatRecord(userId, sessionId, userInput, "ORDER_FAILED", preferences, reply);
            return ResultDTO.success(chatReply(sessionId, reply, preferences));
        }

        // 精确匹配菜名
        DishInfo dish = dishInfoService.getDishByName(slots.dishName);
        if (dish == null) {
            // 精确没匹配到 → 模糊搜索
            List<DishInfo> fuzzyDishes = dishInfoService.searchDishesByName(slots.dishName);
            if (fuzzyDishes == null || fuzzyDishes.isEmpty()) {
                String reply = "抱歉，没有找到「" + slots.dishName + "」。请确认菜名是否正确，或尝试输入完整菜名。";
                saveChatRecord(userId, sessionId, userInput, "ORDER_FAILED", preferences, reply);
                return ResultDTO.success(chatReply(sessionId, reply, preferences));
            }
            // 取模糊搜索的第一个结果
            dish = fuzzyDishes.get(0);
        }

        // 计算数量和总价
        int quantity = slots.quantity > 0 ? slots.quantity : 1;
        BigDecimal unitPrice = dish.getPrice() != null ? dish.getPrice() : BigDecimal.ZERO;
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // 保存待确认订单到 Redis
        Map<String, Object> pendingOrder = new HashMap<>();
        pendingOrder.put("dishId", dish.getDishId());
        pendingOrder.put("dishName", dish.getDishName());
        pendingOrder.put("dishPrice", unitPrice);
        pendingOrder.put("dishImage", dish.getImageUrl());
        pendingOrder.put("quantity", quantity);
        pendingOrder.put("total", totalPrice);
        pendingOrder.put("createdAt", System.currentTimeMillis());
        savePendingOrder(sessionId, pendingOrder);

        // 回复：请用户确认或取消
        String reply = String.format("确认下单：%s × %d份，共 ¥%s。回复「确认」下单，或「取消」放弃。",
                dish.getDishName(), quantity, String.format("%.2f", totalPrice));
        Map<String, Object> response = chatReply(sessionId, reply, preferences);
        response.put("pendingOrder", pendingOrder);
        saveChatRecord(userId, sessionId, userInput, "ORDER_PENDING", preferences, reply);
        return ResultDTO.success(response);
    }


    // ── CONFIRM_ORDER：确认订单 ──

    /**
     * 确认待确认的订单。
     * 从 Redis 取待确认订单 → 加购物车 → 一键结算 → 返回订单结果。
     */
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

            // 创建购物车记录
            CartInfo cartInfo = new CartInfo();
            cartInfo.setUserId(userId);
            cartInfo.setDishId(dish.getDishId());
            cartInfo.setDishName(dish.getDishName());
            cartInfo.setDishPrice(dish.getPrice());
            cartInfo.setQuantity(quantity);
            cartInfo.setSubtotal(dish.getPrice() != null
                    ? dish.getPrice().multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO);
            CartInfo savedCart = cartService.addToCart(cartInfo);

            // 一键结算（从购物车创建订单）
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


    // ── CANCEL_ORDER：取消订单 ──

    /** 取消待确认的订单，清空 Redis 中的待确认记录 */
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


    // ── ADD_TO_CART：加购物车 ──

    /** 处理加购物车意图：提取菜名 → 查库 → 添加到购物车 */
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

        // 创建购物车记录并保存
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


    // ── EXPLAIN：解释推荐原因 ──

    /** 解释当前推荐的理由：从偏好出发，说明匹配了哪些口味/避开了哪些忌口 */
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


    // ── REFINE：精细化调整推荐 ──

    /**
     * 处理调整推荐意图（最复杂的逻辑）。
     *
     * 支持多种调整方式：
     * - "换一批"/"还有吗" → 翻页（page+1），保留当前累积偏好
     * - "不要这个"/"换一个" → 翻页，类似换一批
     * - "太辣了" → 提取"辣"作为临时忌口，增添"清淡"口味
     * - "有点X" → 将X加入临时忌口，尝试反口味
     * - "不要太X" → 将X加入临时忌口
     *
     * 每次调整后重新计算推荐，合并累积偏好。
     */
    private ResultDTO handleRefineIntent(String userInput, String sessionId, Long userId) {
        String text = userInput.trim().toLowerCase();

        // 从文本中提取用户不想要的东西（如"太辣了"→"辣"）
        String dislike = extractDislike(text);
        List<String> freshTaboos = new ArrayList<>();
        List<String> freshTastes = new ArrayList<>();

        // 如果提取到了"不喜欢"的东西，加入临时忌口，同时尝试推荐反口味
        if (dislike != null && !dislike.isEmpty()) {
            freshTaboos.add(dislike);
            String opposite = oppositeTaste(dislike);  // "辣"→"清淡"
            if (opposite != null) freshTastes.add(opposite);
        }

        Map<String, Object> redisPrefs = getTempPreference(sessionId);
        boolean hasPrefs = !isPreferenceEmpty(redisPrefs);

        // ── "换一批" / "还有吗" / "再看看" ──
        // 保持原有累积偏好不变，只翻页
        if (text.contains("换一批") || text.contains("还有吗") || text.contains("再看看")) {
            Map<String, Object> usedPrefs = hasPrefs ? redisPrefs : createEmptyPreference();
            int page = nextRecommendPage(sessionId);  // 页码递增
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

        // ── "不要这个" / "换一个" / "换一道" → 翻页 ──
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

        // ── "太X了" / "有点X" / "不太X" → 调整偏好 ──
        // 先提取本轮偏好，再合并新发现的忌口/口味
        Map<String, Object> currentPrefs = extractPreferences(userInput);
        if (!freshTaboos.isEmpty()) {
            currentPrefs = mergeTaboos(currentPrefs, freshTaboos);
        }
        if (!freshTastes.isEmpty()) {
            currentPrefs = mergePreferencesField(currentPrefs, "tastes", freshTastes);
        }

        // 合并历史累积偏好
        Map<String, Object> usedPrefs = hasPrefs
                ? sanitizePreferences(mergePreferences(redisPrefs, currentPrefs))
                : currentPrefs;
        Object result = recommendationService.recommendDishesByPreferencesWithExplain(usedPrefs, 1, 9);
        List<DishInfo> dishes = extractDishListFromExplain(result);

        // 结果为空且历史偏好存在 → 清空历史，只用本轮
        if (dishes.isEmpty() && hasPrefs) {
            clearTempPreference(sessionId);
            usedPrefs = currentPrefs;
            result = recommendationService.recommendDishesByPreferencesWithExplain(usedPrefs, 1, 9);
            dishes = extractDishListFromExplain(result);
        } else if (!dishes.isEmpty()) {
            saveTempPreference(sessionId, usedPrefs);
        }

        // 构建回复：提示用户避开了什么，推荐了什么口味
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

    /**
     * 从文本中提取用户不喜欢的口味关键词。
     * 支持模式：
     * - "太辣了" → "辣"
     * - "有点辣" → "辣"
     * - "不要太辣" → "辣"
     *
     * @return 提取到的口味词，没找到返回null
     */
    private String extractDislike(String text) {
        Matcher m = Pattern.compile("太([\\u4e00-\\u9fa5]{1,3})[了啦]").matcher(text);
        if (m.find()) return m.group(1);
        m = Pattern.compile("有点([\\u4e00-\\u9fa5]{1,3})").matcher(text);
        if (m.find()) return m.group(1);
        if (text.contains("不要太")) {
            m = Pattern.compile("不要太([\\u4e00-\\u9fa5]{1,3})").matcher(text);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /**
     * 反口味映射。
     * 用户说"太辣了"，系统推荐反口味"清淡"。
     * 用来在 REFINE 中做智能调整。
     */
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


    // ── ASK_DISH：询问菜品信息 ──

    /**
     * 处理询问菜品信息意图。
     * 如"宫保鸡丁辣不辣？"→回复菜品详情+针对性回答。
     * 支持问题：辣不辣、多少钱、成分等。
     */
    private ResultDTO handleAskDishIntent(String userInput, String sessionId, Long userId) {
        String clean = userInput.replaceAll("[？?吗啊呀呢吧！!，。,.]", "").trim();
        List<DishInfo> dishes = dishInfoService.searchDishesByName(clean);
        Map<String, Object> prefs = getTempPreference(sessionId);

        if (dishes == null || dishes.isEmpty()) {
            String reply = "抱歉，我不太了解这道菜。试试问我菜单上的菜品，比如「宫保鸡丁辣不辣？」";
            saveChatRecord(userId, sessionId, userInput, "ASK_DISH_NOTFOUND", prefs, reply);
            return ResultDTO.success(chatReply(sessionId, reply, prefs));
        }

        // 构建菜品信息回复
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

        // 针对性回答：根据用户问题的内容做定向回复
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


    // ── 价格排序工具 ──

    /**
     * 根据用户输入对菜品列表按价格排序。
     * 用户说"便宜"/"实惠"→升序；"贵"/"好一点"→降序。
     */
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


    // ── CHAT：闲聊 ──

    /**
     * 处理闲聊意图。
     * 如果用户提到食物相关关键词（"辣""清淡""鸡"等），引导用户说"推荐"。
     * 否则返回欢迎语 + 操作提示。
     */
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


    // ========================================================================
    //  订单槽位解析（从自然语言中提取菜名和数量）
    // ========================================================================

    /**
     * 解析用户输入中的下单信息：菜名 + 数量。
     * 解析策略：
     * 1. 优先用 BERT NER 提取的 dishes（最准确）
     * 2. 回退：正则 DISH_FALLBACK_PATTERN 兜底提取
     * 3. 数量用 QUANTITY_PATTERN 正则提取
     */
    private OrderSlots parseOrderSlots(String userInput, Map<String, Object> preferences) {
        OrderSlots slots = new OrderSlots();

        // 策略1：使用 BERT NER 提取的菜品（精准）
        Object dishesObj = preferences.get("dishes");
        if (dishesObj instanceof List<?> dishes && !dishes.isEmpty()) {
            slots.dishName = dishes.get(0).toString();
        }

        // 策略2：正则兜底提取（"帮我点一份宫保鸡丁"→提取"宫保鸡丁"）
        if (slots.dishName == null) {
            Matcher dishMatcher = DISH_FALLBACK_PATTERN.matcher(userInput);
            if (dishMatcher.find()) {
                String candidate = dishMatcher.group(4);
                if (candidate != null && candidate.length() >= 2) {
                    slots.dishName = candidate;
                }
            }
        }

        // 提取数量："2份"→2，"一份"→1
        Matcher qtyMatcher = QUANTITY_PATTERN.matcher(userInput);
        if (qtyMatcher.find()) {
            slots.quantity = Integer.parseInt(qtyMatcher.group(1));
        } else if (userInput.contains("一份") || userInput.contains("一个")) {
            slots.quantity = 1;
        }

        return slots;
    }

    /** 内部类：批处理单槽位（菜名+数量） */
    static class OrderSlots {
        String dishName;
        int quantity = 1;
    }


    // ========================================================================
    //  Redis 会话状态管理：待确认订单
    // ========================================================================

    /** 从 Redis 读取待确认订单 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getPendingOrder(String sessionId) {
        String key = PENDING_ORDER_KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /** 保存待确认订单到 Redis（5分钟 TTL） */
    private void savePendingOrder(String sessionId, Map<String, Object> order) {
        String key = PENDING_ORDER_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, order, PENDING_ORDER_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /** 删除待确认订单 */
    private void clearPendingOrder(String sessionId) {
        String key = PENDING_ORDER_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
    }


    // ========================================================================
    //  响应组装
    // ========================================================================

    /**
     * 构建简单的聊天响应（无推荐结果时使用）。
     * 返回基本字段：sessionId、回复文本、空推荐列表、偏好信息。
     */
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

    /**
     * 构建完整的聊天响应（有推荐结果时使用）。
     * 包含：推荐菜品列表、偏好信息、推荐详情、前端展示用的标签。
     */
    private Map<String, Object> buildChatResponse(String sessionId, Map<String, Object> usedPreferences,
                                                   List<DishInfo> recommendedDishes, Map<String, Object> currentPreferences,
                                                   boolean hasTempPreference, String reply, Object recommendResult) {
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("reply", reply);                                    // 回复文本
        response.put("recommendations", recommendedDishes);               // 推荐菜品列表
        response.put("recommendation", recommendResult);                  // 推荐详情（含分数等）
        response.put("currentPreferences", currentPreferences);           // 本轮输入提取的偏好
        response.put("usedPreferences", usedPreferences);                 // 实际使用的偏好（累积累后）
        response.put("updatedTempPreferences", usedPreferences);          // 更新后存Redis的偏好
        response.put("hasTempPreference", hasTempPreference || !isPreferenceEmpty(currentPreferences));
        response.put("tempPreferenceSummary", buildPreferenceSummary(usedPreferences));  // 前端展示用标签
        return response;
    }


    // ========================================================================
    //  会话级去重（防止"换一批"重复推荐同一道菜）
    // ========================================================================

    /**
     * 会话级去重：过滤掉当前会话已展示过的菜品。
     * 已展示的dishId存在Redis Set中，30分钟过期。
     *
     * 如果所有菜品都已展示过 → 重置Set，允许重新推荐（避免推无可推）。
     */
    private List<DishInfo> dedupDishes(String sessionId, List<DishInfo> dishes) {
        if (dishes == null || dishes.isEmpty()) return dishes;
        String key = "chat:shown_dishes:" + sessionId;
        List<DishInfo> fresh = new ArrayList<>();
        for (DishInfo d : dishes) {
            Long dishId = d.getDishId();
            if (dishId != null && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, dishId)))
                continue;  // 已展示过，跳过
            fresh.add(d);
        }
        // 全都被展示过了 → 重置Set，本次推荐当作新的
        if (fresh.isEmpty()) {
            redisTemplate.delete(key);
            return dishes;
        }
        // 标记为已展示
        for (DishInfo d : fresh) {
            if (d.getDishId() != null) redisTemplate.opsForSet().add(key, d.getDishId());
        }
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        return fresh;
    }


    // ========================================================================
    //  分页管理（"换一批"翻页）
    // ========================================================================

    /**
     * 初始化分页计数器为1。
     * 在推荐成功后调用，确保"换一批"从 page=2 开始。
     */
    private void initRecommendPage(String sessionId) {
        String key = "chat:rec_page:" + sessionId;
        redisTemplate.opsForValue().set(key, 1, 10, TimeUnit.MINUTES);
    }

    /**
     * 获取并递增分页计数器。
     * 首次调用返回2（因为init时是1，next时+1），后续递增。
     * 实现"换一批"翻页效果。
     */
    private int nextRecommendPage(String sessionId) {
        String key = "chat:rec_page:" + sessionId;
        Object val = redisTemplate.opsForValue().get(key);
        int page = (val instanceof Number n) ? n.intValue() : 0;
        page++;
        redisTemplate.opsForValue().set(key, page, 10, TimeUnit.MINUTES);
        return page;
    }


    // ========================================================================
    //  偏好字段合并工具
    // ========================================================================

    /** 合并两个偏好Map的taboos字段 */
    private Map<String, Object> mergeTaboos(Map<String, Object> prefs, List<String> newTaboos) {
        return mergePreferencesField(prefs, "taboos", newTaboos);
    }

    /**
     * 合并偏好Map的指定字段（tastes/taboos/dishes）。
     * 去重合并，保留原有内容 + 新增内容。
     */
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


    // ========================================================================
    //  解释生成
    // ========================================================================

    /**
     * 生成推荐解释文本。
     * 优先用 LLM 服务（如有配置），否则返回静态文案。
     */
    private String buildExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return "暂时没有匹配的菜品，请调整偏好试试。";
        }
        if (recommendationExplanationService != null) {
            return recommendationExplanationService.generateExplanation(userInput, preferences, dishes);
        }
        return "已根据您提供的信息完成推荐。";
    }


    // ========================================================================
    //  Session ID / User ID 解析
    // ========================================================================

    /** 解析 sessionId，为空则自动生成新的UUID */
    private String resolveSessionId(Object sessionIdObj) {
        if (sessionIdObj == null) return "chat_" + UUID.randomUUID();
        String sessionId = sessionIdObj.toString().trim();
        return sessionId.isEmpty() ? "chat_" + UUID.randomUUID() : sessionId;
    }

    /** 解析 userId，支持Number和String两种类型 */
    private Long parseUserId(Object userIdObj) {
        if (userIdObj == null) return null;
        try {
            if (userIdObj instanceof Number number) return number.longValue();
            return Long.parseLong(userIdObj.toString());
        } catch (Exception e) {
            return null;
        }
    }


    // ========================================================================
    //  推荐结果提取（兼容多种反序列化形态）
    // ========================================================================

    /**
     * 从 WithExplain 的返回 Map 中提取 DishInfo 列表。
     * 处理两层包装：外层Map含recommendation字段，内层是Page或Map。
     */
    private List<DishInfo> extractDishListFromExplain(Object recommendResult) {
        if (recommendResult instanceof Map<?, ?> map && map.containsKey("recommendation")) {
            return extractDishList(map.get("recommendation"));
        }
        return extractDishList(recommendResult);
    }

    /**
     * 从推荐结果中提取菜品列表。
     * 兼容 Page 对象和 Map（Jackson反序列化后的形态）两种形式。
     */
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

    /**
     * 将 LinkedHashMap 手动映射为 DishInfo 实体。
     * Jackson 反序列化 Page 时，泛型信息丢失，records中的元素会变成 LinkedHashMap，
     * 需要手动转回 DishInfo。
     */
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

    // 类型转换辅助函数
    private Long toLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private Integer toInteger(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private BigDecimal toBigDecimal(Object value) { return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null; }
    private String toString(Object value) { return value == null ? null : value.toString(); }


    // ========================================================================
    //  偏好管理（合并、去重、冲突处理、序列化）
    // ========================================================================

    /**
     * 合并历史偏好和当前偏好。
     * tastes/taboos/dishes 三个字段各自去重合并。
     * 用于多轮对话累积偏好：用户第一轮说"辣的"，第二轮说"不要香菜"，
     * 合并后：tastes=["辣"]，taboos=["香菜"]。
     */
    private Map<String, Object> mergePreferences(Map<String, Object> history, Map<String, Object> current) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("tastes", mergeList(history.get("tastes"), current.get("tastes")));
        merged.put("taboos", mergeList(history.get("taboos"), current.get("taboos")));
        merged.put("dishes", mergeList(history.get("dishes"), current.get("dishes")));
        return merged;
    }

    /** 合并两个列表，去重，保持顺序 */
    private List<String> mergeList(Object first, Object second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        append(set, first);
        append(set, second);
        return new ArrayList<>(set);
    }

    /** 将列表对象中的String提取到Set中（用于合并去重） */
    private void append(LinkedHashSet<String> set, Object listObj) {
        if (!(listObj instanceof List<?> list)) return;
        for (Object item : list) {
            if (item != null) {
                String value = item.toString().trim();
                if (!value.isEmpty()) set.add(value);
            }
        }
    }


    // ========================================================================
    //  Redis 会话状态管理：临时偏好
    // ========================================================================

    /**
     * 从 Redis 读取会话级临时偏好。
     * 返回包含 tastes/taboos/dishes 三个非空列表的 Map。
     * 如果 Redis 中没有或有但为空，返回空偏好结构。
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

    /** 将会话级临时偏好保存到 Redis，1小时自动过期 */
    private void saveTempPreference(String sessionId, Map<String, Object> preference) {
        String key = TEMP_PREFERENCE_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, preference, TEMP_PREFERENCE_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 删除会话级临时偏好 */
    private void clearTempPreference(String sessionId) {
        redisTemplate.delete(TEMP_PREFERENCE_KEY_PREFIX + sessionId);
    }

    /** 判断偏好 Map 是否无有效内容（三个列表都为空） */
    private boolean isPreferenceEmpty(Map<String, Object> preference) {
        if (preference == null) return true;
        return mergeList(preference.get("tastes"), null).isEmpty()
                && mergeList(preference.get("taboos"), null).isEmpty()
                && mergeList(preference.get("dishes"), null).isEmpty();
    }

    /** 创建一个空的偏好结构（三个空列表） */
    private Map<String, Object> createEmptyPreference() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("tastes", new ArrayList<String>());
        preferences.put("taboos", new ArrayList<String>());
        preferences.put("dishes", new ArrayList<String>());
        return preferences;
    }

    /**
     * 构建偏好摘要（用于前端展示）。
     * 将 tastes/taboos/dishes 转为统一格式的标签列表：
     * [{label:"口味", type:"taste", value:"辣"}, {label:"忌口", type:"taboo", value:"香菜"}]
     */
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

    /** 将偏好转为标签列表（前端Tag组件用） */
    private List<Map<String, String>> buildPreferenceLabels(Map<String, Object> preference) {
        List<Map<String, String>> labels = new ArrayList<>();
        appendPreferenceLabels(labels, "口味", "taste", preference.get("tastes"));
        appendPreferenceLabels(labels, "忌口", "taboo", preference.get("taboos"));
        appendPreferenceLabels(labels, "菜品", "dish", preference.get("dishes"));
        return labels;
    }

    /** 添加一个偏好维度到标签列表 */
    private void appendPreferenceLabels(List<Map<String, String>> labels, String label, String type, Object value) {
        for (String item : mergeList(value, null)) {
            Map<String, String> tag = new HashMap<>();
            tag.put("label", label);
            tag.put("type", type);
            tag.put("value", item);
            labels.add(tag);
        }
    }

    /**
     * 清洗/消毒偏好：去除 null 值、去重、去除口味中与忌口冲突的项。
     * 例：用户"喜欢辣但不能吃辣"→tastes中的"辣"被移除→不再推荐辣菜。
     */
    private Map<String, Object> sanitizePreferences(Map<String, Object> preference) {
        Map<String, Object> safe = preference == null ? createEmptyPreference() : new HashMap<>(preference);
        List<String> tastes = mergeList(safe.get("tastes"), null);
        List<String> taboos = mergeList(safe.get("taboos"), null);
        List<String> dishes = mergeList(safe.get("dishes"), null);
        // 关键：如果某个口味同时出现在忌口中，从口味中移除
        LinkedHashSet<String> tabooSet = new LinkedHashSet<>(taboos);
        tastes.removeIf(tabooSet::contains);
        safe.put("tastes", tastes);
        safe.put("taboos", taboos);
        safe.put("dishes", dishes);
        return safe;
    }


    // ========================================================================
    //  聊天记录持久化
    // ========================================================================

    /**
     * 将本轮聊天记录保存到数据库。
     * 记录：userId、sessionId、用户输入、意图分类、NLP提取结果、系统回复。
     * 用于历史查询和分析。
     */
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
