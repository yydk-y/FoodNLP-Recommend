package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.DishInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜品信息Mapper接口
 */
@Mapper
public interface DishInfoMapper extends BaseMapper<DishInfo> {
}