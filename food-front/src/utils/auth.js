// src/utils/auth.js

export const setToken = (token) => {
  localStorage.setItem('token', token)
}

export const getToken = () => {
  return localStorage.getItem('token')
}

export const removeToken = () => {
  localStorage.removeItem('token')
}

export const setUserInfo = (userInfo) => {
  localStorage.setItem('userInfo', JSON.stringify(userInfo))
}

export const getUserInfo = () => {
  const info = localStorage.getItem('userInfo')
  return info ? JSON.parse(info) : null
}

export const removeUserInfo = () => {
  localStorage.removeItem('userInfo')
}