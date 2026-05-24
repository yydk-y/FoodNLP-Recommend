package com.SFood.system.service;

import java.util.Map;

/**
 * NLP 服务接口 — 从用户自然语言输入中提取食物偏好信息。
 *
 * <p>将用户的日常口语（如"推荐辣的，不要香菜"）转化为结构化数据：
 * <ul>
 *   <li>tastes: 口味偏好，如辣、甜、清淡</li>
 *   <li>taboos: 忌口，如香菜、海鲜、辣</li>
 *   <li>dishes: 提到的具体菜品名称，如宫保鸡丁</li>
 * </ul>
 *
 * <p>默认实现 {@link BERTNLPServiceImpl} 委托 BERT NER 服务 + 本地关键词回退。
 */
public interface NLPService {

    /**
     * 从用户输入中提取食物偏好。
     *
     * @param userInput 用户自然语言输入，如"推荐辣的，不要香菜"
     * @return Map，含四个 key：
     *         - originalInput: 原始输入文本
     *         - tastes: 口味列表
     *         - taboos: 忌口列表
     *         - dishes: 菜品名称列表
     */
    Map<String, Object> extractFoodPreferences(String userInput);

    /**
     * 批量提取食物偏好。
     * 内部逐条调用单条方法。
     *
     * @param userInputs 输入文本数组
     * @return Map[] 每个元素对应一个输入的提取结果（含originalInput/tastes/taboos/dishes）
     */
    Map<String, Object>[] extractFoodPreferencesBatch(String[] userInputs);
}
