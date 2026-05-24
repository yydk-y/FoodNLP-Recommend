package com.SFood.system.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 菜品信息数据传输对象
 */
@Data
public class DishInfoDTO {
    
    /** 菜品ID（更新时使用） */
    private Long dishId;
    
    /** 菜品名称 */
    @NotBlank(message = "菜品名称不能为空")
    private String dishName;
    
    /** 分类ID */
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
    
    /** 菜品价格 */
    @NotNull(message = "菜品价格不能为空")
    private BigDecimal price;
    
    /** 口味，字符串，多个口味用逗号分隔 */
    private String taste;
    
    /** 主要食材，字符串，多个食材用逗号分隔 */
    private String ingredient;
    
    /** 菜品图片URL */
    private String imageUrl;
    
    /** 菜品描述 */
    private String description;
    
    /** 状态 1-上架 0-下架 */
    private Integer status = 1;
}