package com.SFood.system.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 热销菜品DTO
 */
@Data
public class HotDishDTO {
    
    /** 菜品ID */
    private Long dishId;
    
    /** 菜品名称 */
    private String dishName;
    
    /** 菜品图片 */
    private String dishImage;
    
    /** 菜品价格 */
    private BigDecimal dishPrice;
    
    /** 销售数量 */
    private Integer salesCount;
    
    /** 销售总额 */
    private BigDecimal totalSales;
    
    /** 排名 */
    private Integer rank;
    
    public HotDishDTO() {
    }
    
    public HotDishDTO(Long dishId, String dishName, String dishImage, BigDecimal dishPrice, 
                     Integer salesCount, BigDecimal totalSales, Integer rank) {
        this.dishId = dishId;
        this.dishName = dishName;
        this.dishImage = dishImage;
        this.dishPrice = dishPrice;
        this.salesCount = salesCount;
        this.totalSales = totalSales;
        this.rank = rank;
    }
}