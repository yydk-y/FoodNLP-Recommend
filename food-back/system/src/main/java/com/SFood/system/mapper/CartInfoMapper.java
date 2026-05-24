package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.CartInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车Mapper接口
 */
@Mapper
public interface CartInfoMapper extends BaseMapper<CartInfo> {
}