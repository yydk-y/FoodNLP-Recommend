package com.SFood.system.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 登录请求DTO
 */
@Data
public class LoginDTO {
    
    @NotBlank(message = "手机号不能为空")
    private String phone;
    
    @NotBlank(message = "密码不能为空")
    private String password;
}