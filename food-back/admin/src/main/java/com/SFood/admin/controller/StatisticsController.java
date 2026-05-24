package com.SFood.admin.controller;

import com.SFood.common.dto.ResultDTO;
import com.SFood.system.dto.HotDishDTO;
import com.SFood.system.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/user-total")
    public ResultDTO<Long> getUserTotalCount() {
        return ResultDTO.success(statisticsService.getUserTotalCount());
    }

    @GetMapping("/hot-dishes")
    public ResultDTO<List<HotDishDTO>> getHotDishesTop5() {
        return ResultDTO.success(statisticsService.getHotDishesTop5());
    }

    @GetMapping("/hot-dishes/week")
    public ResultDTO<List<HotDishDTO>> getWeeklyHotDishesTop5() {
        return ResultDTO.success(statisticsService.getWeeklyHotDishesTop5());
    }

    @GetMapping("/hot-dishes/month")
    public ResultDTO<List<HotDishDTO>> getMonthlyHotDishesTop5() {
        return ResultDTO.success(statisticsService.getMonthlyHotDishesTop5());
    }

    @GetMapping("/order-total")
    public ResultDTO<Long> getTotalOrderCount() {
        return ResultDTO.success(statisticsService.getTotalOrderCount());
    }

    @GetMapping("/today-orders")
    public ResultDTO<Long> getTodayOrderCount() {
        return ResultDTO.success(statisticsService.getTodayOrderCount());
    }

    @GetMapping("/today-sales")
    public ResultDTO<Double> getTodaySalesAmount() {
        return ResultDTO.success(statisticsService.getTodaySalesAmount());
    }

    @GetMapping("/active-users")
    public ResultDTO<Integer> getActiveUserCount() {
        return ResultDTO.success(statisticsService.getActiveUserCount());
    }

    @GetMapping("/overview")
    public ResultDTO<Map<String, Object>> getStatisticsOverview() {
        Map<String, Object> overview = new HashMap<>();

        overview.put("userTotalCount", statisticsService.getUserTotalCount());
        overview.put("activeUserCount", statisticsService.getActiveUserCount());
        overview.put("totalOrderCount", statisticsService.getTotalOrderCount());
        overview.put("todayOrderCount", statisticsService.getTodayOrderCount());
        overview.put("todaySalesAmount", statisticsService.getTodaySalesAmount());

        overview.put("hotDishesTop5", statisticsService.getHotDishesTop5());
        overview.put("weeklyHotDishesTop5", statisticsService.getWeeklyHotDishesTop5());
        overview.put("monthlyHotDishesTop5", statisticsService.getMonthlyHotDishesTop5());

        overview.put("salesOrderTrend7d", statisticsService.getSalesOrderTrend(7));
        overview.put("averageOrderAmountTrend7d", statisticsService.getAverageOrderAmountTrend(7));
        overview.put("repurchaseRate7d", statisticsService.getRepurchaseRate(7));
        overview.put("repurchaseRate30d", statisticsService.getRepurchaseRate(30));

        return ResultDTO.success(overview);
    }
}
