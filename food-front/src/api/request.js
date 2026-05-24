import axios from 'axios'

const request = axios.create({
  baseURL: '/api',// 代理到后端，暂未启用
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json'
  }
  // withCredentials: true
})

// 请求拦截器（携带token）
request.interceptors.request.use(config => {
  const token = localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')).token : ''
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function handleAuthError() {
  localStorage.removeItem('user')
  localStorage.removeItem('cart')
  localStorage.removeItem('order')
  localStorage.removeItem('orders')
  window.location.href = '/login'
}

// 响应拦截器（处理token过期）
request.interceptors.response.use(
  response => {
    if (response.data && response.data.code === 401) {
      handleAuthError()
      return Promise.reject(new Error('token已过期，请重新登录'))
    }
    return response
  },
  error => {
    if (error.response && error.response.status === 401) {
      handleAuthError()
    }
    return Promise.reject(error)
  }
)

export default request