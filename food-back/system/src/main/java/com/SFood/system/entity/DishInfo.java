package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品信息实体 → 映射数据库 dish_info 表。
 *
 * <p>推荐系统的核心操作对象。
 * <ul>
 *   <li>推荐算法基于 taste（口味）、ingredient（食材）、description（描述）计算余弦相似度</li>
 *   <li>heat（热度/销量）用于热门推荐和多样性排序</li>
 *   <li>matchScore 是非持久化字段（@TableField(exist=false)），仅在推荐结果中携带</li>
 *   <li>status=1 表示上架，status=0 表示下架（推荐只取上架的菜品）</li>
 * </ul>
 */
@Data
@TableName("dish_info")
public class DishInfo {

    /** 菜品ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long dishId;

    /** 菜品名称 — 用于菜名精确/子串匹配 */
    private String dishName;

    /** 分类ID — 用于 ensureDiversity 的类别多样性保证和 injectExploreDishes 的跨类别探索 */
    private Long categoryId;

    /** 菜品价格 — 用于排序（便宜/贵的筛选） */
    private BigDecimal price;

    /** 口味，JSON数组格式，如 "["辣","香"]" — 推荐算法的主要匹配维度，权重0.8 */
    private String taste;

    /** 主要食材，JSON数组格式，如 "["鸡肉","花生"]" — 推荐算法的次要匹配维度，权重0.65 */
    private String ingredient;

    /** 菜品图片URL */
    private String imageUrl;

    /** 热度（销量/点击量）— 用于热门推荐和按热度排序 */
    private Integer heat;

    /** 菜品描述 — 推荐算法中权重最低的维度（0.25），用于隐式口味检测和特征补充 */
    private String description;

    /**
     * 状态：1-上架，0-下架。
     * getAvailableDishes() 只取 status=1 的菜品。
     */
    private Integer status;

    /**
     * 推荐匹配度（非数据库字段）。
     * 由推荐算法计算后设置，携带到前端展示"匹配度：92%"。
     */
    @TableField(exist = false)
    private Double matchScore;

    /** 创建时间（自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
