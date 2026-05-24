package com.SFood.system.dto;

import lombok.Data;
import java.util.List;

/**
 * 分页查询结果DTO
 */
@Data
public class PageDTO<T> {
    
    /** 当前页码 */
    private Integer pageNum;
    
    /** 每页大小 */
    private Integer pageSize;
    
    /** 总记录数 */
    private Long total;
    
    /** 总页数 */
    private Integer totalPages;
    
    /** 数据列表 */
    private List<T> list;
    
    public PageDTO() {
    }
    
    public PageDTO(Integer pageNum, Integer pageSize, Long total, List<T> list) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.list = list;
        
        // 计算总页数
        if (pageSize > 0 && total > 0) {
            this.totalPages = (int) Math.ceil((double) total / pageSize);
        } else {
            this.totalPages = 0;
        }
    }
}