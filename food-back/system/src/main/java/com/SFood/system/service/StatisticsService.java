package com.SFood.system.service;

import com.SFood.system.dto.HotDishDTO;

import java.util.List;
import java.util.Map;

/**
 * 统计服务接口
 */
public interface StatisticsService {

    Long getUserTotalCount();

    List<HotDishDTO> getHotDishesTop5();

    List<HotDishDTO> getWeeklyHotDishesTop5();

    List<HotDishDTO> getMonthlyHotDishesTop5();

    Long getTotalOrderCount();

    Long getTodayOrderCount();

    Double getTodaySalesAmount();

    Integer getActiveUserCount();

    /**
     * 销售额+订单数趋势（近N天，按天）
     */
    List<Map<String, Object>> getSalesOrderTrend(int days);

    /**
     * 客单价趋势（近N天，按天）
     */
    List<Map<String, Object>> getAverageOrderAmountTrend(int days);

    /**
     * 复购率（近N天，百分比）
     */
    Double getRepurchaseRate(int days);
}
