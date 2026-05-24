package com.SFood.system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * BERT 服务 HTTP 客户端。
 *
 * <p>负责与独立的 Python BERT NER 服务（Flask, port 5000）通信，
 * 从用户自然语言输入中提取口味(TASTE)、忌口(TABOO)、菜品(DISH)三大类实体。
 *
 * <p>当 Python BERT 服务不可用时（未启动/网络错误），
 * 会自动降级到基于关键词匹配的本地回退提取，保证系统不依赖外部服务也能运行。
 *
 * <p>即使 BERT 服务可用，也会在 BERT 结果基础上补充本地同义词匹配的口味，
 * 避免 BERT 模型训练不足漏掉某些口味关键词，形成双层保障。
 */
@Component
public class BERTServiceClient {

    /** Python BERT Flask 服务的地址（默认本机5000端口） */
    private static final String BERT_SERVICE_URL = "http://localhost:5000";

    /**
     * 本地口味关键词映射表。
     * key=标准口味名，value=所有口语化同义词。
     * 用于：BERT不可用时的回退提取；BERT可用时补充提取结果。
     * 与 RecommendationServiceImpl 中的 TERM_SYNONYMS 有重叠，但保持独立。
     */
    private static final Map<String, List<String>> TASTE_KEYWORDS = createTasteKeywordMap();

    /** HTTP 客户端，用于发送 REST 请求到 BERT 服务 */
    private final RestTemplate restTemplate;
    /** JSON 解析器，用于解析 BERT 服务返回的 JSON 响应 */
    private final ObjectMapper objectMapper;

