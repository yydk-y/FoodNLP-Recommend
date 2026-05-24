package com.SFood.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.SFood.system.entity.CartInfo;
import com.SFood.system.entity.OrderMain;
import com.SFood.system.entity.OrderDetail;
import com.SFood.system.mapper.CartInfoMapper;
import com.SFood.system.mapper.OrderMainMapper;
import com.SFood.system.mapper.OrderDetailMapper;
import com.SFood.system.service.OrderService;
import com.SFood.system.dto.OrderStatistics;
import com.SFood.system.dto.PageDTO;
import com.SFood.system.dto.OrderQueryDTO;
import com.SFood.system.service.UserPreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.SFood.common.util.OrderNoGenerator;

/**
 * 订单服务实现类
 */
@Service
public class OrderServiceImpl implements OrderService {
    
    @Autowired
    private OrderMainMapper orderMainMapper;
    
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    
    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired(required = false)
    private UserPreferenceService userPreferenceService;
    
    @Override
    @Transactional
    public OrderMain checkoutFromCart(Long userId, List<Long> cartIds) {
        // 1. 验证购物车商品是否存在且属于该用户
        List<CartInfo> cartItems = validateCartItems(userId, cartIds);
        
        // 2. 计算订单总金额
        BigDecimal totalPrice = calculateTotalPrice(cartItems);
        
        // 3. 创建订单主表
        OrderMain orderMain = createOrderMain(userId, totalPrice);
        
        // 4. 创建订单明细
        List<OrderDetail> orderDetails = createOrderDetails(orderMain.getOrderId(), cartItems);
        
        // 5. 删除已结算的购物车商品
        deleteCheckedOutCartItems(cartIds);

        refreshHistoryPreferenceQuietly(userId);
        return orderMain;
    }
    
    @Override
    @Transactional
    public OrderMain createOrder(OrderMain orderMain, List<OrderDetail> orderDetails) {
        // 如果订单号为空，自动生成
        if (orderMain.getOrderNo() == null || orderMain.getOrderNo().isEmpty()) {
            orderMain.setOrderNo(OrderNoGenerator.generateOrderNo());
        }
        
        // 插入订单主表
        orderMainMapper.insert(orderMain);
        
        // 插入订单明细
        for (OrderDetail detail : orderDetails) {
            detail.setOrderId(orderMain.getOrderId());
            orderDetailMapper.insert(detail);
        }

        refreshHistoryPreferenceQuietly(orderMain.getUserId());
        return orderMain;
    }
    
