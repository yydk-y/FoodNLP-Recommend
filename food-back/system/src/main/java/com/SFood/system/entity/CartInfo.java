package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车实体类
 * 同一个dishId的商品合并，不同商品有不同的购物车项
 */
@Data
@TableName("cart_info")
public class CartInfo {
    
    /** 购物车ID */
    @TableId(type = IdType.AUTO)
    private Long cartId;
    
    /** 用户ID */
    private Long userId;
    
    /** 菜品ID - 同一个dishId视为同一个商品 */
    private Long dishId;
    
    /** 菜品名称（冗余字段，便于查询） */
    private String dishName;
    
    /** 菜品价格（冗余字段，便于计算） */
    private BigDecimal dishPrice;
    
    /** 菜品图片 */
    private String dishImage;
    
    /** 菜品数量 */
    private Integer quantity;
    
    /** 小计金额（数量 * 单价） */
    private BigDecimal subtotal;
    
    /** 备注（少辣/不放葱） */
    private String remark;
    
    /** 加入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    /**
     * 计算小计金额
     */
    public void calculateSubtotal() {
        if (dishPrice != null && quantity != null && quantity > 0) {
            this.subtotal = dishPrice.multiply(new BigDecimal(quantity));
        } else {
            this.subtotal = BigDecimal.ZERO;
        }
    }
}