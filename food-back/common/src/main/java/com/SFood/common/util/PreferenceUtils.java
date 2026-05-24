package com.SFood.common.util;

import java.util.*;
import java.math.BigDecimal;

/**
 * 偏好相关工具方法 — 消除 RecommendationChatController / NLPController 间的重复。
 */
public final class PreferenceUtils {

    private PreferenceUtils() {}

    /**
     * 合并两个偏好对象（去重合并 tastes / taboos / dishes）。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergePreferences(Map<String, Object> existing, Map<String, Object> newPref) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("tastes", mergeList(
                existing != null ? existing.get("tastes") : null,
                newPref != null ? newPref.get("tastes") : null));
        merged.put("taboos", mergeList(
                existing != null ? existing.get("taboos") : null,
                newPref != null ? newPref.get("taboos") : null));
        merged.put("dishes", mergeList(
                existing != null ? existing.get("dishes") : null,
                newPref != null ? newPref.get("dishes") : null));
        return merged;
    }

    /**
     * 合并两个列表，去重。
     */
    public static List<String> mergeList(Object first, Object second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        append(set, first);
        append(set, second);
        return new ArrayList<>(set);
    }

    private static void append(LinkedHashSet<String> set, Object listObj) {
        if (!(listObj instanceof List<?> list)) return;
        for (Object item : list) {
            if (item != null) {
                String value = item.toString().trim();
                if (!value.isEmpty()) set.add(value);
            }
        }
    }

    /**
     * 创建空偏好 Map。
     */
    public static Map<String, Object> createEmptyPreference() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("tastes", new ArrayList<String>());
        prefs.put("taboos", new ArrayList<String>());
        prefs.put("dishes", new ArrayList<String>());
        return prefs;
    }
}
