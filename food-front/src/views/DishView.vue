<template>
  <div class="dish-view">
    <div class="view-header">
      <div class="title-wrap">
        <h2 class="view-title">菜品探索</h2>
        <p class="view-subtitle">支持菜名搜索，快速发现今天适合你的菜</p>
      </div>
      <div class="view-chip">菜品探索</div>
      <div class="explore-stats">
        <div><strong>{{ dishList.length }}</strong><span>当前菜品</span></div>
        <div><strong>{{ categories.length }}</strong><span>分类覆盖</span></div>
        <div><strong>{{ activeFilterText }}</strong><span>筛选状态</span></div>
      </div>
    </div>

    <div class="dish-layout">
      <div class="dish-main">
        <div class="filters">
          <div class="search-container">
            <input 
              type="text" 
              v-model="keyword" 
              placeholder="" 
              class="search-input"
              @keyup.enter="handleSearch"
            />
            <button class="search-btn" @click="handleSearch">
              <span class="search-icon">🔍</span>
              <span class="search-text">搜索</span>
            </button>
          </div>
        </div>
        <div class="dish-content">
          <aside class="category-sidebar">
            <button 
              :class="['cat-btn', { active: category === '' }]"
              @click="category=''; pagination.current=1; loadDishes()"
            >
              全部分类
            </button>
            <button
              v-for="cat in categories"
              :key="cat.id"
              :class="['cat-btn', { active: category === cat.name }]"
              @click="category=cat.name; pagination.current=1; loadDishes()"
            >
              {{ cat.name }}
            </button>
          </aside>
          <div class="dish-grid-area">
            <div v-if="keyword || category" class="filter-insight"><span>智能筛选</span><strong>{{ filterInsight }}</strong></div>
            <div class="dish-list compact-grid six-grid">
      <article v-for="dish in dishList" :key="dish.id" class="explore-card" @click="goToDetail(dish)">
        <div class="explore-cover">
          <img :src="resolveImageUrl(dish.image)" :alt="dish.name" />
          <span class="category-chip">{{ dish.category || '精选菜品' }}</span>
          <span v-if="dish.rating !== undefined && dish.rating !== null" class="score-chip">★ {{ Number(dish.rating || 0).toFixed(1) }}</span>
        </div>
        <div class="explore-body">
          <div class="explore-top"><h3>{{ dish.name }}</h3><strong>￥{{ Number(dish.price || 0).toFixed(2) }}</strong></div>
          <p class="explore-desc">{{ dish.description }}</p>
          <div class="explore-reason">{{ getDishInsight(dish) }}</div>
          <div class="explore-meta">
            <span v-for="tag in getDishTags(dish).slice(0, 3)" :key="tag">{{ tag }}</span>
            <span v-if="dish.salesCount" class="sales-pill">销量 {{ dish.salesCount }}</span>
          </div>
          <button type="button" class="add-mini" @click.stop="addDishToCart(dish)">加入购物车</button>
        </div>
      </article>
            <div v-if="dishList.length === 0" class="empty"><div class="empty-icon">🍽️</div><div class="empty-text">暂无菜品</div></div>
        </div>

        <!-- 分页组件 -->
        <div v-if="pagination.total > 0" class="pagination-container">
          <div class="pagination">
            <button 
              class="pagination-btn" 
              :disabled="pagination.current <= 1"
              @click="handlePageChange(pagination.current - 1)"
            >
              上一页
            </button>
            
            <div class="pagination-info">
              第 {{ pagination.current }} 页 / 共 {{ Math.ceil(pagination.total / pagination.pageSize) }} 页
            </div>
            
            <button 
              class="pagination-btn" 
              :disabled="pagination.current >= Math.ceil(pagination.total / pagination.pageSize)"
              @click="handlePageChange(pagination.current + 1)"
            >
              下一页
            </button>
            
            <div class="limit-control">
              <span>每页显示：</span>
              <select v-model="pagination.pageSize" @change="handlePageSizeChange">
                <option value="6">6</option>
                <option value="12">12</option>
                <option value="18">18</option>
              </select>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <div class="loading-text">加载中...</div>
    </div>

    <!-- 底部购物车 -->
    <div class="cart-container" :class="{ 'cart-expanded': isCartExpanded }">
      <!-- 购物车按钮 -->
      <div class="cart-button" @click="toggleCart">
        <div class="cart-icon">
          🛒
          <span v-if="cartItems.length > 0" class="cart-badge">{{ cartItems.length }}</span>
        </div>
        <div class="cart-info">
          <span class="cart-total">￥{{ totalPrice.toFixed(2) }}</span>
          <span class="cart-text">购物车</span>
        </div>
      </div>

      <!-- 购物车内容 -->
      <div v-if="isCartExpanded" class="cart-content">
        <div class="cart-header">
          <h4>购物车</h4>
          <button class="cart-close" @click="toggleCart">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
          </button>
        </div>

        <div v-if="cartItems.length === 0" class="cart-empty">
          <div class="empty-icon">🛒</div>
          <p>购物车为空</p>
        </div>

        <div v-else class="cart-items">
          <div v-for="item in cartItems" :key="item.cartId" class="cart-item">
            <img :src="resolveImageUrl(item.dishImage)" :alt="item.dishName" class="item-image" />
            <div class="item-info">
              <div class="item-name">{{ item.dishName }}</div>
              <div class="item-price">￥{{ item.dishPrice.toFixed(2) }}</div>
            </div>
            <div class="item-controls">
              <button class="qty-btn" @click="updateQuantity(item.cartId, item.quantity - 1)">-</button>
              <span class="item-qty">{{ item.quantity }}</span>
              <button class="qty-btn" @click="updateQuantity(item.cartId, item.quantity + 1)">+</button>
              <button class="delete-btn" @click="removeItem(item.cartId)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div v-if="cartItems.length > 0" class="cart-footer">
          <div class="cart-summary">
            <div class="total-items">共 {{ cartItems.length }} 件商品</div>
            <div class="total-price">总计：￥{{ totalPrice.toFixed(2) }}</div>
          </div>
          <button class="checkout-btn" @click="checkout">立即下单</button>
        </div>
      </div>
    </div>
    <button class="ai-assistant-fab" @click="router.push('/chat')" title="智能点单助手">
      <span class="fab-icon">🤖</span>
      <span class="fab-label">智能点单助手</span>
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { dishInfoAPI, dishCategoryAPI, ratingAPI, cartAPI, orderAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import { resolveImageUrl } from '@/utils/image'

const dishList = ref([])
const categories = ref([]) // 分类列表
const keyword = ref('')
const category = ref('')
const userStore = useUserStore()
const router = useRouter()
const smartChips = ['辣一点', '清淡不油', '下饭菜', '汤类', '高性价比', '不要海鲜']

const activeFilterText = computed(() => keyword.value || category.value ? '已启用' : '全部')
const filterInsight = computed(() => {
  const parts = []
  if (keyword.value) parts.push(`关键词：${keyword.value}`)
  if (category.value) parts.push(`分类：${category.value}`)
  return parts.length ? `已根据 ${parts.join('，')} 为你筛选菜品` : ''
})

const applySmartChip = (chip) => {
  keyword.value = chip
  handleSearch()
}


const getDishTags = (dish) => {
  const tags = [...(dish.flavors || []), ...(dish.ingredients || [])]
  if (dish.category) tags.unshift(dish.category)
  return [...new Set(tags.filter(Boolean))]
}

const getDishInsight = (dish) => {
  const tags = getDishTags(dish)
  if (tags.length) return `适合想吃 ${tags.slice(0, 2).join('、')} 的时候选择。`
  if (dish.rating) return `当前评分 ${Number(dish.rating).toFixed(1)}，适合作为日常点餐选择。`
  return '精选菜品，适合作为日常点餐选择。'
}// 分页相关
const pagination = ref({
  current: 1,
  pageSize: 6,
  total: 0
})

// 加载状态
const loading = ref(false)

// 购物车相关
const cartItems = ref([])
const isCartExpanded = ref(false)

// 加载分类数据
const loadCategories = async () => {
  try {
    const res = await dishCategoryAPI.getAll()
    if (res.data.code === 200) {
      categories.value = res.data.data.map(cat => ({
        id: cat.categoryId || cat.id,
        name: cat.categoryName || cat.name
      }))
    }
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 加载菜品数据
const loadDishes = async () => {
  loading.value = true
  try {
    let response = null
    let isSearchMode = false
    
    // 如果有搜索关键词，调用后端分页搜索API
    if (keyword.value.trim()) {
      response = await dishInfoAPI.searchByNamePage(
        keyword.value.trim(), 
        pagination.value.current, 
        pagination.value.pageSize
      )
      isSearchMode = true
    } else if (category.value) {
      // 如果有分类选择，调用后端分类分页查询API
      const selectedCategory = categories.value.find(cat => cat.name === category.value)
      if (selectedCategory) {
        response = await dishInfoAPI.getDishesByCategoryScorePage(
          selectedCategory.id,
          pagination.value.current, 
          pagination.value.pageSize
        )
        isSearchMode = true
      }
    } else {
      // 没有搜索关键词和分类选择，使用分页查询API
      response = await dishInfoAPI.getByScore(
        pagination.value.current, 
        pagination.value.pageSize
      )
      isSearchMode = true
    }
    
    if (response.data.code === 200) {
      let dishesData = []
      let total = 0
      
      // 处理分页搜索返回的数据结构
      if (isSearchMode) {
        // 分页搜索返回的数据结构
        dishesData = response.data.data.records || response.data.data
        total = response.data.data.total || 0
        
        // 更新分页信息
        pagination.value.total = total
      } else {
        // 非搜索情况返回的数据结构
        dishesData = response.data.data
        total = dishesData.length
      }
      
      if (dishesData.length > 0) {
        // 处理菜品数据
        let filteredDishes = dishesData.map(dishWithCategory => {
          const dish = dishWithCategory.dishInfo || dishWithCategory
          const categoryInfo = dishWithCategory.dishCategory
          
          return {
            id: dish.dishId || dish.id,
            name: dish.dishName || dish.name,
            category: categoryInfo ? categoryInfo.categoryName : (dish.categoryName || dish.category),
            categoryId: dish.categoryId,
            price: dish.price,
            flavors: dish.taste ? dish.taste.split(',').map(s => s.trim()).filter(s => s) : [],
            ingredients: dish.ingredient ? dish.ingredient.split(',').map(s => s.trim()).filter(s => s) : [],
            description: dish.description || '这是一道美味的菜品。',
            image: resolveImageUrl(dish.imageUrl || dish.image),
            isAvailable: Number(dish.status) === 1,
            salesCount: dish.salesCount || 0,
            rating: null // 初始化为null，稍后通过API获取
          }
        })
        
        // 后端应只返回上架菜品，前端兜底再过滤一次
        const availableDishes = filteredDishes.filter(dish => dish.isAvailable)
        pagination.value.total = isSearchMode ? Math.max(Number(total) || 0, availableDishes.length) : availableDishes.length
        
        // 为每个菜品获取评分
        const dishesWithRating = await Promise.all(
          availableDishes.map(async (dish) => {
            try {
              const ratingRes = await ratingAPI.getAverage(dish.id)
              if (ratingRes.data.code === 200) {
                return {
                  ...dish,
                  rating: parseFloat(ratingRes.data.data)
                }
              }
              return dish
            } catch (error) {
              console.error(`获取菜品${dish.id}评分失败:`, error)
              return dish
            }
          })
        )
        
        dishList.value = dishesWithRating
      } else {
        dishList.value = []
      }
    }
  } catch (error) {
    console.error('加载菜品失败:', error)
    dishList.value = []
  } finally {
    loading.value = false
  }
}

// 跳转到菜品详情
const goToDetail = (dish) => {
  router.push(`/dish/${dish.id}`)
}

// 处理搜索按钮点击
const handleSearch = () => {
  // 搜索时重置到第一页
  pagination.value.current = 1
  loadDishes()
}

// 处理分类选择变化
// 处理分页变化
const handlePageChange = (page) => {
  pagination.value.current = page
  loadDishes()
}

// 处理每页显示数量变化
const handlePageSizeChange = () => {
  // 每页显示数量变化时，重置到第一页
  pagination.value.current = 1
  loadDishes()
}

const addDishToCart = async (dish) => {
  try {
    const cartItem = {
      userId: userStore.userId,
      dishId: Number(dish.id),
      quantity: 1,
      remark: '',
      dishName: dish.name,
      dishPrice: dish.price,
      dishImage: dish.image
    }
    await cartAPI.addItem(cartItem)
    await loadCart()
    isCartExpanded.value = true
  } catch (error) {
    console.error('添加到购物车失败:', error)
    alert('添加到购物车失败，请重试')
  }
}

// 计算总价
const totalPrice = computed(() => {
  return cartItems.value.reduce((total, item) => {
    return total + (item.dishPrice * item.quantity)
  }, 0)
})

// 处理添加购物车
const handleAddToCart = async (cartItem) => {
  try {
    // 先加载购物车数据
    await loadCart()
    // 显示购物车
    isCartExpanded.value = true
  } catch (error) {
    console.error('处理购物车失败:', error)
  }
}

// 加载购物车数据
const loadCart = async () => {
  try {
    const res = await cartAPI.getCart(userStore.userId)
    if (res.data.code === 200) {
      cartItems.value = res.data.data || []
    }
  } catch (error) {
    console.error('加载购物车失败:', error)
    cartItems.value = []
  }
}

// 切换购物车显示
const toggleCart = () => {
  isCartExpanded.value = !isCartExpanded.value
}

// 更新商品数量
const updateQuantity = async (cartId, newQuantity) => {
  if (newQuantity < 1) return
  
  try {
    await cartAPI.updateQuantity(cartId, newQuantity, '')
    await loadCart()
  } catch (error) {
    console.error('更新数量失败:', error)
  }
}

// 删除商品
const removeItem = async (cartId) => {
  try {
    await cartAPI.removeItem(cartId)
    await loadCart()
  } catch (error) {
    console.error('删除商品失败:', error)
  }
}

// 下单
const checkout = async () => {
  if (cartItems.value.length === 0) {
    alert('购物车为空')
    return
  }
  
  try {
    const cartIds = cartItems.value.map(item => item.cartId)
    const res = await cartAPI.batchCheckout(userStore.userId, cartIds)
    
    if (res.data.code === 200) {
      alert('下单成功！')
      await loadCart()
      isCartExpanded.value = false
    } else {
      alert('下单失败：' + res.data.message)
    }
  } catch (error) {
    console.error('下单失败:', error)
    alert('下单失败，请重试')
  }
}
// 组件挂载时加载数据
onMounted(async () => {
  pagination.value.current = 1
  pagination.value.pageSize = 6
  localStorage.removeItem('dishViewPagination')
  
  await loadCategories()
  await loadDishes()
  await loadCart()
})

// 点餐页刷新后固定回到第一页，不再缓存上次分页状态
onUnmounted(() => {})
</script>

<style scoped>
.dish-view {
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.78));
  border-radius: 24px;
  padding: 32px;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--color-border);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.dish-view:hover {
  box-shadow: var(--shadow-lg);
}

.dish-view::before {
  content: '';
  position: absolute;
  top: -120px;
  right: -120px;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(67, 56, 202, 0.13), transparent 65%);
  pointer-events: none;
}

