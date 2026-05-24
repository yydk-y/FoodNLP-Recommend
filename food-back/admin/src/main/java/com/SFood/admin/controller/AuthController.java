package com.SFood.admin.controller;

import com.SFood.common.util.JwtUtil;
import com.SFood.system.dto.LoginDTO;
import com.SFood.system.dto.RegisterDTO;
import com.SFood.common.dto.ResultDTO;
import com.SFood.system.dto.TokenDTO;
import com.SFood.system.entity.UserInfo;
import com.SFood.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器（登录注册）
 */
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResultDTO register(@Valid @RequestBody RegisterDTO registerDTO) {
        UserInfo userInfo = userService.register(registerDTO);
        // 返回时隐藏密码
        userInfo.setPassword(null);
        return ResultDTO.success("注册成功", userInfo);
    }

    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResultDTO login(@Valid @RequestBody LoginDTO loginDTO) {
        try {
            UserInfo userInfo = userService.login(loginDTO);

            // 生成JWT token
            String token = jwtUtil.generateToken(userInfo.getUserId(), userInfo.getPhone());
            Long expiresIn = jwtUtil.getExpirationDateFromToken(token).getTime() / 1000;

            // 返回时隐藏密码
            userInfo.setPassword(null);

            // 返回token和用户信息
            TokenDTO tokenDTO = new TokenDTO(token, expiresIn, userInfo);
            return ResultDTO.success("登录成功", tokenDTO);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }


    /**
     * 检查手机号是否已存在
     * @param phone 手机号
     * @return 检查结果
     */
    @GetMapping("/checkPhone")
    public ResultDTO checkPhone(@RequestParam String phone) {
        try {
            UserInfo userInfo = userService.findByPhone(phone);
            return ResultDTO.success(userInfo == null);
        } catch (Exception e) {
            return ResultDTO.error(e.getMessage());
        }
    }
}