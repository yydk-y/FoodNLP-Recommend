package com.SFood.system.service;

import com.SFood.system.entity.OrderMain;
import com.SFood.system.entity.OrderDetail;
import com.SFood.system.dto.OrderStatistics;
import com.SFood.system.dto.PageDTO;
import com.SFood.system.dto.OrderQueryDTO;
import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {
    
    /**
     * 从购物车批量结算生成订单
     * @param userId 用户ID
     * @param cartIds 购物车ID列表
     * @return 生成的订单主表信息
     */
    OrderMain checkoutFromCart(Long userId, List<Long> cartIds);
    
    /**
     * 创建订单
     * @param orderMain 订单主表信息
     * @param orderDetails 订单明细列表
     * @return 订单主表信息
     */
    OrderMain createOrder(OrderMain orderMain, List<OrderDetail> orderDetails);
    
    /**
     * 获取用户订单列表
     * @param userId 用户ID
     * @return 订单列表
     */
    List<OrderMain> getUserOrders(Long userId);
    
    /**
     * 获取订单详情
     * @param orderId 订单ID
     * @return 订单详情（包含主表和明细）
     */
    OrderMain getOrderDetail(Long orderId);
    
    /**
     * 获取订单明细列表
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    List<OrderDetail> getOrderDetails(Long orderId);
    
    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 新状态
     * @return 是否成功
     */
    boolean updateOrderStatus(Long orderId, Integer status);
    
    /**
     * 支付订单
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean payOrder(Long orderId);
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean cancelOrder(Long orderId);
    
    /**
     * 删除订单
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean deleteOrder(Long orderId);
    
    /**
     * 获取所有订单列表（管理员用）
     * @return 所有订单列表
     */
    List<OrderMain> getAllOrders();
    
    /**
     * 根据状态获取订单列表
     * @param status 订单状态
     * @return 订单列表
     */
    List<OrderMain> getOrdersByStatus(Integer status);
    
    /**
     * 根据用户和状态获取订单列表
     * @param userId 用户ID
     * @param status 订单状态
     * @return 订单列表
     */
    List<OrderMain> getUserOrdersByStatus(Long userId, Integer status);
    
    /**
     * 获取订单统计信息
     * @param userId 用户ID（可选，为null时统计所有用户）
     * @return 统计信息
     */
    OrderStatistics getOrderStatistics(Long userId);
    
    /**
     * 分页查询订单列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageDTO<OrderMain> getOrderPage(OrderQueryDTO queryDTO);
    
    /**
     * 获取用户近7天的订单明细
     * @param userId 用户ID
     * @return 近7天的订单明细列表
     */
    List<OrderDetail> getUserRecentOrderDetails(Long userId);
}