    public BERTServiceClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 健康检查：探测 Python BERT 服务是否存活。
     * 调用 /health 端点，返回 200 则认为可用。
     * 每次 extractEntities 都会先调这个检查，避免调用已宕机的服务。
     */
    public boolean isServiceAvailable() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    BERT_SERVICE_URL + "/health", String.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;  // 任何异常（连接拒绝/超时）都视为不可用
        }
    }

    /**
     * ★★★ 核心方法：从用户输入文本中提取食物相关实体 ★★★
     *
     * 流程：
     * 1. 先检查 BERT 服务是否可用
     * 2. 可用 → 调 BERT 的 /extract 接口，取 NER 结果
     * 3. 不可用或调用失败 → 本地关键词匹配回退
     * 4. 无论哪种方式，都在 BERT 结果基础上补充本地关键词口味（双层保障）
     *
     * @param text 用户输入文本，如"我想吃辣的，不要香菜"
     * @return Map，包含四个key：
     *         - originalInput: 原始输入文本
     *         - tastes: 口味列表，如["辣"]
     *         - taboos: 忌口列表，如["香菜"]
     *         - dishes: 菜品名称列表，如[]
     */
    public Map<String, Object> extractEntities(String text) {
        // BERT 服务不可用 → 直接走本地回退
        if (!isServiceAvailable()) {
            return createFallbackResult(text);
        }

        try {
            // 构建 POST 请求体：{"text": "用户输入"}
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // 发送 POST 请求到 BERT 服务的 /extract 端点
            ResponseEntity<String> response = restTemplate.postForEntity(
                    BERT_SERVICE_URL + "/extract", request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                // 解析 BERT 服务返回的 JSON → Map
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                return formatResult(result);
            } else {
                // BERT 返回了非200状态码 → 回退
                return createFallbackResult(text);
            }

        } catch (Exception e) {
            // 网络错误/JSON解析错误 → 回退
            System.err.println("BERT服务调用失败: " + e.getMessage());
            return createFallbackResult(text);
        }
    }

    /**
     * 将 BERT 服务返回的原始响应格式化为标准 Map。
     *
     * BERT 返回的 entities 含 TASTE/TABOO/DISH 三个字段，
     * 但 BERT 模型可能漏掉某些口味（如"想吃酸的"没认出"酸"）。
     * 所以用本地关键词回退做补充（mergeFallbackTastes），双层保障。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> formatResult(Map<String, Object> bertResult) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("originalInput", bertResult.get("originalInput"));

        // 从 BERT 响应中提取 entities 节点
        Map<String, Object> entities = (Map<String, Object>) bertResult.get("entities");
        if (entities != null) {
            // 口味：BERT 结果 + 本地关键词回退（双层保障）
            formatted.put("tastes", mergeFallbackTastes(
                    entities.getOrDefault("TASTE", new ArrayList<>()),
                    formatted.get("originalInput")));
            formatted.put("taboos", entities.getOrDefault("TABOO", new ArrayList<>()));
            formatted.put("dishes", entities.getOrDefault("DISH", new ArrayList<>()));
        } else {
            // entities 节点不存在 → 纯本地回退
            formatted.put("tastes", extractFallbackTastes(formatted.get("originalInput")));
            formatted.put("taboos", new ArrayList<>());
            formatted.put("dishes", new ArrayList<>());
        }
        return formatted;
    }

    /**
     * BERT 服务不可用或调用失败时的完全兜底结果。
     * 只做口味提取（因为口味关键词最明确），忌口和菜品靠后续正则或 NER 降级。
     */
    private Map<String, Object> createFallbackResult(String text) {
        Map<String, Object> result = new HashMap<>();
        result.put("originalInput", text);
        result.put("tastes", extractFallbackTastes(text));   // 本地关键词提取口味
        result.put("taboos", new ArrayList<>());             // 忌口空，后续由推荐层处理
        result.put("dishes", new ArrayList<>());             // 菜品空，后续由正则或推荐层处理
        return result;
    }

    /**
     * 口味关键词映射表。
     * 覆盖六大基础味型，每个味型包含常见口语化表示和相关词汇。
     */
    private static Map<String, List<String>> createTasteKeywordMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("酸", Arrays.asList("酸", "酸味", "酸口", "偏酸", "酸爽", "酸辣", "酸甜", "醋", "糖醋", "泡菜", "酸菜", "番茄", "柠檬"));
        map.put("甜", Arrays.asList("甜", "甜味", "甜口", "偏甜", "香甜", "酸甜", "糖醋", "蜜汁", "焦糖", "红糖", "拔丝", "蜂蜜"));
        map.put("苦", Arrays.asList("苦", "苦味", "苦口", "偏苦", "微苦", "清苦", "苦瓜", "苦菊"));
        map.put("辣", Arrays.asList("辣", "辣味", "辣口", "偏辣", "麻辣", "香辣", "酸辣", "微辣", "中辣", "重辣", "剁椒", "泡椒", "辣椒"));
        map.put("咸", Arrays.asList("咸", "咸味", "咸口", "偏咸", "鲜咸", "咸鲜", "酱香", "酱油", "腊味", "腌", "盐焗", "咸蛋黄"));
        return map;
    }

    /**
     * 将 BERT 结果和本地回退结果合并。
     * LinkedHashSet 保证：去重 + BERT结果优先 + 本地补充。
     */
    private List<String> mergeFallbackTastes(Object extractedTastes, Object originalInput) {
        LinkedHashSet<String> tastes = new LinkedHashSet<>();
        // 先加 BERT 提取的口味
        if (extractedTastes instanceof Collection<?> collection) {
            for (Object taste : collection) {
                if (taste != null && !taste.toString().trim().isEmpty()) {
                    tastes.add(taste.toString().trim());
                }
            }
        }
        // 再加本地关键词匹配的口味（如果 BERT 漏了，这里补上）
        tastes.addAll(extractFallbackTastes(originalInput));
        return new ArrayList<>(tastes);
    }

    /**
     * 纯本地关键词匹配的口味提取。
     * 遍历 TASTE_KEYWORDS 映射表，如果用户输入文本包含某个口味的同义词，就提取该口味。
     *
     * 例：输入"想吃酸的辣的" → 检测到"辣"和"酸" → 返回["辣","酸"]
     */
    private List<String> extractFallbackTastes(Object textObj) {
        String text = textObj == null ? "" : textObj.toString();
        if (text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> tastes = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : TASTE_KEYWORDS.entrySet()) {
            // 只要输入文本包含该口味的任何一个同义词，就提取该口味
            if (entry.getValue().stream().anyMatch(text::contains)) {
                tastes.add(entry.getKey());
            }
        }
        return new ArrayList<>(tastes);
    }
}
