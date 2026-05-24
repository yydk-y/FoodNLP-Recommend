package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户偏好实体 → 映射数据库 user_preference 表。
 *
 * <p>存储已注册用户的持久化饮食偏好，是推荐系统的重要数据来源之一。
 *
 * <p>三个偏好字段均为 JSON 数组格式的字符串：
 * <ul>
 *   <li>taste — 口味偏好，如 "["辣","酸"]"，推荐算法用作匹配维度</li>
 *   <li>ingredient — 食材偏好，如 "["鸡肉","豆腐"]"，推荐算法用作匹配维度</li>
 *   <li>taboo — 忌口，如 "["香菜","海鲜"]"，推荐前过滤含忌口的菜品</li>
 * </ul>
 *
 * <p>此实体同时也是 UserPreferenceServiceImpl 中历史偏好的 Redis 缓存结构。
 */
@Data
@TableName("user_preference")
public class UserPreference {

    /** 偏好ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long prefId;

    /** 用户ID — 与 UserInfo 表关联 */
    private Long userId;

    /** 口味偏好，JSON数组格式，如 ["辣","酸","甜"] */
    private String taste;

    /** 食材偏好，JSON数组格式，如 ["鸡肉","豆腐","虾"] */
    private String ingredient;

    /** 忌口，JSON数组格式，如 ["香菜","海鲜","辣"] */
    private String taboo;

    /** 最后更新时间 — 用于缓存失效判断 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
