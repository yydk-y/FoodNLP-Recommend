package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.DishCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜品分类Mapper接口
 */
@Mapper
public interface DishCategoryMapper extends BaseMapper<DishCategory> {
}