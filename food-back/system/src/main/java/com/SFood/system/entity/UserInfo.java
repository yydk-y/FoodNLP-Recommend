package com.SFood.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户信息实体类
 */
@Data
@TableName("user_info")
public class UserInfo {
    
    /** 用户唯一ID */
    @TableId(type = IdType.AUTO)
    private Long userId;
    
    /** 手机号（登录账号） */
    private String phone;
    
    /** 加密密码（SHA256） */
    private String password;
    
    /** 昵称 */
    private String nickname;
    
    /** 是否是管理员 false-普通用户 true-管理员 */
    private Boolean isAdmin;
    
    /** 状态 1-正常 0-禁用 */
    private Integer status;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}