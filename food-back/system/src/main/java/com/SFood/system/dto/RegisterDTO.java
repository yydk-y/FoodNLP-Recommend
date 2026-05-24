package com.SFood.system.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 注册请求DTO
 */
@Data
public class RegisterDTO {
    
    @NotBlank(message = "手机号不能为空")
    private String phone;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    @NotBlank(message = "昵称不能为空")
    private String nickname;
}