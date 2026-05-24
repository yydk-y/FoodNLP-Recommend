package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品信息实体类
 */
@Data
@TableName("dish_info")
public class DishInfo {
    
    /** 菜品ID */
    @TableId(type = IdType.AUTO)
    private Long dishId;
    
    /** 菜品名称 */
    private String dishName;
    
    /** 分类ID */
    private Long categoryId;
    
    /** 菜品价格 */
    private BigDecimal price;
    
    /** 口味，JSON数组 ["辣","香"] */
    private String taste;
    
    /** 主要食材，JSON数组 ["鸡肉","花生"] */
    private String ingredient;
    
    /** 菜品图片URL */
    private String imageUrl;
    
    /** 热度（销量/点击量） */
    private Integer heat;
    
    /** 菜品描述 */
    private String description;
    
    /** 状态 1-上架 0-下架 */
    private Integer status;

    /** 推荐匹配度（非数据库字段） */
    @TableField(exist = false)
    private Double matchScore;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}