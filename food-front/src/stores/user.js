import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const userId = ref(null)
  const phone = ref('')
  const nickname = ref('')
  const preferences = ref([])      // 初始化为空数组
  const dietaryRestrictions = ref([]) // 初始化为空数组
  const ingredientPreferences = ref([]) // 新增食材偏好
  const isAdmin = ref(false)
  const token = ref('')

  const isLoggedIn = computed(() => !!token.value)

  // 登录
  function login(userInfo) {
    userId.value = userInfo.id
    phone.value = userInfo.phone
    nickname.value = userInfo.nickname
    preferences.value = Array.isArray(userInfo.preferences) ? userInfo.preferences : []
    dietaryRestrictions.value = Array.isArray(userInfo.dietaryRestrictions) ? userInfo.dietaryRestrictions : []
    ingredientPreferences.value = Array.isArray(userInfo.ingredientPreferences) ? userInfo.ingredientPreferences : []
    isAdmin.value = userInfo.isAdmin || false
    token.value = userInfo.token || ''
    localStorage.setItem('user', JSON.stringify({
      id: userId.value,
      phone: phone.value,
      nickname: nickname.value,
      preferences: preferences.value,
      dietaryRestrictions: dietaryRestrictions.value,
      ingredientPreferences: ingredientPreferences.value,
      isAdmin: isAdmin.value,
      token: token.value
    }))
  }

  function logout() {
    userId.value = null
    phone.value = ''
    nickname.value = ''
    preferences.value = []   // 重置为空数组
    dietaryRestrictions.value = []
    ingredientPreferences.value = []
    isAdmin.value = false
    token.value = ''
    localStorage.removeItem('user')
    localStorage.removeItem('cart')
    localStorage.removeItem('order')
    localStorage.removeItem('orders')
  }

  // 从 localStorage 恢复
  function restore() {
    const stored = localStorage.getItem('user')
    if (stored) {
      const user = JSON.parse(stored)
      userId.value = user.id
      phone.value = user.phone
      nickname.value = user.nickname
      preferences.value = Array.isArray(user.preferences) ? user.preferences : []
      dietaryRestrictions.value = Array.isArray(user.dietaryRestrictions) ? user.dietaryRestrictions : []
      ingredientPreferences.value = Array.isArray(user.ingredientPreferences) ? user.ingredientPreferences : []
      isAdmin.value = user.isAdmin
      token.value = user.token
    }
  }

  // 更新偏好
  function updatePreferences(prefs, restrictions, ingreds) {
    preferences.value = Array.isArray(prefs) ? prefs : []
    dietaryRestrictions.value = Array.isArray(restrictions) ? restrictions : []
    ingredientPreferences.value = Array.isArray(ingreds) ? ingreds : []
    const stored = JSON.parse(localStorage.getItem('user') || '{}')
    stored.preferences = preferences.value
    stored.dietaryRestrictions = dietaryRestrictions.value
    stored.ingredientPreferences = ingredientPreferences.value
    localStorage.setItem('user', JSON.stringify(stored))
  }

  return {
    userId,
    phone,
    nickname,
    preferences,
    dietaryRestrictions,
    ingredientPreferences,
    isAdmin,
    token,
    isLoggedIn,
    login,
    logout,
    restore,
    updatePreferences
  }
})