package com.SFood.system.dto;

import lombok.Data;

/**
 * 订单查询条件DTO
 */
@Data
public class OrderQueryDTO {
    
    /** 用户ID */
    private Long userId;
    
    /** 订单状态（1-待支付，2-已支付，3-完成，4-取消） */
    private Integer status;
    
    /** 开始时间（创建时间） */
    private String startTime;
    
    /** 结束时间（创建时间） */
    private String endTime;
    
    /** 支付开始时间 */
    private String payStartTime;
    
    /** 支付结束时间 */
    private String payEndTime;
    
    /** 最低金额 */
    private Double minAmount;
    
    /** 最高金额 */
    private Double maxAmount;
    
    /** 当前页码 */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 10;
}