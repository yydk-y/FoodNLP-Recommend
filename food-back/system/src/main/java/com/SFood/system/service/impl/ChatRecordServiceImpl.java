package com.SFood.system.service.impl;

import com.SFood.system.entity.ChatRecord;
import com.SFood.system.mapper.ChatRecordMapper;
import com.SFood.system.service.ChatRecordService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 聊天记录服务实现
 */
@Service
public class ChatRecordServiceImpl implements ChatRecordService {

    @Autowired
    private ChatRecordMapper chatRecordMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void saveChatRecord(ChatRecord chatRecord) {
        if (chatRecord == null) {
            return;
        }
        if (chatRecord.getChatTime() == null) {
            chatRecord.setChatTime(LocalDateTime.now());
        }
        chatRecordMapper.insert(chatRecord);
    }

    @Override
    public List<ChatRecord> listBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        QueryWrapper<ChatRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId);
        queryWrapper.orderByAsc("chat_time");
        return chatRecordMapper.selectList(queryWrapper);
    }

    @Override
    public Map<String, Object> getMergedPreferencesBySessionId(String sessionId) {
        Map<String, Object> merged = createEmptyPreferences();
        List<ChatRecord> records = listBySessionId(sessionId);
        for (ChatRecord record : records) {
            Map<String, Object> prefFromRecord = parsePreferences(record.getBertExtract());
            merged = mergePreferences(merged, prefFromRecord);
        }
        return merged;
    }

    private Map<String, Object> parsePreferences(String json) {
        if (json == null || json.isBlank()) {
            return createEmptyPreferences();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return createEmptyPreferences();
        }
    }

    private Map<String, Object> createEmptyPreferences() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("tastes", new ArrayList<String>());
        preferences.put("taboos", new ArrayList<String>());
        preferences.put("dishes", new ArrayList<String>());
        return preferences;
    }

    private Map<String, Object> mergePreferences(Map<String, Object> base, Map<String, Object> addition) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("tastes", mergeList(base.get("tastes"), addition.get("tastes")));
        merged.put("taboos", mergeList(base.get("taboos"), addition.get("taboos")));
        merged.put("dishes", mergeList(base.get("dishes"), addition.get("dishes")));
        return merged;
    }

    private List<String> mergeList(Object first, Object second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        append(set, first);
        append(set, second);
        return new ArrayList<>(set);
    }

    private void append(Set<String> set, Object listObject) {
        if (!(listObject instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item != null) {
                String value = item.toString().trim();
                if (!value.isEmpty()) {
                    set.add(value);
                }
            }
        }
    }
}
