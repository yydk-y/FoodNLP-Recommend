package com.SFood.admin.controller;

import com.SFood.common.dto.ResultDTO;
import com.SFood.system.dto.OrderQueryDTO;
import com.SFood.system.dto.OrderStatistics;
import com.SFood.system.dto.PageDTO;
import com.SFood.system.entity.OrderDetail;
import com.SFood.system.entity.OrderMain;
import com.SFood.system.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long NUTRITION_TREND_CACHE_MINUTES = 30;

    @Value("${llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${llm.base-url:https://api.deepseek.com}")
    private String llmBaseUrl;

    @Value("${llm.api-key:}")
    private String llmApiKey;

    @Value("${llm.model:deepseek-chat}")
    private String llmModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public ResultDTO<List<OrderMain>> getUserOrders(@RequestParam Long userId) {
        return ResultDTO.success(orderService.getUserOrders(userId));
    }

    @GetMapping("/detail/{orderId}")
    public ResultDTO<OrderMain> getOrderDetail(@PathVariable Long orderId) {
        return ResultDTO.success(orderService.getOrderDetail(orderId));
    }

    @GetMapping("/details/{orderId}")
    public ResultDTO<List<OrderDetail>> getOrderDetails(@PathVariable Long orderId) {
        return ResultDTO.success(orderService.getOrderDetails(orderId));
    }

    @PutMapping("/status")
    public ResultDTO<Boolean> updateOrderStatus(@RequestParam Long orderId, @RequestParam Integer status) {
        return ResultDTO.success(orderService.updateOrderStatus(orderId, status));
    }

    @PostMapping("/pay")
    public ResultDTO<Boolean> payOrder(@RequestParam Long orderId) {
        return ResultDTO.success(orderService.payOrder(orderId));
    }


    @PostMapping("/pay/session")
    public ResultDTO createPaySession(@RequestParam Long orderId,
                                                            @RequestParam(defaultValue = "ALIPAY") String channel) {
        OrderMain order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return ResultDTO.error("订单不存在");
        }
        if (!Objects.equals(order.getStatus(), 1)) {
            return ResultDTO.error("当前订单状态不允许支付");
        }

        String sessionId = "PAY" + System.currentTimeMillis() + new Random().nextInt(1000);
        String key = "order:pay:session:" + orderId + ":" + sessionId;
        Map<String, Object> session = new HashMap<>();
        session.put("sessionId", sessionId);
        session.put("orderId", orderId);
        session.put("orderNo", order.getOrderNo());
        session.put("amount", order.getTotalPrice());
        session.put("channel", channel == null ? "ALIPAY" : channel.toUpperCase());
        session.put("status", "PENDING");
        session.put("expireAt", LocalDateTime.now().plusMinutes(5).toString());
        redisTemplate.opsForValue().set(key, session, 5, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("orderId", orderId);
        response.put("orderNo", order.getOrderNo());
        response.put("amount", order.getTotalPrice());
        response.put("channel", session.get("channel"));
        response.put("expireSeconds", 300);
        response.put("payCode", "MOCK-" + order.getOrderNo());
        return ResultDTO.success(response);
    }

    @PostMapping("/pay/confirm")
    public ResultDTO confirmPay(@RequestParam Long orderId,
                                                      @RequestParam String sessionId) {
        String key = "order:pay:session:" + orderId + ":" + sessionId;
        Object raw = redisTemplate.opsForValue().get(key);
        if (!(raw instanceof Map<?, ?> map)) {
            return ResultDTO.error("支付会话不存在或已过期");
        }

        OrderMain order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return ResultDTO.error("订单不存在");
        }
        if (Objects.equals(order.getStatus(), 2)) {
            Map<String, Object> done = new HashMap<>();
            done.put("paid", true);
            done.put("orderId", orderId);
            done.put("orderNo", order.getOrderNo());
            done.put("payTime", order.getPayTime() == null ? LocalDateTime.now().toString() : order.getPayTime().toString());
            return ResultDTO.success(done);
        }
        if (!Objects.equals(order.getStatus(), 1)) {
            return ResultDTO.error("当前订单状态不允许支付");
        }

        boolean paid = orderService.payOrder(orderId);
        if (!paid) {
            return ResultDTO.error("支付失败，请稍后重试");
        }

        Map<String, Object> session = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                session.put(entry.getKey().toString(), entry.getValue());
            }
        }
        session.put("status", "SUCCESS");
        redisTemplate.opsForValue().set(key, session, 2, TimeUnit.MINUTES);

        OrderMain refreshed = orderService.getOrderDetail(orderId);
        Map<String, Object> response = new HashMap<>();
        response.put("paid", true);
        response.put("orderId", orderId);
        response.put("orderNo", refreshed == null ? null : refreshed.getOrderNo());
        response.put("payTime", refreshed != null && refreshed.getPayTime() != null ? refreshed.getPayTime().toString() : LocalDateTime.now().toString());
        response.put("paymentNo", "PMT" + System.currentTimeMillis());
        response.put("channel", session.getOrDefault("channel", "ALIPAY"));
        return ResultDTO.success(response);
    }

    @PostMapping("/create")
    public ResultDTO<OrderMain> createOrder(@RequestBody OrderMain orderMain, @RequestBody List<OrderDetail> orderDetails) {
        return ResultDTO.success(orderService.createOrder(orderMain, orderDetails));
    }

    @PostMapping("/cancel")
    public ResultDTO<Boolean> cancelOrder(@RequestParam Long orderId) {
        return ResultDTO.success(orderService.cancelOrder(orderId));
    }

    @DeleteMapping("/delete")
    public ResultDTO<Boolean> deleteOrder(@RequestParam Long orderId) {
        return ResultDTO.success(orderService.deleteOrder(orderId));
    }

    @GetMapping("/all")
    public ResultDTO<List<OrderMain>> getAllOrders() {
        return ResultDTO.success(orderService.getAllOrders());
    }

    @GetMapping("/by-status")
    public ResultDTO<List<OrderMain>> getOrdersByStatus(@RequestParam Integer status) {
        return ResultDTO.success(orderService.getOrdersByStatus(status));
    }

    @GetMapping("/user-by-status")
    public ResultDTO<List<OrderMain>> getUserOrdersByStatus(@RequestParam Long userId, @RequestParam Integer status) {
        return ResultDTO.success(orderService.getUserOrdersByStatus(userId, status));
    }

    @GetMapping("/statistics")
    public ResultDTO<OrderStatistics> getOrderStatistics(@RequestParam(required = false) Long userId) {
        return ResultDTO.success(orderService.getOrderStatistics(userId));
    }

    @GetMapping("/page")
    public ResultDTO<PageDTO<OrderMain>> getOrderPage(@ModelAttribute OrderQueryDTO queryDTO) {
        return ResultDTO.success(orderService.getOrderPage(queryDTO));
    }

    /**
     * 使用 DeepSeek 分析近7天营养趋势
     */
    @GetMapping("/nutrition-trend-ai")
    public ResultDTO getNutritionTrendAI(@RequestParam Long userId) {
        try {
            List<OrderMain> orders = orderService.getUserOrders(userId);
            // 仅统计已支付/已完成的订单，排除未支付和已取消
            orders = orders.stream()
                    .filter(o -> o.getStatus() != null && (o.getStatus() == 2 || o.getStatus() == 3))
                    .collect(java.util.stream.Collectors.toList());
            LocalDate start = LocalDate.now().minusDays(6);

            String latestOrderTime = orders.stream()
                    .filter(order -> order.getCreateTime() != null)
                    .map(order -> order.getCreateTime().toString())
                    .max(String::compareTo)
                    .orElse("none");
            String cacheVersion = orders.size() + ":" + latestOrderTime;
            String cacheKey = "nutrition:trend:ai:" + userId + ":" + cacheVersion;

            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResult = (Map<String, Object>) map;
                return ResultDTO.success(cachedResult);
            }

            List<Map<String, Object>> orderPayload = new ArrayList<>();
            for (OrderMain order : orders) {
                if (order.getCreateTime() == null || order.getCreateTime().toLocalDate().isBefore(start)) {
                    continue;
                }
                List<OrderDetail> details = orderService.getOrderDetails(order.getOrderId());
                List<Map<String, Object>> items = new ArrayList<>();
                for (OrderDetail detail : details) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("dishName", detail.getDishName());
                    item.put("quantity", detail.getNum());
                    item.put("remark", detail.getRemark());
                    items.add(item);
                }
                Map<String, Object> orderItem = new HashMap<>();
                orderItem.put("date", order.getCreateTime().toLocalDate().toString());
                orderItem.put("items", items);
                orderPayload.add(orderItem);
            }

            if (!llmEnabled || llmApiKey == null || llmApiKey.isBlank()) {
                return ResultDTO.error("DeepSeek未启用或未配置API Key");
            }

            LocalDate end = LocalDate.now();
            String prompt = "请根据用户近7天点餐记录估算每日营养摄入，并严格返回JSON。\n"
                    + "输出结构：{\"trend\":[{\"date\":\"MM-dd\",\"calories\":0,\"protein\":0,\"fat\":0,\"carbs\":0}],"
                    + "\"summary\":{\"avgCalories\":0,\"avgProtein\":0,\"avgFat\":0,\"avgCarbs\":0,\"advice\":\"\"}}\n"
                    + "要求：trend数组包含从 " + start + " 到 " + end + " 共7天，每天一条，日期格式MM-dd；无数据天填0；只返回JSON，不要任何解释文本。\n"
                    + "订单数据：" + objectMapper.writeValueAsString(orderPayload);

            String content = callDeepSeek(prompt);
            if (content == null || content.isBlank()) {
                return ResultDTO.error("DeepSeek返回为空");
            }

            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> result = objectMapper.convertValue(root, Map.class);
            redisTemplate.opsForValue().set(cacheKey, result, NUTRITION_TREND_CACHE_MINUTES, TimeUnit.MINUTES);
            return ResultDTO.success(result);
        } catch (Exception e) {
            return ResultDTO.error("营养分析失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/nutrition-trend-ai/cache")
    public ResultDTO<Boolean> clearNutritionTrendAICache(@RequestParam Long userId) {
        String keyPattern = "nutrition:trend:ai:" + userId + ":*";
        Set<String> keys = redisTemplate.keys(keyPattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        return ResultDTO.success(true);
    }

    private String callDeepSeek(String prompt) throws Exception {
        String endpoint = llmBaseUrl.endsWith("/") ? llmBaseUrl + "v1/chat/completions" : llmBaseUrl + "/v1/chat/completions";

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);
        body.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是营养分析助手，只返回合法JSON。"));
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).path("message").path("content").asText(null);
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
