package com.SFood.common.constant;

import java.util.*;

/**
 * 口味同义词常量 — 两处引用同一来源。
 * BERTServiceClient（NLP fallback）和 RecommendationServiceImpl（相似度计算）共享。
 */
public final class TasteConstants {

    private TasteConstants() {}

    /**
     * 6 类口味及其同义词/相关词。
     * 比 BERTServiceClient 原 fallback 多「海鲜」「清淡」两类。
     */
    private static final Map<String, List<String>> SYNONYM_MAP = createSynonymMap();

    /**
     * NLP fallback 用的 5 类口味关键词（排除海鲜/清淡，它们不是基础口味）。
     */
    private static final Map<String, List<String>> TASTE_KEYWORD_MAP = createTasteKeywordMap();

    public static Map<String, List<String>> getSynonymMap() {
        return SYNONYM_MAP;
    }

    public static Map<String, List<String>> getTasteKeywordMap() {
        return TASTE_KEYWORD_MAP;
    }

    /** 所有口味类别名（共 6 个） */
    public static Set<String> getAllTasteCategories() {
        return SYNONYM_MAP.keySet();
    }

    /** 5 类基础口味（作为 NLP fallback 匹配的目标） */
    public static Set<String> getBasicTasteCategories() {
        return TASTE_KEYWORD_MAP.keySet();
    }

    /**
     * 判断文本是否包含某类口味的同义词。
     */
    public static boolean matchesTasteCategory(String text, String category) {
        List<String> aliases = SYNONYM_MAP.get(category);
        if (aliases == null) return false;
        return aliases.stream().anyMatch(text::contains) || text.contains(category);
    }

    private static Map<String, List<String>> createSynonymMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("酸", Arrays.asList("酸味", "酸口", "偏酸", "酸一点", "酸爽", "酸辣", "酸甜", "醋", "陈醋", "香醋", "米醋", "糖醋", "泡菜", "酸菜", "番茄", "柠檬", "山楂", "话梅", "梅子"));
        map.put("甜", Arrays.asList("甜味", "甜口", "偏甜", "甜一点", "甜香", "香甜", "酸甜", "糖醋", "蜜汁", "焦糖", "红糖", "拔丝", "糖", "蜂蜜", "冰糖", "桂花"));
        map.put("海鲜", Arrays.asList("海产", "虾", "虾仁", "蟹", "鱼", "贝", "扇贝", "蛤蜊", "鱿鱼", "章鱼", "海带"));
        map.put("清淡", Arrays.asList("少油", "不油", "低脂", "清爽"));
        map.put("苦", Arrays.asList("苦味", "苦口", "偏苦", "苦一点", "微苦", "清苦", "苦瓜", "苦菊", "莲子心", "陈皮"));
        map.put("辣", Arrays.asList("辣味", "辣口", "偏辣", "辣一点", "麻辣", "香辣", "酸辣", "微辣", "中辣", "重辣", "特辣", "变态辣", "椒麻", "剁椒", "泡椒", "辣椒", "小米椒", "青椒", "红椒", "花椒", "胡椒"));
        map.put("咸", Arrays.asList("咸味", "咸口", "偏咸", "咸一点", "鲜咸", "咸鲜", "酱香", "酱爆", "酱烧", "酱油", "豆瓣酱", "黄豆酱", "腊味", "腌", "盐焗", "咸蛋黄", "咸菜"));
        return map;
    }

    private static Map<String, List<String>> createTasteKeywordMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("酸", Arrays.asList("酸", "酸味", "酸口", "偏酸", "酸爽", "酸辣", "酸甜", "醋", "糖醋", "泡菜", "酸菜", "番茄", "柠檬"));
        map.put("甜", Arrays.asList("甜", "甜味", "甜口", "偏甜", "香甜", "酸甜", "糖醋", "蜜汁", "焦糖", "红糖", "拔丝", "蜂蜜"));
        map.put("苦", Arrays.asList("苦", "苦味", "苦口", "偏苦", "微苦", "清苦", "苦瓜", "苦菊"));
        map.put("辣", Arrays.asList("辣", "辣味", "辣口", "偏辣", "麻辣", "香辣", "酸辣", "微辣", "中辣", "重辣", "剁椒", "泡椒", "辣椒"));
        map.put("咸", Arrays.asList("咸", "咸味", "咸口", "偏咸", "鲜咸", "咸鲜", "酱香", "酱油", "腊味", "腌", "盐焗", "咸蛋黄"));
        return map;
    }
}
