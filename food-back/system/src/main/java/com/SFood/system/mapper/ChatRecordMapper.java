package com.SFood.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.SFood.system.entity.ChatRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话记录Mapper接口
 */
@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
}