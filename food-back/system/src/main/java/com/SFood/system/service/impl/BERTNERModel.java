package com.SFood.system.service.impl;


import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * BERT NER模型类
 * 负责加载和运行训练好的BERT NER模型
 */
public class BERTNERModel {
    
    private static final String MODEL_PATH = "../bert_ner_model_next";
    private static final int MAX_LENGTH = 512;
    
    private ZooModel<NDList, NDList> model;
    private Predictor<NDList, NDList> predictor;
    private BertFullTokenizer tokenizer;
    private DefaultVocabulary vocabulary;
    
    // 标签映射
    private static final Map<Integer, String> ID2LABEL = Map.of(
            0, "O",
            1, "B-TASTE",
            2, "I-TASTE",
            3, "B-DISH",
            4, "I-DISH",
            5, "B-TABOO",
            6, "I-TABOO"
    );
    
    public BERTNERModel() {
        try {
            initializeModel();
        } catch (Exception e) {
            System.err.println("BERT NER模型初始化失败: " + e.getMessage());
        }
    }
    
    private void initializeModel() throws IOException, TranslateException {
        // 获取模型路径
        Path modelDir = Paths.get(MODEL_PATH);
        
        // 构建模型加载标准
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(modelDir)
                .optTranslator(new NoopTranslator())
                .optEngine("PyTorch")
                .build();
        
        // 加载模型
        try {
            this.model = criteria.loadModel();
        } catch (ModelNotFoundException e) {
            throw new RuntimeException(e);
        } catch (MalformedModelException e) {
            throw new RuntimeException(e);
        }
        this.predictor = model.newPredictor();
        
        // 初始化tokenizer（这里需要根据你的模型配置调整）
        initializeTokenizer();
    }
    
    private void initializeTokenizer() {
        // 这里需要根据你的tokenizer配置来初始化
        // 由于我们使用的是自定义模型，这里简化处理
        // 实际使用时需要加载tokenizer.json和vocab文件
        try {
            // 简化版tokenizer - 按字符分割
            this.tokenizer = null; // 暂时设为null，使用字符级处理
        } catch (Exception e) {
            System.err.println("Tokenizer初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 从文本中提取实体
     * @param text 输入文本
     * @return 实体映射，包含TASTE、DISH、TABOO三类实体
     */
    public Map<String, List<String>> extractEntities(String text) {
        if (model == null || predictor == null) {
            // 如果模型未加载成功，使用基于规则的回退方法
            return fallbackExtraction(text);
        }
        
        try (NDManager manager = NDManager.newBaseManager()) {
            // 预处理文本
            List<String> tokens = preprocessText(text);
            
            // 转换为模型输入
            NDArray inputIds = createInputIds(manager, tokens);
            NDArray attentionMask = createAttentionMask(manager, tokens.size());
            
            // 模型推理
            NDList input = new NDList(inputIds, attentionMask);
            NDList output = predictor.predict(input);
            
            // 解析输出
            return parseModelOutput(output, tokens, text);
            
        } catch (Exception e) {
            System.err.println("实体提取失败: " + e.getMessage());
            return fallbackExtraction(text);
        }
    }
    
    private List<String> preprocessText(String text) {
        // 字符级分词
        List<String> tokens = new ArrayList<>();
        for (char c : text.toCharArray()) {
            tokens.add(String.valueOf(c));
        }
        return tokens;
    }
    
    private NDArray createInputIds(NDManager manager, List<String> tokens) {
        // 简化处理：将字符转换为ID
        // 实际使用时需要根据vocab文件进行映射
        long[] inputIds = new long[Math.min(tokens.size(), MAX_LENGTH)];
        for (int i = 0; i < inputIds.length; i++) {
            // 简化映射：使用字符的ASCII码
            inputIds[i] = tokens.get(i).charAt(0);
        }
        
        // 填充到最大长度
        long[] paddedIds = new long[MAX_LENGTH];
        System.arraycopy(inputIds, 0, paddedIds, 0, inputIds.length);
        
        return manager.create(paddedIds).reshape(1, -1);
    }
    
    private NDArray createAttentionMask(NDManager manager, int tokenLength) {
        long[] mask = new long[MAX_LENGTH];
        for (int i = 0; i < Math.min(tokenLength, MAX_LENGTH); i++) {
            mask[i] = 1;
        }
        return manager.create(mask).reshape(1, -1);
    }
    
    private Map<String, List<String>> parseModelOutput(NDList output, List<String> tokens, String originalText) {
        Map<String, List<String>> entities = new HashMap<>();
        entities.put("TASTE", new ArrayList<>());
        entities.put("DISH", new ArrayList<>());
        entities.put("TABOO", new ArrayList<>());
        
        // 简化处理：由于模型输出格式未知，这里使用基于规则的回退
        // 实际使用时需要根据模型的实际输出格式进行解析
        return fallbackExtraction(originalText);
    }
    
    /**
     * 基于规则的回退实体提取方法
     */
    private Map<String, List<String>> fallbackExtraction(String text) {
        Map<String, List<String>> entities = new HashMap<>();
        entities.put("TASTE", new ArrayList<>());
        entities.put("DISH", new ArrayList<>());
        entities.put("TABOO", new ArrayList<>());
        
        // 简单的关键词匹配规则
        String lowerText = text.toLowerCase();
        
        // 口味偏好提取
        if (lowerText.contains("辣") || lowerText.contains("麻辣")) {
            entities.get("TASTE").add("辣");
        }
        if (lowerText.contains("甜") || lowerText.contains("甜的")) {
            entities.get("TASTE").add("甜");
        }
        if (lowerText.contains("酸") || lowerText.contains("酸的")) {
            entities.get("TASTE").add("酸");
        }
        if (lowerText.contains("咸") || lowerText.contains("咸的")) {
            entities.get("TASTE").add("咸");
        }
        if (lowerText.contains("清淡") || lowerText.contains("清淡的")) {
            entities.get("TASTE").add("清淡");
        }
        
        // 忌口提取
        if (lowerText.contains("香菜") || lowerText.contains("芫荽")) {
            entities.get("TABOO").add("香菜");
        }
        if (lowerText.contains("海鲜")) {
            entities.get("TABOO").add("海鲜");
        }
        if (lowerText.contains("辣") && (lowerText.contains("不吃") || lowerText.contains("不要"))) {
            entities.get("TABOO").add("辣");
        }
        if (lowerText.contains("猪肉")) {
            entities.get("TABOO").add("猪肉");
        }
        
        // 菜品提取（简化处理）
        if (lowerText.contains("宫保鸡丁")) {
            entities.get("DISH").add("宫保鸡丁");
        }
        if (lowerText.contains("鱼香肉丝")) {
            entities.get("DISH").add("鱼香肉丝");
        }
        if (lowerText.contains("麻婆豆腐")) {
            entities.get("DISH").add("麻婆豆腐");
        }
        
        return entities;
    }
    
    public void close() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }
}