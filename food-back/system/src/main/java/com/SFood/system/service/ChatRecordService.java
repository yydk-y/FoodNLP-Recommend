package com.SFood.system.service;

import com.SFood.system.entity.ChatRecord;

import java.util.List;
import java.util.Map;

/**
 * 聊天记录服务
 */
public interface ChatRecordService {

    /**
     * 保存聊天记录
     */
    void saveChatRecord(ChatRecord chatRecord);

    /**
     * 查询会话下所有聊天记录
     */
    List<ChatRecord> listBySessionId(String sessionId);

    /**
     * 基于会话历史记录聚合偏好
     */
    Map<String, Object> getMergedPreferencesBySessionId(String sessionId);
}
