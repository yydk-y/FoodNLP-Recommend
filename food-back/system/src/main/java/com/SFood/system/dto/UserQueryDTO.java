package com.SFood.system.dto;

import lombok.Data;

/**
 * 用户查询条件DTO
 */
@Data
public class UserQueryDTO {
    
    /** 手机号（模糊查询） */
    private String phone;
    
    /** 昵称（模糊查询） */
    private String nickname;
    
    /** 用户状态（0-禁用，1-正常） */
    private Integer status;
    
    /** 是否是管理员 */
    private Boolean isAdmin;
    
    /** 开始时间（创建时间） */
    private String startTime;
    
    /** 结束时间（创建时间） */
    private String endTime;
    
    /** 当前页码 */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 10;
}