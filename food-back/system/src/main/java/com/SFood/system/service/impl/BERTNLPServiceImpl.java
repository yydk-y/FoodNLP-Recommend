package com.SFood.system.service.impl;

import com.SFood.system.service.NLPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * BERT NLP 服务实现。
 *
 * <p>委托 BERTServiceClient 与 Python BERT NER 服务通信。
 * 输入为空时直接返回空结果，不调用网络。
 * 批量版本逐个调用单条方法。
 */
@Service
public class BERTNLPServiceImpl implements NLPService {

    /** BERT 服务 HTTP 客户端（含本地回退） */
    private final BERTServiceClient bertServiceClient;

    @Autowired
    public BERTNLPServiceImpl(BERTServiceClient bertServiceClient) {
        this.bertServiceClient = bertServiceClient;
    }

    @Override
    public Map<String, Object> extractFoodPreferences(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return createEmptyResult(userInput);
        }
        return bertServiceClient.extractEntities(userInput);
    }

    @Override
    public Map<String, Object>[] extractFoodPreferencesBatch(String[] userInputs) {
        if (userInputs == null || userInputs.length == 0) return new Map[0];
        @SuppressWarnings("unchecked")
        Map<String, Object>[] results = new Map[userInputs.length];
        for (int i = 0; i < userInputs.length; i++) results[i] = extractFoodPreferences(userInputs[i]);
        return results;
    }

    /** 构造空的提取结果（输入为空时返回） */
    private Map<String, Object> createEmptyResult(String userInput) {
        Map<String, Object> result = new HashMap<>();
        result.put("originalInput", userInput);
        result.put("tastes", new ArrayList<>());
        result.put("taboos", new ArrayList<>());
        result.put("dishes", new ArrayList<>());
        return result;
    }
}