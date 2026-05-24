package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.DishRating;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜品评分Mapper接口
 */
@Mapper
public interface DishRatingMapper extends BaseMapper<DishRating> {
    
    /**
     * 根据菜品ID查询评分
     */
    @Select("SELECT * FROM dish_rating WHERE dish_id = #{dishId}")
    List<DishRating> selectByDishId(@Param("dishId") Long dishId);
}