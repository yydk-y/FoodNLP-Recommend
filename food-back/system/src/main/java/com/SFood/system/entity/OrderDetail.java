package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单明细实体类
 */
@Data
@TableName("order_detail")
public class OrderDetail {
    
    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long detailId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 菜品ID */
    private Long dishId;
    
    /** 菜品名称（冗余） */
    private String dishName;
    
    /** 下单时价格 */
    private BigDecimal price;
    
    /** 数量 */
    private Integer num;
    
    /** 备注 */
    private String remark;
}