.view-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.title-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.view-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.2px;
  background: linear-gradient(135deg, var(--color-gradient-start) 0%, var(--color-gradient-end) 58%, var(--color-gradient-accent) 100%);
  background-clip: text;
  color: transparent;
}

.view-subtitle {
  margin: 0;
  color: var(--color-text-sub);
  font-size: 14px;
}

.view-chip {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(67, 56, 202, 0.2);
  background: rgba(67, 56, 202, 0.1);
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.dish-layout {
  margin-bottom: 28px;
}

.dish-content {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.category-sidebar {
  width: 180px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  padding: 8px;
  border-radius: 18px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(12px);
}

.cat-btn {
  display: block;
  width: 100%;
  padding: 10px 16px;
  margin-bottom: 4px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--color-text-main);
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cat-btn:hover {
  background: rgba(67, 56, 202, 0.08);
  color: var(--color-primary-strong);
}

.cat-btn.active {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: #fff;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(59, 47, 127, 0.18);
}

.dish-grid-area {
  flex: 1;
  min-width: 0;
}

.filters {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
  flex-wrap: wrap;
  align-items: center;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(12px);
  position: relative;
  z-index: 1;
}

.search-container {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 200px;
}

.search-input {
  flex: 1;
  padding: 14px 20px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 47, 127, 0.12);
  background: rgba(255, 255, 255, 0.95);
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  color: #fff;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  border: none;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  white-space: nowrap;
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(59, 47, 127, 0.32);
}

.search-btn:active {
  transform: translateY(0);
}

.search-icon {
  font-size: 16px;
}

.search-text {
  font-weight: 500;
}

/* 购物车样式 */
.cart-container {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 1000;
}

.cart-button {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border-radius: 16px;
  cursor: pointer;
  box-shadow: 0 14px 30px rgba(59, 47, 127, 0.32);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  border: none;
}

.cart-button:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-xl);
}

