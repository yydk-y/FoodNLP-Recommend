package com.SFood.system.service;

import com.SFood.system.dto.LoginDTO;
import com.SFood.system.dto.RegisterDTO;
import com.SFood.system.dto.PageDTO;
import com.SFood.system.dto.UserQueryDTO;
import com.SFood.system.entity.UserInfo;
import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {
    
    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 注册结果
     */
    UserInfo register(RegisterDTO registerDTO);
    
    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 登录成功的用户信息
     */
    UserInfo login(LoginDTO loginDTO);
    
    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 用户信息
     */
    UserInfo findByPhone(String phone);
    
    /**
     * 根据用户ID查询用户
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfo findById(Long userId);
    
    /**
     * 更新用户信息
     * @param userInfo 用户信息
     * @return 更新后的用户信息
     */
    UserInfo updateUser(UserInfo userInfo);
    
    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean changePassword(Long userId, String oldPassword, String newPassword);
    
    /**
     * 重置密码
     * @param phone 手机号
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean resetPassword(String phone, String newPassword);
    
    /**
     * 获取所有用户列表（管理员用）
     * @return 用户列表
     */
    List<UserInfo> getAllUsers();
    
    /**
     * 更新用户状态
     * @param userId 用户ID
     * @param status 状态（1-正常，0-禁用）
     * @return 是否成功
     */
    boolean updateUserStatus(Long userId, Integer status);
    
    /**
     * 删除用户
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteUser(Long userId);
    
    /**
     * 分页查询用户列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageDTO<UserInfo> getUserPage(UserQueryDTO queryDTO);
}