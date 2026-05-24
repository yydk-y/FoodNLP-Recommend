package com.SFood.system.dto;

import lombok.Data;

/**
 * Token响应DTO
 */
@Data
public class TokenDTO {
    
    /**
     * 访问token
     */
    private String accessToken;
    
    /**
     * token类型
     */
    private String tokenType = "Bearer";
    
    /**
     * 过期时间（秒）
     */
    private Long expiresIn;
    
    /**
     * 用户信息
     */
    private Object userInfo;
    
    public TokenDTO(String accessToken, Long expiresIn, Object userInfo) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.userInfo = userInfo;
    }
}