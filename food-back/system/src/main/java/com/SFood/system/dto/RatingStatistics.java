package com.SFood.system.dto;

import lombok.Data;

/**
 * 评价统计信息DTO
 */
@Data
public class RatingStatistics {
    
    /** 菜品ID */
    private Long dishId;
    
    /** 总评价数 */
    private Integer totalRatings;
    
    /** 平均评分 */
    private Double averageScore;
    
    /** 5星评价数 */
    private Integer fiveStarCount;
    
    /** 4星评价数 */
    private Integer fourStarCount;
    
    /** 3星评价数 */
    private Integer threeStarCount;
    
    /** 2星评价数 */
    private Integer twoStarCount;
    
    /** 1星评价数 */
    private Integer oneStarCount;
    
    /** 好评率（4星及以上占比） */
    private Double positiveRate;
    
    public RatingStatistics() {
        this.totalRatings = 0;
        this.averageScore = 0.0;
        this.fiveStarCount = 0;
        this.fourStarCount = 0;
        this.threeStarCount = 0;
        this.twoStarCount = 0;
        this.oneStarCount = 0;
        this.positiveRate = 0.0;
    }
}