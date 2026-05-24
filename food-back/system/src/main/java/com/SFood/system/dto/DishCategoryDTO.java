package com.SFood.system.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 菜品分类数据传输对象
 */
@Data
public class DishCategoryDTO {
    
    /** 分类ID（更新时使用） */
    private Long categoryId;
    
    /** 分类名称（热菜/凉菜/汤品） */
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;
    
    /** 排序权重 */
    private Integer sort = 0;
}