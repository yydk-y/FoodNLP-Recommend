package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户信息Mapper接口
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}