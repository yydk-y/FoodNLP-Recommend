// src/api/user.js
import request from './request'

// 用户登录
export const login = (data) => {
  return request.post('/auth/login', data)
}

// 用户注册
export const register = (data) => {
  return request.post('/auth/register', data)
}

// 获取用户信息
export const getUserInfo = () => {
  return request.get('/user/info')
}

// 更新用户信息
export const updateUserInfo = (data) => {
  return request.put('/user/info', data)
}

// 更新用户偏好
export const updatePreferences = (data) => {
  return request.put('/user/preferences', data)
}

// 获取用户列表（管理端用）
export const getUsers = () => {
  return request.get('/admin/users')
}