package com.SFood.system.service.impl;

import com.SFood.system.entity.DishInfo;
import com.SFood.system.service.RecommendationExplanationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于大语言模型（如GPT）的推荐解释服务。
 *
 * 当用户输入"我想吃辣的"后，系统推荐了"麻辣水煮鱼"，
 * 这个服务负责生成"因为你说了想吃辣的，麻辣水煮鱼口味偏辣，匹配度高"这样的自然语言解释。
 *
 * 如果LLM服务不可用（未配置API Key等），会回退到基于规则的简单模板解释。
 */
@Service
@Primary
@Slf4j
public class LLMRecommendationExplanationServiceImpl implements RecommendationExplanationService {

    // ==================== 依赖注入 ====================

    /** RestTemplate：发送HTTP请求到LLM API */
    private final RestTemplate restTemplate;

    /** ObjectMapper：解析LLM API返回的JSON响应 */
    private final ObjectMapper objectMapper;

    /** 是否启用LLM解释功能（配置项：llm.enabled，默认false） */
    @Value("${llm.enabled:false}")
    private boolean llmEnabled;

    /** LLM API的基础地址（配置项：llm.base-url，默认OpenAI地址） */
    @Value("${llm.base-url:https://api.openai.com}")
    private String llmBaseUrl;

    /** LLM API的密钥（配置项：llm.api-key） */
    @Value("${llm.api-key:}")
    private String llmApiKey;

    /** 使用的LLM模型名称（配置项：llm.model，默认gpt-4o-mini） */
    @Value("${llm.model:gpt-4o-mini}")
    private String llmModel;


    // ==================== 构造函数 ====================

    /** 初始化HTTP客户端和JSON解析器 */
    public LLMRecommendationExplanationServiceImpl() {
        this.restTemplate = new RestTemplate();     // 用于调用LLM的REST API
        this.objectMapper = new ObjectMapper();     // 用于解析API返回的JSON
    }


    // ========================================================================
    //  核心方法：生成推荐解释
    // ========================================================================

    /**
     * 生成推荐结果的自然语言解释。
     *
     * 流程：
     * 1. 无推荐菜品 → 返回默认提示
     * 2. LLM未配置或不可用 → 使用规则回退解释
     * 3. 调用LLM API获取解释 → 成功则返回，失败则回退
     *
     * @param userInput         用户原始的输入文本，如"我想吃辣的"
     * @param preferences       用户偏好Map，含tastes/taboos/dishes三个key
     * @param recommendedDishes 系统推荐的菜品列表
     * @return 自然语言解释文本
     */
    @Override
    public String generateExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        // 场景1：没有推荐结果
        if (recommendedDishes == null || recommendedDishes.isEmpty()) {
            log.info("LLM解释跳过：无可推荐菜品");
            return "当前没有可推荐菜品，可能是筛掉忌口后无匹配结果。";
        }

        // 场景2：LLM未启用或API Key没配 → 用规则模板
        // 配置项在 application.yml 中：llm.enabled=true 和 llm.api-key=xxx
        if (!llmEnabled || llmApiKey == null || llmApiKey.isBlank()) {
            log.info("LLM解释回退：llm.enabled={} 或 api-key 未配置", llmEnabled);
            return buildFallbackExplanation(preferences, recommendedDishes);
        }

        // 场景3：调用LLM
        try {
            // 构建提示词：把用户输入、偏好、推荐结果拼成一段文字发给LLM
            String prompt = buildPrompt(userInput, preferences, recommendedDishes);
            // 调用LLM的chat completion API
            String llmResponse = callChatCompletion(prompt);

            // LLM返回空 → 回退规则解释
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM解释回退：模型返回空内容");
                return buildFallbackExplanation(preferences, recommendedDishes);
            }

