package com.SFood.system.service.impl;

import com.SFood.common.util.PasswordUtil;
import com.SFood.system.dto.LoginDTO;
import com.SFood.system.dto.RegisterDTO;
import com.SFood.system.entity.UserInfo;
import com.SFood.system.mapper.UserInfoMapper;
import com.SFood.system.service.UserService;
import com.SFood.system.service.UserPreferenceService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.SFood.system.dto.PageDTO;
import com.SFood.system.dto.UserQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired(required = false)
    private UserPreferenceService userPreferenceService;

    @Override
    public UserInfo register(RegisterDTO registerDTO) {
        // 验证手机号是否已存在
        UserInfo existingUser = findByPhone(registerDTO.getPhone());
        if (existingUser != null) {
            throw new RuntimeException("手机号已存在");
        }
        //todo 格式验证
        if (registerDTO.getPassword().length() < 6 || registerDTO.getPassword().length() > 10) {
            throw new RuntimeException("密码长度必须在6到10位之间");
        }
        if (StringUtils.isEmpty(registerDTO.getNickname())) {
            throw new RuntimeException("昵称不能为空");
        }
        if (StringUtils.isEmpty(registerDTO.getPhone())) {
            throw new RuntimeException("手机号不能为空");
        }
        if (!registerDTO.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new RuntimeException("手机号格式不正确");
        }
        // 创建新用户
        UserInfo userInfo = new UserInfo();
        userInfo.setPhone(registerDTO.getPhone());
        userInfo.setPassword(PasswordUtil.sha256(registerDTO.getPassword()));
        userInfo.setNickname(registerDTO.getNickname());
        userInfo.setIsAdmin(false); // 默认普通用户
        userInfo.setStatus(1); // 默认正常状态
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());

        // 保存用户
        int result = userInfoMapper.insert(userInfo);
        if (result > 0) {
            return userInfo;
        }
        throw new RuntimeException("注册失败");
    }

    @Override
    public UserInfo login(LoginDTO loginDTO) {
        // 根据手机号查询用户
        UserInfo userInfo = findByPhone(loginDTO.getPhone());
        if (userInfo == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码
        if (!PasswordUtil.verify(loginDTO.getPassword(), userInfo.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 验证用户状态
        if (userInfo.getStatus() != 1) {
            throw new RuntimeException("用户已被禁用");
        }

        // 更新最后登录时间
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfoMapper.updateById(userInfo);

        if (userPreferenceService != null) {
            try {
                userPreferenceService.updatePreferenceByRecentOrders(userInfo.getUserId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return userInfo;
    }

    @Override
    public UserInfo findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }

        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        return userInfoMapper.selectOne(queryWrapper);
    }
    
    @Override
    public UserInfo findById(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        return userInfoMapper.selectById(userId);
    }
    
    @Override
    public UserInfo updateUser(UserInfo userInfo) {
        if (userInfo == null || userInfo.getUserId() == null) {
            throw new RuntimeException("用户信息不完整");
        }
        
        // 验证用户是否存在
        UserInfo existingUser = userInfoMapper.selectById(userInfo.getUserId());
        if (existingUser == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 更新基本信息（不更新密码）
        existingUser.setNickname(userInfo.getNickname());
        existingUser.setUpdateTime(LocalDateTime.now());
        existingUser.setIsAdmin(userInfo.getIsAdmin());
        userInfoMapper.updateById(existingUser);
        return existingUser;
    }
    
    @Override
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || !StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new RuntimeException("参数不完整");
        }
        
        if (newPassword.length() < 6 || newPassword.length() > 10) {
            throw new RuntimeException("新密码长度必须在6到10位之间");
        }
        
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 验证旧密码
        if (!PasswordUtil.verify(oldPassword, userInfo.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        
        // 更新密码
        userInfo.setPassword(PasswordUtil.sha256(newPassword));
        userInfo.setUpdateTime(LocalDateTime.now());
        
        int result = userInfoMapper.updateById(userInfo);
        return result > 0;
    }
    
    @Override
    public boolean resetPassword(String phone, String newPassword) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(newPassword)) {
            throw new RuntimeException("参数不完整");
        }
        
        if (newPassword.length() < 6 || newPassword.length() > 10) {
            throw new RuntimeException("新密码长度必须在6到10位之间");
        }
        
        UserInfo userInfo = findByPhone(phone);
        if (userInfo == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 重置密码
        userInfo.setPassword(PasswordUtil.sha256(newPassword));
        userInfo.setUpdateTime(LocalDateTime.now());
        
        int result = userInfoMapper.updateById(userInfo);
        return result > 0;
    }
    
    @Override
    public List<UserInfo> getAllUsers() {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        return userInfoMapper.selectList(queryWrapper);
    }
    
    @Override
    public boolean updateUserStatus(Long userId, Integer status) {
        if (userId == null || status == null) {
            throw new RuntimeException("参数不完整");
        }
        
        if (status != 0 && status != 1) {
            throw new RuntimeException("状态值不合法");
        }
        
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo == null) {
            throw new RuntimeException("用户不存在");
        }
        
        userInfo.setStatus(status);
        userInfo.setUpdateTime(LocalDateTime.now());
        
        int result = userInfoMapper.updateById(userInfo);
        return result > 0;
    }
    
    @Override
    public boolean deleteUser(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo == null) {
            throw new RuntimeException("用户不存在");
        }
        
        int result = userInfoMapper.deleteById(userId);
        return result > 0;
    }
    
    @Override
    public PageDTO<UserInfo> getUserPage(UserQueryDTO queryDTO) {
        // 参数验证
        if (queryDTO == null) {
            queryDTO = new UserQueryDTO();
        }
        if (queryDTO.getPageNum() == null || queryDTO.getPageNum() < 1) {
            queryDTO.setPageNum(1);
        }
        if (queryDTO.getPageSize() == null || queryDTO.getPageSize() < 1) {
            queryDTO.setPageSize(10);
        }
        
        // 创建分页对象
        Page<UserInfo> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        
        // 构建查询条件
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        
        // 手机号模糊查询
        if (StringUtils.hasText(queryDTO.getPhone())) {
            queryWrapper.like("phone", queryDTO.getPhone());
        }
        
        // 昵称模糊查询
        if (StringUtils.hasText(queryDTO.getNickname())) {
            queryWrapper.like("nickname", queryDTO.getNickname());
        }
        
        // 状态查询
        if (queryDTO.getStatus() != null) {
            queryWrapper.eq("status", queryDTO.getStatus());
        }
        
        // 管理员查询
        if (queryDTO.getIsAdmin() != null) {
            queryWrapper.eq("is_admin", queryDTO.getIsAdmin());
        }
        
        // 时间范围查询
        if (StringUtils.hasText(queryDTO.getStartTime())) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(queryDTO.getStartTime() + " 00:00:00", 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.ge("create_time", startTime);
            } catch (Exception e) {
                throw new RuntimeException("开始时间格式错误，请使用yyyy-MM-dd格式");
            }
        }
        
        if (StringUtils.hasText(queryDTO.getEndTime())) {
            try {
                LocalDateTime endTime = LocalDateTime.parse(queryDTO.getEndTime() + " 23:59:59", 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.le("create_time", endTime);
            } catch (Exception e) {
                throw new RuntimeException("结束时间格式错误，请使用yyyy-MM-dd格式");
            }
        }
        
        // 按创建时间倒序排列
        queryWrapper.orderByDesc("create_time");
        
        // 执行分页查询
        Page<UserInfo> resultPage = userInfoMapper.selectPage(page, queryWrapper);
        
        // 转换为自定义分页DTO
        PageDTO<UserInfo> pageDTO = new PageDTO<>(
            (int) resultPage.getCurrent(),
            (int) resultPage.getSize(),
            resultPage.getTotal(),
            resultPage.getRecords()
        );
        
        // 隐藏密码
        pageDTO.getList().forEach(user -> user.setPassword(null));
        
        return pageDTO;
    }
}