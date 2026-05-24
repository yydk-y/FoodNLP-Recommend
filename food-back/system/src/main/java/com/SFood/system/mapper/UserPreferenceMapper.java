package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户偏好Mapper接口
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
    
    /**
     * 根据用户ID和菜品ID查询用户偏好
     */
    @Select("SELECT * FROM user_preference WHERE user_id = #{userId} AND dish_id = #{dishId}")
    UserPreference selectByUserIdAndDishId(@Param("userId") Long userId, @Param("dishId") Long dishId);
    
    /**
     * 根据用户ID查询用户偏好
     */
    @Select("SELECT * FROM user_preference WHERE user_id = #{userId}")
    List<UserPreference> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID和菜品类别查询用户偏好
     */
    @Select("SELECT up.* FROM user_preference up " +
            "JOIN dish_info di ON up.dish_id = di.dish_id " +
            "WHERE up.user_id = #{userId} AND di.category_id = #{categoryId}")
    List<UserPreference> selectByUserIdAndCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
    
    /**
     * 查询所有用户ID
     */
    @Select("SELECT DISTINCT user_id FROM user_preference")
    List<Long> selectAllUserIds();
}