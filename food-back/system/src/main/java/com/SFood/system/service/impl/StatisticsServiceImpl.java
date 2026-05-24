package com.SFood.system.service.impl;

import com.SFood.system.dto.HotDishDTO;
import com.SFood.system.entity.DishInfo;
import com.SFood.system.entity.OrderDetail;
import com.SFood.system.entity.OrderMain;
import com.SFood.system.entity.UserInfo;
import com.SFood.system.mapper.DishInfoMapper;
import com.SFood.system.mapper.OrderDetailMapper;
import com.SFood.system.mapper.OrderMainMapper;
import com.SFood.system.mapper.UserInfoMapper;
import com.SFood.system.service.StatisticsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务实现类
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private OrderMainMapper orderMainMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private DishInfoMapper dishInfoMapper;

    @Override
    public Long getUserTotalCount() {
        return userInfoMapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public List<HotDishDTO> getHotDishesTop5() {
        return getHotDishesTop5ByRange(null, null);
    }

    @Override
    public List<HotDishDTO> getWeeklyHotDishesTop5() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.MAX);
        return getHotDishesTop5ByRange(startOfWeek, endOfWeek);
    }

    @Override
    public List<HotDishDTO> getMonthlyHotDishesTop5() {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
        return getHotDishesTop5ByRange(startOfMonth, endOfMonth);
    }

    private List<HotDishDTO> getHotDishesTop5ByRange(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OrderMain> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.in(OrderMain::getStatus, 2, 3);
        if (startTime != null && endTime != null) {
            orderWrapper.between(OrderMain::getCreateTime, startTime, endTime);
        }

        List<OrderMain> validOrders = orderMainMapper.selectList(orderWrapper);
        if (validOrders.isEmpty()) return List.of();

        List<Long> orderIds = validOrders.stream().map(OrderMain::getOrderId).collect(Collectors.toList());

        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(OrderDetail::getOrderId, orderIds);
        List<OrderDetail> orderDetails = orderDetailMapper.selectList(detailWrapper);

        Map<Long, List<OrderDetail>> dishSales = orderDetails.stream().collect(Collectors.groupingBy(OrderDetail::getDishId));
        List<Long> dishIds = new ArrayList<>(dishSales.keySet());
        List<DishInfo> dishes = dishInfoMapper.selectBatchIds(dishIds);
        Map<Long, DishInfo> dishMap = dishes.stream().collect(Collectors.toMap(DishInfo::getDishId, d -> d));

        List<HotDishDTO> hotDishes = dishSales.entrySet().stream().map(entry -> {
            Long dishId = entry.getKey();
            List<OrderDetail> details = entry.getValue();
            DishInfo dish = dishMap.get(dishId);
            if (dish == null) return null;

            Integer salesCount = details.stream().mapToInt(OrderDetail::getNum).sum();
            BigDecimal totalSales = details.stream()
                    .map(detail -> detail.getPrice().multiply(BigDecimal.valueOf(detail.getNum())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new HotDishDTO(dishId, dish.getDishName(), dish.getImageUrl(), dish.getPrice(), salesCount, totalSales, 0);
        }).filter(Objects::nonNull)
          .sorted(Comparator.comparing(HotDishDTO::getSalesCount).reversed())
          .limit(5)
          .collect(Collectors.toList());

        for (int i = 0; i < hotDishes.size(); i++) {
            hotDishes.get(i).setRank(i + 1);
        }
        return hotDishes;
    }

    @Override
    public Long getTotalOrderCount() {
        return orderMainMapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public Long getTodayOrderCount() {
        LocalDate today = LocalDate.now();
        return orderMainMapper.selectCount(new LambdaQueryWrapper<OrderMain>()
                .between(OrderMain::getCreateTime, today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
    }

    @Override
    public Double getTodaySalesAmount() {
        LocalDate today = LocalDate.now();
        List<OrderMain> todayOrders = orderMainMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                .between(OrderMain::getCreateTime, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .in(OrderMain::getStatus, 2, 3));

        return todayOrders.stream().map(OrderMain::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
    }

    @Override
    public Integer getActiveUserCount() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<OrderMain> recentOrders = orderMainMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                .ge(OrderMain::getCreateTime, thirtyDaysAgo)
                .in(OrderMain::getStatus, 2, 3));

        return (int) recentOrders.stream().map(OrderMain::getUserId).distinct().count();
    }

    @Override
    public List<Map<String, Object>> getSalesOrderTrend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<OrderMain> orders = getPaidOrdersInRange(start.atStartOfDay(), end.atTime(LocalTime.MAX));

        Map<LocalDate, List<OrderMain>> grouped = orders.stream().collect(Collectors.groupingBy(o -> o.getCreateTime().toLocalDate()));
        List<Map<String, Object>> result = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<OrderMain> dayOrders = grouped.getOrDefault(date, List.of());
            BigDecimal sales = dayOrders.stream().map(OrderMain::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> row = new HashMap<>();
            row.put("date", date.toString());
            row.put("salesAmount", sales.doubleValue());
            row.put("orderCount", dayOrders.size());
            result.add(row);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getAverageOrderAmountTrend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<OrderMain> orders = getPaidOrdersInRange(start.atStartOfDay(), end.atTime(LocalTime.MAX));

        Map<LocalDate, List<OrderMain>> grouped = orders.stream().collect(Collectors.groupingBy(o -> o.getCreateTime().toLocalDate()));
        List<Map<String, Object>> result = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<OrderMain> dayOrders = grouped.getOrDefault(date, List.of());
            BigDecimal sales = dayOrders.stream().map(OrderMain::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = dayOrders.isEmpty() ? BigDecimal.ZERO : sales.divide(BigDecimal.valueOf(dayOrders.size()), 2, RoundingMode.HALF_UP);

            Map<String, Object> row = new HashMap<>();
            row.put("date", date.toString());
            row.put("averageOrderAmount", avg.doubleValue());
            result.add(row);
        }

        return result;
    }

    @Override
    public Double getRepurchaseRate(int days) {
        LocalDateTime startTime = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        LocalDateTime endTime = LocalDate.now().atTime(LocalTime.MAX);
        List<OrderMain> orders = getPaidOrdersInRange(startTime, endTime);

        if (orders.isEmpty()) return 0.0;

        Map<Long, Long> orderCountByUser = orders.stream()
                .collect(Collectors.groupingBy(OrderMain::getUserId, Collectors.counting()));

        long totalUsers = orderCountByUser.size();
        long repurchaseUsers = orderCountByUser.values().stream().filter(count -> count >= 2).count();

        if (totalUsers == 0) return 0.0;
        return BigDecimal.valueOf(repurchaseUsers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalUsers), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<OrderMain> getPaidOrdersInRange(LocalDateTime start, LocalDateTime end) {
        return orderMainMapper.selectList(new LambdaQueryWrapper<OrderMain>()
                .between(OrderMain::getCreateTime, start, end)
                .in(OrderMain::getStatus, 2, 3));
    }
}
