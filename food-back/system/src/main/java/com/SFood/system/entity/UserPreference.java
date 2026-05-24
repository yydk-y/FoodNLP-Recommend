package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户偏好实体类
 */
@Data
@TableName("user_preference")
public class UserPreference {
    
    /** 偏好ID */
    @TableId(type = IdType.AUTO)
    private Long prefId;
    
    /** 用户ID */
    private Long userId;
    
    /** 口味偏好，JSON数组 */
    private String taste;
    
    /** 食材偏好，JSON数组 */
    private String ingredient;
    
    /** 忌口，JSON数组 */
    private String taboo;
    
    /** 最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}