package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 菜品评分实体类
 */
@Data
@TableName("dish_rating")
public class DishRating {
    
    /** 评分ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long ratingId;
    
    /** 关联用户ID */
    private Long userId;
    
    /** 关联菜品ID */
    private Long dishId;
    
    /** 关联订单编号 */
    private String orderId;
    
    /** 评分（1-5星） */
    private Integer score;
    
    /** 评价内容 */
    private String comment;
    
    /** 评分时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime ratingTime;
}