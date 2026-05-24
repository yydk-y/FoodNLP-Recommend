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
 * 从用户自然语言输入中提取口味(TASTE)、忌口(TABOO)、菜品(DISH)实体。
 * BERT 服务不可用时，降级到基于关键词规则的本地回退提取。
 */
@Component
public class BERTServiceClient {

    /** Python BERT 服务的地址 */
    private static final String BERT_SERVICE_URL = "http://localhost:5000";
    /** 口味关键词映射表 — 本地回退和 BERT 结果合并使用 */
    private static final Map<String, List<String>> TASTE_KEYWORDS = createTasteKeywordMap();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BERTServiceClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /** 检查 BERT Flask 服务 /health 是否可访问 */
    public boolean isServiceAvailable() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                BERT_SERVICE_URL + "/health", String.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从用户输入文本中提取食物相关实体。
     *
     * <p>优先调用 BERT 服务，失败时降级到本地关键词匹配。
     * 无论哪种方式，都会在 BERT 结果基础上补充本地关键词匹配的口味，
     * 确保尽可能多的提取。
     *
     * @param text 用户输入文本
     * @return Map{originalInput, tastes, taboos, dishes}
     */
    public Map<String, Object> extractEntities(String text) {
        if (!isServiceAvailable()) {
            return createFallbackResult(text);
        }
        
        try {
            // 构建请求
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("text", text);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(
                BERT_SERVICE_URL + "/extract", request, String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                // 解析响应
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                return formatResult(result);
            } else {
                return createFallbackResult(text);
            }
            
        } catch (Exception e) {
            System.err.println("BERT服务调用失败: " + e.getMessage());
            return createFallbackResult(text);
        }
    }
    
    /**
     * 将 BERT 服务返回的原始响应格式化为标准 Map。
     * BERT 结果中的 TA/TABOO/DISH 字段与本地回退提取合并，
     * 避免 BERT 模型训练不足漏掉某些口味。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> formatResult(Map<String, Object> bertResult) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("originalInput", bertResult.get("originalInput"));

        Map<String, Object> entities = (Map<String, Object>) bertResult.get("entities");
        if (entities != null) {
            // 口味：BERT 结果 + 本地关键词回退（双层保障）
            formatted.put("tastes", mergeFallbackTastes(entities.getOrDefault("TASTE", new ArrayList<>()), formatted.get("originalInput")));
            formatted.put("taboos", entities.getOrDefault("TABOO", new ArrayList<>()));
            formatted.put("dishes", entities.getOrDefault("DISH", new ArrayList<>()));
        } else {
            formatted.put("tastes", extractFallbackTastes(formatted.get("originalInput")));
            formatted.put("taboos", new ArrayList<>());
            formatted.put("dishes", new ArrayList<>());
        }
        return formatted;
    }

    /** BERT 服务不可用或调用失败时的兜底结果 */
    private Map<String, Object> createFallbackResult(String text) {
        Map<String, Object> result = new HashMap<>();
        result.put("originalInput", text);
        result.put("tastes", extractFallbackTastes(text));
        result.put("taboos", new ArrayList<>());
        result.put("dishes", new ArrayList<>());
        return result;
    }

    private static Map<String, List<String>> createTasteKeywordMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("酸", Arrays.asList("酸", "酸味", "酸口", "偏酸", "酸爽", "酸辣", "酸甜", "醋", "糖醋", "泡菜", "酸菜", "番茄", "柠檬"));
        map.put("甜", Arrays.asList("甜", "甜味", "甜口", "偏甜", "香甜", "酸甜", "糖醋", "蜜汁", "焦糖", "红糖", "拔丝", "蜂蜜"));
        map.put("苦", Arrays.asList("苦", "苦味", "苦口", "偏苦", "微苦", "清苦", "苦瓜", "苦菊"));
        map.put("辣", Arrays.asList("辣", "辣味", "辣口", "偏辣", "麻辣", "香辣", "酸辣", "微辣", "中辣", "重辣", "剁椒", "泡椒", "辣椒"));
        map.put("咸", Arrays.asList("咸", "咸味", "咸口", "偏咸", "鲜咸", "咸鲜", "酱香", "酱油", "腊味", "腌", "盐焗", "咸蛋黄"));
        return map;
    }

    private List<String> mergeFallbackTastes(Object extractedTastes, Object originalInput) {
        LinkedHashSet<String> tastes = new LinkedHashSet<>();
        if (extractedTastes instanceof Collection<?> collection) {
            for (Object taste : collection) {
                if (taste != null && !taste.toString().trim().isEmpty()) {
                    tastes.add(taste.toString().trim());
                }
            }
        }
        tastes.addAll(extractFallbackTastes(originalInput));
        return new ArrayList<>(tastes);
    }

    private List<String> extractFallbackTastes(Object textObj) {
        String text = textObj == null ? "" : textObj.toString();
        if (text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> tastes = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : TASTE_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(text::contains)) {
                tastes.add(entry.getKey());
            }
        }
        return new ArrayList<>(tastes);
    }
}

