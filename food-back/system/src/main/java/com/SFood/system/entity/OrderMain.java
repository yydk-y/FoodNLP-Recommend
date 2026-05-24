package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体类
 */
@Data
@TableName("order_main")
public class OrderMain {
    
    /** 订单ID */
    @TableId(type = IdType.AUTO)
    private Long orderId;
    
    /** 用户ID */
    private Long userId;
    
    /** 订单总价 */
    private BigDecimal totalPrice;
    
    /** 状态 1-待支付 2-已支付 3-完成 4-取消 */
    private Integer status;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /** 支付时间（模拟） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
    
    /** 订单号 */
    @TableField("order_no")
    private String orderNo;
}