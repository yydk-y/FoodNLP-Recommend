package com.SFood.admin.controller;

import com.SFood.common.util.SecurityContextUtil;
import com.SFood.common.dto.ResultDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试控制器
 * 用于演示token验证拦截器的使用
 */
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 需要token验证的接口
     * 只有携带有效token才能访问
     */
    @GetMapping("/secure")
    public ResultDTO secureEndpoint() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        String phone = SecurityContextUtil.getCurrentUserPhone();
        
        String message = String.format("当前登录用户: ID=%d, 手机号=%s", userId, phone);
        return ResultDTO.success(message);
    }

    /**
     * 公开接口（不需要token验证）
     * 这个接口应该被拦截器放行
     */
    @GetMapping("/public")
    public ResultDTO publicEndpoint() {
        return ResultDTO.success("这是一个公开接口，无需token验证");
    }
}