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
 * 基于大语言模型的推荐解释服务实现类
 * <p>
 * 该服务利用大语言模型（如GPT）为推荐结果生成自然语言解释，
 * 解释推荐的依据，包括口味匹配、忌口规避和偏好菜品关联等因素。
 * <p>
 * 当LLM服务不可用时，会回退到基于规则的简单解释。
 */
@Service
@Primary
@Slf4j
public class LLMRecommendationExplanationServiceImpl implements RecommendationExplanationService {

    /** REST客户端，用于调用LLM API */
    private final RestTemplate restTemplate;
    
    /** JSON解析器，用于处理LLM API响应 */
    private final ObjectMapper objectMapper;
    
    /** 是否启用LLM解释功能，默认值为false */
    @Value("${llm.enabled:false}")
    private boolean llmEnabled;

    /** LLM API基础URL，默认值为OpenAI API地址 */
    @Value("${llm.base-url:https://api.openai.com}")
    private String llmBaseUrl;

    /** LLM API密钥 */
    @Value("${llm.api-key:}")
    private String llmApiKey;

    /** 使用的LLM模型，默认值为gpt-4o-mini */
    @Value("${llm.model:gpt-4o-mini}")
    private String llmModel;
    
    /**
     * 构造函数
     * <p>
     * 初始化RestTemplate和ObjectMapper实例，用于LLM API调用和响应解析。
     * RestTemplate用于发送HTTP请求到LLM服务，ObjectMapper用于解析JSON响应。
     */
    public LLMRecommendationExplanationServiceImpl() {
        // 初始化RestTemplate，用于发送HTTP请求到LLM API
        this.restTemplate = new RestTemplate();
        // 初始化ObjectMapper，用于解析LLM API返回的JSON响应
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成推荐解释
     * <p>
     * 首先检查是否有推荐菜品，然后检查LLM配置是否启用，
     * 最后调用LLM或回退到规则解释。
     * 该方法是服务的核心方法，处理整个推荐解释的生成流程。
     * 
     * @param userInput 用户原始输入文本
     * @param preferences 用户偏好信息，包含口味、忌口、菜品偏好等
     * @param recommendedDishes 推荐的菜品列表
     * @return 推荐解释文本，可能来自LLM或基于规则的回退解释
     */
    @Override
    public String generateExplanation(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        // 检查是否有推荐菜品，无推荐菜品时返回默认提示
        if (recommendedDishes == null || recommendedDishes.isEmpty()) {
            log.info("LLM解释跳过：无可推荐菜品");
            return "当前没有可推荐菜品，可能是筛掉忌口后无匹配结果。";
        }

        // 检查LLM配置是否启用，未启用时使用回退解释
        if (!llmEnabled || llmApiKey == null || llmApiKey.isBlank()) {
            log.info("LLM解释回退：llm.enabled={} 或 api-key 未配置", llmEnabled);
            return buildFallbackExplanation(preferences, recommendedDishes);
        }

        try {
            // 构建LLM提示词，包含所有必要信息
            String prompt = buildPrompt(userInput, preferences, recommendedDishes);
            // 调用LLM API获取解释
            String llmResponse = callChatCompletion(prompt);
            
            // 检查LLM返回结果，为空时使用回退解释
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM解释回退：模型返回空内容");
                return buildFallbackExplanation(preferences, recommendedDishes);
            }
            
            // LLM解释成功，返回结果
            log.info("LLM解释成功：model={}, baseUrl={}", llmModel, llmBaseUrl);
            return llmResponse.trim();
        } catch (Exception e) {
            // 调用异常时使用回退解释
            log.warn("LLM解释回退：调用异常 -> {}", e.getMessage());
            return buildFallbackExplanation(preferences, recommendedDishes);
        }
    }

    /**
     * 调用LLM的chat completion接口
     * <p>
     * 构建请求体，设置系统提示和用户提示，调用API并解析响应。
     * 实现了与OpenAI兼容的chat completion API调用流程，支持自定义模型和参数。
     * 
     * @param prompt 用户提示词，包含用户输入、偏好信息和推荐结果
     * @return LLM生成的解释文本，如果调用失败则返回null
     * @throws Exception 调用过程中可能发生的异常，如网络错误或API调用失败
     */
    private String callChatCompletion(String prompt) throws Exception {
        // 构建API端点URL，确保URL格式正确
        String endpoint = llmBaseUrl.endsWith("/") ? llmBaseUrl + "v1/chat/completions" : llmBaseUrl + "/v1/chat/completions";

        // 构建请求体，设置API调用参数
        Map<String, Object> requestBody = new HashMap<>();
        // 指定使用的LLM模型
        requestBody.put("model", llmModel);
        // 设置温度参数，控制生成文本的随机性（0.4表示较低随机性，结果更确定）
        requestBody.put("temperature", 0.4);

        // 构建消息列表，包含系统消息和用户消息
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 添加系统消息，设置助手角色和行为准则
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是餐饮推荐解释助手。请用简洁中文解释推荐依据，长度控制在120字以内。");
        messages.add(systemMessage);

        // 添加用户消息，包含提示词
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        // 将消息列表添加到请求体
        requestBody.put("messages", messages);

        // 设置HTTP头，指定内容类型和认证信息
        HttpHeaders headers = new HttpHeaders();
        // 设置内容类型为JSON
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 设置Bearer认证，使用API密钥
        headers.setBearerAuth(llmApiKey);

        // 构建HTTP请求实体
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        // 发送POST请求到LLM API
        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, requestEntity, String.class);
        