            log.info("LLM解释成功：model={}, baseUrl={}", llmModel, llmBaseUrl);
            return llmResponse.trim();
        } catch (Exception e) {
            // 任何异常都走回退
            log.warn("LLM解释回退：调用异常 -> {}", e.getMessage());
            return buildFallbackExplanation(preferences, recommendedDishes);
        }
    }


    // ========================================================================
    //  调用LLM的Chat Completion API（兼容OpenAI格式）
    // ========================================================================

    /**
     * 调用LLM的 chat completion 接口。
     * 兼容OpenAI格式的API（/v1/chat/completions）。
     *
     * @param prompt 发给LLM的用户提示词
     * @return LLM生成的解释文本，失败返回null
     * @throws Exception 网络错误、API异常等
     */
    private String callChatCompletion(String prompt) throws Exception {
        // 构建请求URL：确保不以/slash结尾
        String endpoint = llmBaseUrl.endsWith("/")
                ? llmBaseUrl + "v1/chat/completions"
                : llmBaseUrl + "/v1/chat/completions";

        // 构建请求体（Map→JSON）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmModel);             // 模型名称，如gpt-4o-mini
        requestBody.put("temperature", 0.4);            // 低温度→输出更确定，不太发散

        // 构建 messages 数组
        List<Map<String, String>> messages = new ArrayList<>();

        // System消息：设定助手的角色和行为
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是餐饮推荐解释助手。请用简洁中文解释推荐依据，长度控制在120字以内。");
        messages.add(systemMessage);

        // User消息：包含用户输入、偏好、推荐结果
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        // 设置HTTP请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);   // Content-Type: application/json
        headers.setBearerAuth(llmApiKey);                       // Authorization: Bearer xxx

        // 发送POST请求
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, requestEntity, String.class);

        // 检查响应状态
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        // 解析JSON响应，提取choices[0].message.content
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }

        return choices.get(0).path("message").path("content").asText(null);
    }


    // ========================================================================
    //  构建LLM提示词
    // ========================================================================

    /**
     * 构建发给LLM的提示词。
     * 包含：用户原始输入、BERT提取的关键词（口味/忌口/菜品偏好）、推荐结果摘要。
     *
     * 提示词结构：
     * - 用户原始输入：xxx
     * - BERT关键词-口味：辣、酸
     * - BERT关键词-忌口：香菜
     * - BERT关键词-偏好菜品：水煮鱼
     * - 推荐结果：麻辣水煮鱼(口味:辣,麻,匹配度:92)；酸菜鱼(口味:酸,辣,匹配度:85)
     * - 请解释为什么推荐这些菜...
     */
    private String buildPrompt(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        // 格式化偏好信息为可读字符串
        String tastes = formatList(preferences.get("tastes"));      // 用户的口味偏好
        String taboos = formatList(preferences.get("taboos"));     // 用户的忌口
        String dishes = formatList(preferences.get("dishes"));     // 用户偏好的菜品

        // 构建推荐结果摘要：菜名(口味:xx,匹配度:xx)
        String recommendationBrief = recommendedDishes.stream()
                .map(d -> {
                    String score = d.getMatchScore() != null ? "匹配度:" + d.getMatchScore().intValue() : "";
                    return String.format("%s(口味:%s%s)",
                            safe(d.getDishName()),
                            safe(d.getTaste()),
                            score.isEmpty() ? "" : "," + score);
                })
                .collect(Collectors.joining("；"));

        // 组装完整提示词
        return "用户原始输入：" + safe(userInput) + "\n"
                + "BERT关键词-口味：" + tastes + "\n"
                + "BERT关键词-忌口：" + taboos + "\n"
                + "BERT关键词-偏好菜品：" + dishes + "\n"
                + "推荐结果：" + recommendationBrief + "\n"
                + "请解释为什么推荐这些菜，突出\"匹配口味、规避忌口、与偏好菜品的关联\"。";
    }


    // ========================================================================
    //  回退解释（LLM不可用时使用规则模板）
    // ========================================================================

    /**
     * 当LLM不可用时，使用基于规则的固定模板生成解释。
     * 格式固定但信息完整，确保用户能理解推荐理由。
     *
     * 模板：
     * "已根据你偏好的口味（辣、酸）和关注菜品（水煮鱼）进行匹配，
     *  并尽量避开忌口（香菜），因此优先推荐：麻辣水煮鱼、酸菜鱼。"
     */
    private String buildFallbackExplanation(Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        String tastes = formatList(preferences.get("tastes"));          // 口味偏好文字
        String taboos = formatList(preferences.get("taboos"));          // 忌口文字
        String preferred = formatList(preferences.get("dishes"));       // 偏好菜品文字

        // 提取推荐菜名，用顿号分隔
        String dishNames = recommendedDishes.stream()
                .map(DishInfo::getDishName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));

        return String.format(
                "已根据你偏好的口味（%s）和关注菜品（%s）进行匹配，并尽量避开忌口（%s），因此优先推荐：%s。",
                tastes, preferred, taboos, dishNames
        );
    }


    // ========================================================================
    //  工具方法
    // ========================================================================

    /**
     * 将列表格式化为中文顿号分隔的字符串。
     * 如 ["辣","酸"] → "辣、酸"
     * 空列表或null → "无"
     */
    private String formatList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "无";
        }
        return list.stream().map(this::safe).collect(Collectors.joining("、"));
    }

    /**
     * 安全地获取对象的字符串表示。
     * null → "无"，非空 → 原值
     */
    private String safe(Object value) {
        return value == null ? "无" : String.valueOf(value);
    }
}
