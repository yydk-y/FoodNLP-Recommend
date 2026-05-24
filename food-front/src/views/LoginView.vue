<template>
  <div class="login-container">
    <div class="login-card card-glass">
      <h2 class="gradient-text">智能点餐系统</h2>
      <div class="tab">
        <span :class="{ active: isLogin }" @click="isLogin = true">登录</span>
        <span :class="{ active: !isLogin }" @click="isLogin = false">注册</span>
      </div>
      <form @submit.prevent="handleSubmit">
        <div class="field">
          <input type="text" v-model="phone" placeholder="手机号" class="form-input" :class="{ 'input-error': errors.phone }" @input="validatePhone()" />
          <span v-if="errors.phone" class="error-text">{{ errors.phone }}</span>
        </div>
        <div class="field">
          <input type="password" v-model="password" placeholder="密码" class="form-input" :class="{ 'input-error': errors.password }" @input="validatePassword()" />
          <span v-if="errors.password" class="error-text">{{ errors.password }}</span>
        </div>
        <div v-if="!isLogin" class="field">
          <input type="text" v-model="nickname" placeholder="昵称" class="form-input" :class="{ 'input-error': errors.nickname }" @input="validateNickname()" />
          <span v-if="errors.nickname" class="error-text">{{ errors.nickname }}</span>
        </div>
        <button type="submit" class="btn-primary">{{ isLogin ? '登录' : '注册' }}</button>
      </form>
      <div class="demo-hint">演示账号: 13800000000 / 123456</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {login, register} from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const isLogin = ref(true)
const phone = ref('')
const password = ref('')
const nickname = ref('')
const errors = ref({ phone: '', password: '', nickname: '' })

const phoneRegex = /^1[3-9]\d{9}$/

function validatePhone() {
  if (phone.value && !phoneRegex.test(phone.value)) {
    errors.value.phone = '手机号格式不正确'
  } else {
    errors.value.phone = ''
  }
}

function validatePassword() {
  if (!isLogin.value && password.value && (password.value.length < 6 || password.value.length > 10)) {
    errors.value.password = '密码长度必须在6到10位之间'
  } else {
    errors.value.password = ''
  }
}

function validateNickname() {
  if (!isLogin.value && nickname.value) {
    errors.value.nickname = ''
  }
}

function validateForm() {
  validatePhone()
  validatePassword()
  validateNickname()
  return !errors.value.phone && !errors.value.password && !errors.value.nickname
}

const handleSubmit = async () => {
  if (!validateForm()) return

  if (isLogin.value) {
    // 调用真实的后端登录接口
    
    try {
      const response = await login({
        phone: phone.value,
        password: password.value
      })
      
      // 根据您提供的后端返回格式处理数据
      if (response.data.code === 200) {
        const { accessToken, userInfo } = response.data.data
        
        // 调用store的login方法存储token和用户数据
        userStore.login({
          id: userInfo.userId,
          phone: userInfo.phone,
          nickname: userInfo.nickname,
          preferences: [], // 根据实际接口返回调整
          dietaryRestrictions: [], // 根据实际接口返回调整
          isAdmin: userInfo.isAdmin || false,
          token: accessToken
        })
        
        // 登录成功后跳转到目标页面或首页
        const redirect = router.currentRoute.value.query.redirect
        router.push(redirect || '/')
      } else {
        alert(response.data.message || '登录失败')
      }
    } catch (error) {
      console.error('登录失败:', error)
      alert(error.response?.data?.message || '登录失败，请检查网络连接')
    }
  } else {
    // 调用真实的后端注册接口
    
    try {
      const response = await register({
        phone: phone.value,
        password: password.value,
        nickname: nickname.value
      })
      
      // 根据您提供的后端返回格式处理数据
      if (response.data.code === 200) {
        const userData = response.data.data
        
        // 注册成功后自动登录
        userStore.login({
          id: userData.userId,
          phone: userData.phone,
          nickname: userData.nickname,
          preferences: [],
          dietaryRestrictions: [],
          isAdmin: userData.isAdmin || false,
          token: '' // 注册接口可能不返回token，需要登录获取
        })
        
        alert('注册成功！')
        router.push('/')
      } else {
        alert(response.data.message || '注册失败')
      }
    } catch (error) {
      console.error('注册失败:', error)
      alert(error.response?.data?.message || '注册失败，请检查网络连接')
    }
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background:
    radial-gradient(circle at 10% 10%, rgba(67, 56, 202, 0.25), transparent 38%),
    radial-gradient(circle at 90% 0%, rgba(109, 40, 217, 0.2), transparent 34%),
    linear-gradient(150deg, #eef1ff, #f5f6fc);
  font-family: 'Inter', 'Poppins', 'PingFang SC', system-ui, -apple-system, sans-serif;
}

.login-card {
  width: 400px;
  max-width: 90%;
  padding: 40px;
  text-align: center;
  border-radius: 20px;
  box-shadow: var(--shadow-glass);
  transition: all 0.3s ease;
  border: 1px solid var(--color-border-light);
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.78));
}

.login-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.gradient-text {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  background-clip: text;
  color: transparent;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 32px;
  letter-spacing: -0.5px;
}

.tab {
  display: flex;
  margin-bottom: 32px;
  border-bottom: 1px solid var(--color-border);
  position: relative;
}

.tab span {
  flex: 1;
  padding: 12px 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-sub);
  transition: all 0.2s ease;
  position: relative;
  z-index: 1;
}

.tab span:hover {
  color: var(--color-primary);
}

.tab span.active {
  color: var(--color-primary);
  font-weight: 600;
}

.tab span.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 40%;
  height: 3px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end), var(--color-gradient-accent));
  border-radius: 2px;
  transition: all 0.3s ease;
}

.form-input {
  width: 100%;
  padding: 16px 20px;
  margin-bottom: 20px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.12);
  background: rgba(255, 255, 255, 0.95);
}

.btn-primary {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 8px;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(67, 56, 202, 0.28);
  filter: brightness(1.05);
}

.btn-primary:active {
  transform: translateY(0);
}

.demo-hint {
  margin-top: 24px;
  font-size: 14px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.7);
  padding: 12px 16px;
  border-radius: 12px;
  backdrop-filter: blur(8px);
  border: 1px solid var(--color-border-light);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-card {
    padding: 32px 24px;
  }
  
  .gradient-text {
    font-size: 24px;
    margin-bottom: 24px;
  }
  
  .tab {
    margin-bottom: 24px;
  }
  
  .form-input {
    padding: 14px 18px;
    margin-bottom: 16px;
  }
  
  .btn-primary {
    padding: 14px;
  }
}
.field {
  margin-bottom: 20px;
  text-align: left;
}

.field .form-input {
  margin-bottom: 0;
}

.error-text {
  display: block;
  color: #e53e3e;
  font-size: 12px;
  margin-top: 4px;
  padding-left: 4px;
}

.input-error {
  border-color: #e53e3e !important;
  box-shadow: 0 0 0 3px rgba(229, 62, 62, 0.12) !important;
}
</style>