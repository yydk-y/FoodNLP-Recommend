package com.SFood.system.service;

import java.util.Map;

/**
 * NLP 服务接口。
 *
 * 从用户自然语言输入中提取食物偏好信息：
 * - tastes: 口味偏好，如辣、甜、清淡
 * - taboos: 忌口，如香菜、海鲜
 * - dishes: 提到的菜品名称
 *
 * 默认实现 {@link BERTNLPServiceImpl} 调用远程 BERT NER 服务，
 * 并含本地关键词回退策略。
 */
public interface NLPService {

    /**
     * 从用户输入中提取食物偏好。
     *
     * @param userInput 用户自然语言输入，如"推荐辣的，不要香菜"
     * @return Map{originalInput, tastes:List, taboos:List, dishes:List}
     */
    Map<String, Object> extractFoodPreferences(String userInput);

    /**
     * 批量提取（逐条调用单条方法）。
     *
     * @param userInputs 输入文本数组
     * @return Map[] 每个元素对应一个输入的提取结果
     */
    Map<String, Object>[] extractFoodPreferencesBatch(String[] userInputs);
}