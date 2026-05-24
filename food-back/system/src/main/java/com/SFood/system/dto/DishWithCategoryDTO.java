package com.SFood.system.dto;

import com.SFood.system.entity.DishCategory;
import com.SFood.system.entity.DishInfo;
import lombok.Data;

/**
 * 包含分类信息的菜品数据传输对象
 */
@Data
public class DishWithCategoryDTO {
    
    /** 菜品信息 */
    private DishInfo dishInfo;
    
    /** 菜品分类信息 */
    private DishCategory dishCategory;
    
    public DishWithCategoryDTO(DishInfo dishInfo, DishCategory dishCategory) {
        this.dishInfo = dishInfo;
        this.dishCategory = dishCategory;
    }
}