        // 检查响应状态，确保请求成功且有响应体
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        // 解析JSON响应
        JsonNode root = objectMapper.readTree(response.getBody());
        // 获取choices字段，包含生成的文本
        JsonNode choices = root.path("choices");
        // 检查choices是否为非空数组
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }

        // 提取第一个选择的消息内容
        return choices.get(0).path("message").path("content").asText(null);
    }

    /**
     * 构建LLM提示词
     * <p>
     * 格式化用户输入、偏好信息和推荐结果，构建清晰的提示词。
     * 提示词包含用户原始输入、BERT提取的关键词和推荐结果，
     * 引导LLM生成符合要求的推荐解释。
     * 
     * @param userInput 用户原始输入文本
     * @param preferences 用户偏好信息，包含口味、忌口、菜品偏好等
     * @param recommendedDishes 推荐的菜品列表
     * @return 格式化的提示词，包含所有必要信息
     */
    private String buildPrompt(String userInput, Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        // 格式化偏好信息，将列表转换为字符串
        String tastes = formatList(preferences.get("tastes")); // 口味偏好
        String taboos = formatList(preferences.get("taboos")); // 忌口信息
        String dishes = formatList(preferences.get("dishes")); // 偏好菜品

        // 构建推荐结果摘要，包含菜品名称和口味
        String recommendationBrief = recommendedDishes.stream()
                .map(d -> {
                    String score = d.getMatchScore() != null ? "匹配度:" + d.getMatchScore().intValue() : "";
                    return String.format("%s(口味:%s%s)", safe(d.getDishName()), safe(d.getTaste()), score.isEmpty() ? "" : "," + score);
                })
                .collect(Collectors.joining("；"));

        // 构建完整提示词，包含所有必要信息和任务指令
        return "用户原始输入：" + safe(userInput) + "\n"
                + "BERT关键词-口味：" + tastes + "\n"
                + "BERT关键词-忌口：" + taboos + "\n"
                + "BERT关键词-偏好菜品：" + dishes + "\n"
                + "推荐结果：" + recommendationBrief + "\n"
                + "请解释为什么推荐这些菜，突出\"匹配口味、规避忌口、与偏好菜品的关联\"。";
    }

    /**
     * 构建回退解释（当LLM不可用时）
     * <p>
     * 使用简单的规则生成解释，避免依赖LLM。
     * 当LLM服务不可用或调用失败时，使用此方法生成基于规则的解释。
     * 
     * @param preferences 用户偏好信息，包含口味、忌口、菜品偏好等
     * @param recommendedDishes 推荐的菜品列表
     * @return 基于规则的解释文本，格式固定但信息完整
     */
    private String buildFallbackExplanation(Map<String, Object> preferences, List<DishInfo> recommendedDishes) {
        // 格式化偏好信息，将列表转换为字符串
        String tastes = formatList(preferences.get("tastes")); // 口味偏好
        String taboos = formatList(preferences.get("taboos")); // 忌口信息
        String preferred = formatList(preferences.get("dishes")); // 偏好菜品
        
        // 提取推荐菜品名称，过滤空值并使用顿号分隔
        String dishNames = recommendedDishes.stream()
                .map(DishInfo::getDishName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));

        // 构建简单解释，使用固定格式
        return String.format(
                "已根据你偏好的口味（%s）和关注菜品（%s）进行匹配，并尽量避开忌口（%s），因此优先推荐：%s。",
                tastes, preferred, taboos, dishNames
        );
    }

    /**
     * 格式化列表为字符串
     * <p>
     * 将列表转换为以"、"分隔的字符串，空列表或非列表对象返回"无"。
     * 用于将偏好信息中的列表转换为可读的字符串格式。
     * 
     * @param value 可能为列表的对象
     * @return 格式化后的字符串，空列表返回"无"
     */
    private String formatList(Object value) {
        // 检查对象是否为列表且非空
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "无";
        }
        // 将列表元素转换为字符串并使用顿号分隔
        return list.stream().map(this::safe).collect(Collectors.joining("、"));
    }

    /**
     * 安全处理对象，避免空指针
     * <p>
     * 处理可能为null的对象，返回"无"或对象的字符串表示。
     * 用于确保所有对象都能安全地转换为字符串，避免空指针异常。
     * 
     * @param value 可能为null的对象
     * @return 安全的字符串表示，null返回"无"
     */
    private String safe(Object value) {
        // 如果对象为null，返回"无"，否则返回对象的字符串表示
        return value == null ? "无" : String.valueOf(value);
    }
}