package com.SFood.system.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单统计信息DTO
 */
@Data
public class OrderStatistics {
    
    /** 总订单数 */
    private Integer totalOrders;
    
    /** 待支付订单数 */
    private Integer pendingOrders;
    
    /** 已支付订单数 */
    private Integer paidOrders;
    
    /** 已完成订单数 */
    private Integer completedOrders;
    
    /** 已取消订单数 */
    private Integer canceledOrders;
    
    /** 总销售额 */
    private BigDecimal totalSales;
    
    /** 平均订单金额 */
    private BigDecimal averageOrderAmount;
    
    /** 今日订单数 */
    private Integer todayOrders;
    
    /** 今日销售额 */
    private BigDecimal todaySales;
    
    public OrderStatistics() {
        this.totalOrders = 0;
        this.pendingOrders = 0;
        this.paidOrders = 0;
        this.completedOrders = 0;
        this.canceledOrders = 0;
        this.totalSales = BigDecimal.ZERO;
        this.averageOrderAmount = BigDecimal.ZERO;
        this.todayOrders = 0;
        this.todaySales = BigDecimal.ZERO;
    }
}