    @Override
    public List<OrderMain> getUserOrders(Long userId) {
        LambdaQueryWrapper<OrderMain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderMain::getUserId, userId)
               .orderByDesc(OrderMain::getCreateTime);
        return orderMainMapper.selectList(wrapper);
    }
    
    @Override
    public OrderMain getOrderDetail(Long orderId) {
        return orderMainMapper.selectById(orderId);
    }
    
    @Override
    public List<OrderDetail> getOrderDetails(Long orderId) {
        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId, orderId);
        return orderDetailMapper.selectList(wrapper);
    }
    
    @Override
    public boolean updateOrderStatus(Long orderId, Integer status) {
        OrderMain orderMain = orderMainMapper.selectById(orderId);
        if (orderMain != null) {
            orderMain.setStatus(status);
            orderMainMapper.updateById(orderMain);
            refreshHistoryPreferenceQuietly(orderMain.getUserId());
            return true;
        }
        return false;
    }
    
    @Override
    public boolean payOrder(Long orderId) {
        OrderMain orderMain = orderMainMapper.selectById(orderId);
        if (orderMain != null && orderMain.getStatus() == 1) { // 待支付状态
            orderMain.setStatus(2); // 已支付
            orderMain.setPayTime(LocalDateTime.now());
            orderMainMapper.updateById(orderMain);
            refreshHistoryPreferenceQuietly(orderMain.getUserId());
            return true;
        }
        return false;
    }
    
    private void refreshHistoryPreferenceQuietly(Long userId) {
        if (userId == null || userPreferenceService == null) {
            return;
        }
        try {
            userPreferenceService.updatePreferenceByRecentOrders(userId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 验证购物车商品
     */
    private List<CartInfo> validateCartItems(Long userId, List<Long> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) {
            throw new RuntimeException("购物车商品不能为空");
        }
        
        List<CartInfo> cartItems = cartInfoMapper.selectBatchIds(cartIds);
        if (cartItems.size() != cartIds.size()) {
            throw new RuntimeException("部分购物车商品不存在");
        }
        
        // 验证购物车商品是否属于该用户
        for (CartInfo cartItem : cartItems) {
            if (!cartItem.getUserId().equals(userId)) {
                throw new RuntimeException("购物车商品不属于当前用户");
            }
        }
        
        return cartItems;
    }
    
    /**
     * 计算订单总金额
     */
    private BigDecimal calculateTotalPrice(List<CartInfo> cartItems) {
        return cartItems.stream()
                .map(CartInfo::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 创建订单主表
     */
    private OrderMain createOrderMain(Long userId, BigDecimal totalPrice) {
        OrderMain orderMain = new OrderMain();
        orderMain.setUserId(userId);
        orderMain.setTotalPrice(totalPrice);
        orderMain.setStatus(1); // 待支付状态
        orderMain.setOrderNo(OrderNoGenerator.generateOrderNo()); // 生成订单号
        orderMainMapper.insert(orderMain);
        return orderMain;
    }
    
    /**
     * 创建订单明细
     */
    private List<OrderDetail> createOrderDetails(Long orderId, List<CartInfo> cartItems) {
        List<OrderDetail> orderDetails = cartItems.stream().map(cartItem -> {
            OrderDetail detail = new OrderDetail();
            detail.setOrderId(orderId);
            detail.setDishId(cartItem.getDishId());
            detail.setDishName(cartItem.getDishName());
            detail.setPrice(cartItem.getDishPrice());
            detail.setNum(cartItem.getQuantity());
            detail.setRemark(cartItem.getRemark());
            return detail;
        }).collect(Collectors.toList());
        
        // 批量插入订单明细
        for (OrderDetail detail : orderDetails) {
            orderDetailMapper.insert(detail);
        }
        
        return orderDetails;
    }
    
    /**
     * 删除已结算的购物车商品
     */
    private void deleteCheckedOutCartItems(List<Long> cartIds) {
        cartInfoMapper.deleteBatchIds(cartIds);
    }
    
    @Override
    public boolean cancelOrder(Long orderId) {
        OrderMain orderMain = orderMainMapper.selectById(orderId);
        if (orderMain != null && orderMain.getStatus() == 1) { // 只能取消待支付订单
            orderMain.setStatus(4); // 取消状态
            orderMainMapper.updateById(orderMain);
            return true;
        }
        return false;
    }
    
    @Override
    @Transactional
    public boolean deleteOrder(Long orderId) {
        // 先删除订单明细
        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(OrderDetail::getOrderId, orderId);
        orderDetailMapper.delete(detailWrapper);
        
        // 再删除订单主表
        int result = orderMainMapper.deleteById(orderId);
        return result > 0;
    }
    
    @Override
    public List<OrderMain> getAllOrders() {
        LambdaQueryWrapper<OrderMain> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OrderMain::getCreateTime);
        return orderMainMapper.selectList(wrapper);
    }
    
    @Override
    public List<OrderMain> getOrdersByStatus(Integer status) {
        LambdaQueryWrapper<OrderMain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderMain::getStatus, status)
               .orderByDesc(OrderMain::getCreateTime);
        return orderMainMapper.selectList(wrapper);
    }
    
    @Override
    public List<OrderMain> getUserOrdersByStatus(Long userId, Integer status) {
        LambdaQueryWrapper<OrderMain> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderMain::getUserId, userId)
               .eq(OrderMain::getStatus, status)
               .orderByDesc(OrderMain::getCreateTime);
        return orderMainMapper.selectList(wrapper);
    }
    
    @Override
    public OrderStatistics getOrderStatistics(Long userId) {
        OrderStatistics statistics = new OrderStatistics();
        
        LambdaQueryWrapper<OrderMain> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OrderMain::getUserId, userId);
        }
        
        // 获取所有订单
        List<OrderMain> allOrders = orderMainMapper.selectList(wrapper);
        
        // 统计订单数量
        statistics.setTotalOrders(allOrders.size());
        statistics.setPendingOrders((int) allOrders.stream().filter(o -> o.getStatus() == 1).count());
        statistics.setPaidOrders((int) allOrders.stream().filter(o -> o.getStatus() == 2).count());
        statistics.setCompletedOrders((int) allOrders.stream().filter(o -> o.getStatus() == 3).count());
        statistics.setCanceledOrders((int) allOrders.stream().filter(o -> o.getStatus() == 4).count());
        
        // 统计销售额
        BigDecimal totalSales = allOrders.stream()
                .filter(o -> o.getStatus() == 2 || o.getStatus() == 3) // 已支付和已完成订单
                .map(OrderMain::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        statistics.setTotalSales(totalSales);
        
        // 计算平均订单金额
        long validOrderCount = allOrders.stream()
                .filter(o -> o.getStatus() == 2 || o.getStatus() == 3)
                .count();
        if (validOrderCount > 0) {
            statistics.setAverageOrderAmount(totalSales.divide(new BigDecimal(validOrderCount), 2, BigDecimal.ROUND_HALF_UP));
        }
        
        // 统计今日订单
        LocalDate today = LocalDate.now();
        List<OrderMain> todayOrders = allOrders.stream()
                .filter(o -> o.getCreateTime().toLocalDate().equals(today))
                .collect(Collectors.toList());
        
        statistics.setTodayOrders(todayOrders.size());
        statistics.setTodaySales(todayOrders.stream()
                .filter(o -> o.getStatus() == 2 || o.getStatus() == 3)
                .map(OrderMain::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        
        return statistics;
    }
    
    @Override
    public PageDTO<OrderMain> getOrderPage(OrderQueryDTO queryDTO) {
        // 参数验证
        if (queryDTO == null) {
            queryDTO = new OrderQueryDTO();
        }
        if (queryDTO.getPageNum() == null || queryDTO.getPageNum() < 1) {
            queryDTO.setPageNum(1);
        }
        if (queryDTO.getPageSize() == null || queryDTO.getPageSize() < 1) {
            queryDTO.setPageSize(10);
        }
        
        // 创建分页对象
        Page<OrderMain> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        
        // 构建查询条件
        LambdaQueryWrapper<OrderMain> queryWrapper = new LambdaQueryWrapper<>();
        
        // 用户ID查询
        if (queryDTO.getUserId() != null) {
            queryWrapper.eq(OrderMain::getUserId, queryDTO.getUserId());
        }
        
        // 状态查询
        if (queryDTO.getStatus() != null) {
            queryWrapper.eq(OrderMain::getStatus, queryDTO.getStatus());
        }
        
        // 金额范围查询
        if (queryDTO.getMinAmount() != null) {
            queryWrapper.ge(OrderMain::getTotalPrice, BigDecimal.valueOf(queryDTO.getMinAmount()));
        }
        if (queryDTO.getMaxAmount() != null) {
            queryWrapper.le(OrderMain::getTotalPrice, BigDecimal.valueOf(queryDTO.getMaxAmount()));
        }
        
        // 创建时间范围查询
        if (StringUtils.hasText(queryDTO.getStartTime())) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(queryDTO.getStartTime() + " 00:00:00", 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.ge(OrderMain::getCreateTime, startTime);
            } catch (Exception e) {
                throw new RuntimeException("开始时间格式错误，请使用yyyy-MM-dd格式");
            }
        }
        
        if (StringUtils.hasText(queryDTO.getEndTime())) {
            try {
                LocalDateTime endTime = LocalDateTime.parse(queryDTO.getEndTime() + " 23:59:59", 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.le(OrderMain::getCreateTime, endTime);
            } catch (Exception e) {
                throw new RuntimeException("结束时间格式错误，请使用yyyy-MM-dd格式");
            }
        }
        
        // 支付时间范围查询
        if (StringUtils.hasText(queryDTO.getPayStartTime())) {
            try {
                LocalDateTime payStartTime = LocalDateTime.parse(queryDTO.getPayStartTime() + " 00:00:00", 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.ge(OrderMain::getPayTime, payStartTime);
            } catch (Exception e) {
                throw new RuntimeException("支付开始时间格式错误，请使用yyyy-MM-dd格式");
            }
        }
        
        if (StringUtils.hasText(queryDTO.getPayEndTime())) {
            try {
                LocalDateTime payEndTime = LocalDateTime.parse(queryDTO.getPayEndTime() + " 23:59:59", 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.le(OrderMain::getPayTime, payEndTime);
            } catch (Exception e) {
                throw new RuntimeException("支付结束时间格式错误，请使用yyyy-MM-dd格式");
            }
        }
        
        // 按创建时间倒序排列
        queryWrapper.orderByDesc(OrderMain::getCreateTime);
        
        // 执行分页查询
        Page<OrderMain> resultPage = orderMainMapper.selectPage(page, queryWrapper);
        
        // 转换为自定义分页DTO
        PageDTO<OrderMain> pageDTO = new PageDTO<>(
            (int) resultPage.getCurrent(),
            (int) resultPage.getSize(),
            resultPage.getTotal(),
            resultPage.getRecords()
        );
        
        return pageDTO;
    }

    @Override
    public List<OrderDetail> getUserRecentOrderDetails(Long userId) {
        // 计算7天前的时间
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        
        // 查询用户近7天的订单
        LambdaQueryWrapper<OrderMain> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(OrderMain::getUserId, userId)
                .ge(OrderMain::getCreateTime, sevenDaysAgo)
                .in(OrderMain::getStatus, 2, 3); // 只查询已支付和已完成的订单
        List<OrderMain> recentOrders = orderMainMapper.selectList(orderWrapper);
        
        // 如果没有近7天的订单，返回空列表
        if (recentOrders.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 提取订单ID列表
        List<Long> orderIds = recentOrders.stream()
                .map(OrderMain::getOrderId)
                .collect(Collectors.toList());
        
        // 查询这些订单的明细
        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(OrderDetail::getOrderId, orderIds);
        return orderDetailMapper.selectList(detailWrapper);
    }
}