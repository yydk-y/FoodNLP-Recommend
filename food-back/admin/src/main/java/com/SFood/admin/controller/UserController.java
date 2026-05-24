package com.SFood.admin.controller;

import com.SFood.system.dto.PageDTO;
import com.SFood.system.dto.UserQueryDTO;
import com.SFood.system.entity.UserInfo;
import com.SFood.system.service.UserService;
import com.SFood.common.dto.ResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户信息控制器
 */
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 根据用户ID查询用户信息
     */
    @GetMapping("/{userId}")
    public ResultDTO<UserInfo> getUserById(@PathVariable Long userId) {
        UserInfo userInfo = userService.findById(userId);
        if (userInfo != null) {
            userInfo.setPassword(null); // 隐藏密码
        }
        return ResultDTO.success(userInfo);
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/update")
    public ResultDTO<UserInfo> updateUser(@RequestBody UserInfo userInfo) {
        UserInfo result = userService.updateUser(userInfo);
        result.setPassword(null); // 隐藏密码
        return ResultDTO.success(result);
    }
    
    /**
     * 修改密码
     */
    @PutMapping("/change-password")
    public ResultDTO<Boolean> changePassword(@RequestParam Long userId,
                                           @RequestParam String oldPassword,
                                           @RequestParam String newPassword) {
        boolean result = userService.changePassword(userId, oldPassword, newPassword);
        return ResultDTO.success(result);
    }
    
    /**
     * 重置密码
     */
    @PutMapping("/reset-password")
    public ResultDTO<Boolean> resetPassword(@RequestParam String phone,
                                           @RequestParam String newPassword) {
        boolean result = userService.resetPassword(phone, newPassword);
        return ResultDTO.success(result);
    }
    
    /**
     * 获取所有用户列表（管理员用）
     */
    @GetMapping("/all")
    public ResultDTO<List<UserInfo>> getAllUsers() {
        List<UserInfo> users = userService.getAllUsers();
        // 隐藏所有用户的密码
        users.forEach(user -> user.setPassword(null));
        return ResultDTO.success(users);
    }
    
    /**
     * 更新用户状态
     */
    @PutMapping("/status")
    public ResultDTO<Boolean> updateUserStatus(@RequestParam Long userId,
                                             @RequestParam Integer status) {
        boolean result = userService.updateUserStatus(userId, status);
        return ResultDTO.success(result);
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    public ResultDTO<Boolean> deleteUser(@PathVariable Long userId) {
        boolean result = userService.deleteUser(userId);
        return ResultDTO.success(result);
    }
    
    /**
     * 分页查询用户列表
     */
    @GetMapping("/page")
    public ResultDTO<PageDTO<UserInfo>> getUserPage(@ModelAttribute UserQueryDTO queryDTO) {
        PageDTO<UserInfo> pageResult = userService.getUserPage(queryDTO);
        return ResultDTO.success(pageResult);
    }
}