.cart-icon {
  position: relative;
  font-size: 24px;
}

.cart-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #ff4757;
  color: white;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.cart-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.cart-total {
  font-size: 16px;
  font-weight: 600;
}

.cart-text {
  font-size: 12px;
  opacity: 0.9;
}

.cart-content {
  position: absolute;
  bottom: 80px;
  right: 0;
  width: 360px;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  box-shadow: 0 18px 36px rgba(17, 24, 39, 0.16);
  border: 1px solid var(--color-border);
  max-height: 400px;
  overflow: hidden;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.cart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--color-border-light);
}

.cart-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-main);
}

.cart-close {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.cart-close:hover {
  background: rgba(0, 0, 0, 0.05);
}

.cart-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--color-text-sub);
}

.cart-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.cart-items {
  max-height: 200px;
  overflow-y: auto;
  padding: 0 20px;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border-light);
}

.cart-item:last-child {
  border-bottom: none;
}

.item-image {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
}

.item-info {
  flex: 1;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-main);
  margin-bottom: 4px;
}

.item-price {
  font-size: 12px;
  color: var(--color-text-sub);
}

.item-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qty-btn {
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.qty-btn:hover {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.item-qty {
  font-size: 14px;
  font-weight: 500;
  min-width: 20px;
  text-align: center;
}

.delete-btn {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.delete-btn:hover {
  background: rgba(255, 71, 87, 0.1);
  color: #ff4757;
}

.cart-footer {
  padding: 20px;
  border-top: 1px solid var(--color-border-light);
  background: rgba(248, 249, 250, 0.8);
}

.cart-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 14px;
}

.total-items {
  color: var(--color-text-sub);
}

.total-price {
  font-weight: 600;
  color: var(--color-text-main);
}

.checkout-btn {
  width: 100%;
  padding: 12px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.checkout-btn:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .cart-container {
    bottom: 16px;
    right: 16px;
  }
  
  .cart-content {
    width: calc(100vw - 32px);
    right: -16px;
  }
  
  .cart-button {
    padding: 12px 16px;
  }
  
  .cart-total {
    font-size: 14px;
  }
  
  .cart-text {
    font-size: 11px;
  }
}

.category-select {
  padding: 14px 20px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  min-width: 150px;
  cursor: pointer;
}

.category-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 47, 127, 0.12);
  background: rgba(255, 255, 255, 0.95);
}
.dish-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
}

.empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 80px 40px;
  color: var(--color-text-muted);
  background: var(--color-bg-glass);
  border-radius: 24px;
  border: 1px solid var(--color-border);
  backdrop-filter: blur(8px);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.7;
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-sub);
}

/* 分页样式 */
.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 32px;
  padding: 20px 0;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.82);
  border-radius: 16px;
  padding: 12px 24px;
  backdrop-filter: blur(8px);
  border: 1px solid var(--color-border);
}

.pagination-btn {
  padding: 8px 16px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.pagination-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(59, 47, 127, 0.28);
}

.pagination-btn:disabled {
  background: #e5e7eb;
  color: var(--color-text-muted);
  cursor: not-allowed;
  transform: none;
}

.pagination-info {
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
}

.limit-control {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
  font-size: 14px;
  color: var(--color-text-main);
}

.limit-control select {
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  cursor: pointer;
  transition: all 0.3s ease;
}

.limit-control select:hover {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 47, 127, 0.12);
}

/* 加载状态 */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(4px);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--color-border);
  border-top: 4px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.loading-text {
  font-size: 16px;
  color: var(--color-text-sub);
  font-weight: 500;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .dish-view {
    padding: 24px;
  }

  .view-header {
    flex-direction: column;
    gap: 12px;
  }

  .view-title {
    font-size: 24px;
  }

  .dish-content {
    flex-direction: column;
  }

  .category-sidebar {
    width: 100%;
    max-height: none;
    position: static;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 10px;
    overflow-y: visible;
    overflow-x: auto;
  }

  .cat-btn {
    width: auto;
    margin-bottom: 0;
    padding: 6px 14px;
    font-size: 12px;
  }
  
  .filters {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-container {
    width: 100%;
  }
  
  .dish-list {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  
  .empty {
    padding: 60px 30px;
  }
  
  .empty-icon {
    font-size: 48px;
  }
}
.explore-stats{display:grid;grid-template-columns:repeat(3,88px);gap:10px}.explore-stats div{padding:10px;border-radius:14px;background:rgba(255,255,255,.72);border:1px solid var(--color-border);text-align:center}.explore-stats strong{display:block;font-size:19px;color:var(--color-primary-strong)}.explore-stats span{font-size:11px;color:var(--color-text-sub)}.smart-chips{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0 16px}.smart-chips button{border:1px solid rgba(59,47,127,.14);background:rgba(255,255,255,.86);border-radius:999px;padding:7px 12px;color:var(--color-primary-strong);font-weight:700;cursor:pointer;transition:.2s}.smart-chips button:hover{transform:translateY(-2px);box-shadow:var(--shadow-sm)}.filter-insight{display:flex;gap:10px;align-items:center;margin:-4px 0 16px;padding:10px 12px;border-radius:14px;background:rgba(11,122,117,.08);border:1px solid rgba(11,122,117,.12);color:#0b615d}.filter-insight span{font-size:12px;font-weight:900;text-transform:uppercase}.filter-insight strong{font-size:13px}@media(max-width:900px){.explore-stats{grid-template-columns:repeat(3,1fr);width:100%}}.compact-grid{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-bottom:28px;position:relative;z-index:1}.six-grid{grid-auto-rows:1fr}.explore-card{overflow:hidden;border-radius:20px;background:rgba(255,255,255,.9);border:1px solid var(--color-border);box-shadow:var(--shadow-sm);cursor:pointer;transition:.25s cubic-bezier(.2,0,0,1);display:flex;flex-direction:column;min-height:354px}.explore-card:hover{transform:translateY(-4px);box-shadow:var(--shadow-lg);border-color:rgba(59,47,127,.24)}.explore-cover{position:relative;height:132px;overflow:hidden;background:#f1f5f9}.explore-cover img{width:100%;height:100%;object-fit:cover;display:block;transition:.3s}.explore-card:hover .explore-cover img{transform:scale(1.05)}.category-chip,.score-chip{position:absolute;border-radius:999px;padding:4px 8px;font-size:11px;font-weight:900;color:#fff;backdrop-filter:blur(8px)}.category-chip{top:10px;left:10px;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end))}.score-chip{right:10px;bottom:10px;background:rgba(15,23,42,.78)}.explore-body{padding:12px;display:flex;flex-direction:column;gap:8px;height:222px;min-height:222px}.explore-top{display:flex;justify-content:space-between;gap:8px;align-items:flex-start}.explore-top h3{margin:0;font-size:16px;line-height:1.25;color:var(--color-text-main);display:-webkit-box;-webkit-line-clamp:1;-webkit-box-orient:vertical;overflow:hidden}.explore-top strong{color:var(--color-primary-strong);white-space:nowrap}.explore-desc{margin:0;color:var(--color-text-sub);font-size:12px;line-height:1.45;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.explore-reason{padding:8px;border-radius:12px;background:rgba(11,122,117,.08);color:#0b615d;font-size:12px;line-height:1.45;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.explore-meta{display:flex;gap:6px;flex-wrap:wrap;margin-top:auto;min-height:24px;max-height:24px;overflow:hidden}.explore-meta span{padding:3px 8px;border-radius:999px;background:rgba(67,56,202,.08);color:var(--color-primary-strong);font-size:11px;font-weight:800}.explore-meta .sales-pill{background:var(--color-accent-glow);color:#a16207}.add-mini{width:100%;border:none;border-radius:12px;padding:9px 12px;color:#fff;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end));font-weight:800;cursor:pointer;transition:.2s}.add-mini:hover{transform:translateY(-1px);box-shadow:0 10px 20px rgba(59,47,127,.22)}@media(max-width:860px){.compact-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.explore-cover{height:140px}}@media(max-width:560px){.compact-grid{grid-template-columns:1fr}.explore-cover{height:170px}.explore-body{height:auto;min-height:210px}}
.ai-assistant-fab {
  position: fixed;
  bottom: 32px;
  left: 32px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 22px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(59, 47, 127, 0.28);
  transition: all 0.25s cubic-bezier(0.2, 0, 0, 1);
  z-index: 999;
}
.ai-assistant-fab:hover {
  transform: translateY(-3px) scale(1.04);
  box-shadow: 0 12px 32px rgba(59, 47, 127, 0.35);
}
.ai-assistant-fab:active {
  transform: scale(0.96);
}
.fab-icon {
  font-size: 22px;
  line-height: 1;
}
.fab-label {
  white-space: nowrap;
}
@media (max-width: 768px) {
  .ai-assistant-fab {
    bottom: 20px;
    left: 20px;
    padding: 12px 16px;
    font-size: 13px;
  }
  .fab-icon {
    font-size: 18px;
  }
}</style>