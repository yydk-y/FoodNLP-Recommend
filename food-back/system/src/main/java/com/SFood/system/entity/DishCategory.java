package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 菜品分类实体类
 */
@Data
@TableName("dish_category")
public class DishCategory {
    
    /** 分类ID */
    @TableId(type = IdType.AUTO)
    private Long categoryId;
    
    /** 分类名称（热菜/凉菜/汤品） */
    private String categoryName;
    
    /** 排序权重 */
    private Integer sort;
}