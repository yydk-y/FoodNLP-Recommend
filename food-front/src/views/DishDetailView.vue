<template>
  <div class="dish-detail-container">
    <div class="dish-detail card-glass" v-if="dish">
      <div class="back-nav">
        <button class="back-btn" @click="goBack">返回菜品列表</button>
      </div>
      <div class="dish-detail__header">
      <div class="image-container">
        <img :src="dish.image" :alt="dish.name" class="dish-detail__image" />
      </div>
      <div class="dish-detail__info">
        <h1 class="dish-name">{{ dish.name }}</h1>
        <div class="price">￥{{ dish.price }}</div>
        <div class="rating">
          <span class="stars">
            <span v-for="i in 5" :key="i" class="star" :class="{ active: i <= Math.floor(dish.rating) }">★</span>
            <span class="rating-value">{{ dish.rating.toFixed(1) }}</span>
          </span>
          <span class="review-count">{{ dish.reviewCount }}条评价</span>
        </div>
        <div class="tags">
          <span v-for="tag in dish.tags" :key="tag" class="tag">{{ tag }}</span>
        </div>
        <p class="desc">{{ dish.description }}</p>
        <div class="extra-info card-glass">
          <div><strong>口味：</strong>{{ dish.flavors.join('、') || '综合口味' }}</div>
          <div><strong>食材：</strong>{{ dish.ingredients.join('、') || '暂无食材信息' }}</div>
        </div>
        <div class="ai-analysis card-glass">
          <div class="analysis-head"><span>AI 适配分析</span><strong>{{ detailMatchScore ? detailMatchScore + '%' : '--' }} 匹配</strong></div>
          <div class="analysis-grid">
            <div><label>口味特征</label><b>{{ dish.flavors.join(' / ') || '综合' }}</b></div>
            <div><label>价格区间</label><b>{{ priceLevel }}</b></div>
            <div><label>适合场景</label><b>{{ sceneSuggestion }}</b></div>
          </div>
          <p>{{ aiReason }}</p>
        </div>
        <div v-if="tabooWarning" class="taboo-warning">{{ tabooWarning }}</div>
        <button class="btn-primary" @click="addToCart">加入购物车</button>
      </div>
    </div>

    <div class="dish-detail__reviews">
      <h3 class="section-title">用户评价</h3>
      
      <!-- 评价统计信息 -->
      <div v-if="dish.ratingStats" class="rating-stats card-glass">
        <div class="stats-overview">
          <div class="overview-item">
            <span class="overview-value gradient-text">{{ dish.rating.toFixed(1) }}</span>
            <span class="overview-label">综合评分</span>
          </div>
          <div class="overview-item">
            <span class="overview-value gradient-text">{{ dish.reviewCount }}</span>
            <span class="overview-label">评价总数</span>
          </div>
          <div class="overview-item">
            <span class="overview-value gradient-text">{{ calculatePositiveRate() }}%</span>
            <span class="overview-label">好评率</span>
          </div>
        </div>
        
        <!-- 星级分布 -->
        <div class="star-distribution">
          <div v-for="i in 5" :key="i" class="star-row">
            <span class="star-label">{{ 6 - i }}星</span>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: getStarPercentage(6 - i) + '%' }"></div>
            </div>
            <span class="star-count">{{ getStarCount(6 - i) }}</span>
          </div>
        </div>
      </div>
      
      <!-- 评价列表 -->
      <div v-if="!dish.reviews?.length" class="empty">
        <div class="empty-icon">💬</div>
        <div class="empty-text">暂无评价</div>
      </div>
      <div v-else class="review-list">
        <div v-for="(review, idx) in dish.reviews" :key="idx" class="review-item card-glass">
          <div class="review-header">
            <span class="review-user">{{ review.user }}</span>
            <span class="review-stars">
              <span v-for="i in 5" :key="i" class="star small" :class="{ active: i <= review.rating }">★</span>
            </span>
            <span class="review-date">{{ review.date }}</span>
          </div>
          <div class="review-content">{{ review.content }}</div>
        </div>
      </div>
    </div>
    </div>
  
  <div v-else class="loading">
    <div class="loading-icon">🍽️</div>
    <div class="loading-text">加载中...</div>
  </div>
</div>

<!-- 底部购物车（移到最外层，始终可见） -->
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
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dishInfoAPI, cartAPI, ratingAPI, orderAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import { resolveImageUrl } from '@/utils/image'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dish = ref(null)

const detailMatchScore = computed(() => {
  if (!dish.value) return 0
  const queryScore = Number(route.query.matchScore)
  if (queryScore && queryScore > 0) return queryScore
  // 不是从推荐入口进入，不显示匹配度
  return null
})

const priceLevel = computed(() => {
  const price = Number(dish.value?.price || 0)
  if (price <= 20) return '高性价比'
  if (price <= 40) return '日常适中'
  return '品质精选'
})

const sceneSuggestion = computed(() => {
  const flavors = dish.value?.flavors || []
  if (flavors.some(item => item.includes('辣'))) return '午晚餐 / 聚餐'
  if (flavors.some(item => item.includes('清淡') || item.includes('汤'))) return '轻食 / 暖胃'
  return '日常点餐'
})

const aiReason = computed(() => {
  if (!dish.value) return ''
  const flavorText = dish.value.flavors?.length ? `具有 ${dish.value.flavors.join('、')} 等口味特征` : '口味较为综合'
  return `该菜品${flavorText}，${priceLevel.value}，评分表现为 ${dish.value.rating.toFixed(1)}，适合作为${sceneSuggestion.value}选择。`
})

const tabooWarning = computed(() => {
  const ingredients = dish.value?.ingredients || []
  const risky = ['海鲜', '虾', '蟹', '花生', '香菜'].filter(item => ingredients.some(i => i.includes(item)))
  return risky.length ? `忌口提醒：该菜品可能包含 ${risky.join('、')}，请结合个人忌口选择。` : ''
})

// 购物车相关
const cartItems = ref([])
const isCartExpanded = ref(false)

const loadDish = async () => {
  const id = route.params.id
  try {
    const res = await dishInfoAPI.getDishWithCategoryById(id)
    if (res.data.code === 200) {
      const dishWithCategory = res.data.data
      
      // 加载菜品评价统计信息
      let ratingStats = { averageScore: 0, totalRatings: 0 }
      try {
        const statsRes = await ratingAPI.getStatistics(id)
        if (statsRes.data.code === 200) {
          ratingStats = statsRes.data.data
        }
      } catch (error) {
        console.error('加载评价统计失败:', error)
      }
      
      // 加载菜品评价列表
      let reviews = []
      try {
        const reviewsRes = await ratingAPI.getByDish(id)
        if (reviewsRes.data.code === 200) {
          reviews = reviewsRes.data.data.map(review => ({
            user: `用户${review.userId}`,
            rating: review.score,
            date: formatTime(review.ratingTime),
            content: review.comment
          }))
        }
      } catch (error) {
        console.error('加载评价列表失败:', error)
      }
      
      // 转换后端数据格式为前端需要的格式
      dish.value = {
        id: dishWithCategory.dishInfo.dishId || dishWithCategory.dishInfo.id,
        name: dishWithCategory.dishInfo.dishName || dishWithCategory.dishInfo.name,
        price: dishWithCategory.dishInfo.price,
        image: resolveImageUrl(dishWithCategory.dishInfo.imageUrl || dishWithCategory.dishInfo.image),
        description: dishWithCategory.dishInfo.description || '',
        rating: ratingStats.averageScore || 0,
        reviewCount: ratingStats.totalRatings || 0,
        tags: [dishWithCategory.dishCategory.categoryName || '未分类'],
        flavors: dishWithCategory.dishInfo.taste ? dishWithCategory.dishInfo.taste.split(',').map(s => s.trim()).filter(s => s) : [],
        ingredients: dishWithCategory.dishInfo.ingredient ? dishWithCategory.dishInfo.ingredient.split(',').map(s => s.trim()).filter(s => s) : [],
        reviews: reviews,
        ratingStats: ratingStats // 保存完整的统计信息
      }
    }
  } catch (error) {
    console.error('加载菜品失败', error)
    alert('菜品不存在')
    router.push('/dishes')
  }
}

// 计算总价
const totalPrice = computed(() => {
  return cartItems.value.reduce((total, item) => {
    return total + (item.dishPrice * item.quantity)
  }, 0)
})

const addToCart = async () => {
  try {
    const cartItem = {
      userId: userStore.userId,
      dishId: parseInt(dish.value.id),
      quantity: 1,
      remark: '',
      dishName: dish.value.name,
      dishPrice: dish.value.price,
      dishImage: dish.value.image
    }
    await cartAPI.addItem(cartItem)
    // 加载购物车数据并显示购物车
    await loadCart()
    isCartExpanded.value = true
  } catch (error) {
    console.error('添加到购物车失败:', error)
    alert('添加到购物车失败，请重试')
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
// 添加 goBack 方法
const goBack = () => {
  router.back() // 返回上一页
}

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  try {
    const date = new Date(timeStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch (error) {
    console.error('时间格式化失败:', error)
    return timeStr
  }
}

// 获取星级百分比
const getStarPercentage = (star) => {
  if (!dish.value?.ratingStats || dish.value.reviewCount === 0) return 0
  
  const countMap = {
    5: dish.value.ratingStats.fiveStarCount || 0,
    4: dish.value.ratingStats.fourStarCount || 0,
    3: dish.value.ratingStats.threeStarCount || 0,
    2: dish.value.ratingStats.twoStarCount || 0,
    1: dish.value.ratingStats.oneStarCount || 0
  }
  
  return ((countMap[star] || 0) / dish.value.reviewCount) * 100
}

// 获取星级数量
const getStarCount = (star) => {
  if (!dish.value?.ratingStats) return 0
  
  const countMap = {
    5: dish.value.ratingStats.fiveStarCount || 0,
    4: dish.value.ratingStats.fourStarCount || 0,
    3: dish.value.ratingStats.threeStarCount || 0,
    2: dish.value.ratingStats.twoStarCount || 0,
    1: dish.value.ratingStats.oneStarCount || 0
  }
  
  return countMap[star] || 0
}

// 计算好评率（4星和5星评价占比）
const calculatePositiveRate = () => {
  if (!dish.value || !dish.value.ratingStats || !dish.value.reviewCount || dish.value.reviewCount === 0) return 0
  
  // 如果后端提供了好评率，直接使用
  if (dish.value.ratingStats.positiveRate !== undefined && dish.value.ratingStats.positiveRate !== null) {
    // 检查positiveRate是否已经是百分比格式（0-100）
    if (dish.value.ratingStats.positiveRate <= 1) {
      // 如果是小数格式（0-1），转换为百分比
      return (dish.value.ratingStats.positiveRate * 100).toFixed(1)
    } else {
      // 如果已经是百分比格式，直接使用
      return Math.min(dish.value.ratingStats.positiveRate, 100).toFixed(1)
    }
  }
  
  // 否则根据星级分布计算（4星和5星为好评）
  const fiveStarCount = dish.value.ratingStats.fiveStarCount || 0
  const fourStarCount = dish.value.ratingStats.fourStarCount || 0
  const positiveCount = fiveStarCount + fourStarCount
  
  // 确保分母不为零
  if (dish.value.reviewCount === 0) return 0
  
  const rate = (positiveCount / dish.value.reviewCount) * 100
  
  // 确保好评率不超过100%
  return Math.min(rate, 100).toFixed(1)
}

onMounted(async () => {
  await loadDish()
  await loadCart()
})
</script>

<style scoped>
.dish-detail {
  max-width: 1000px;
  margin: 24px auto 40px;
  border-radius: 20px;
  padding: 32px;
  box-shadow: var(--shadow-glass);
  transition: all 0.3s ease;
}

.dish-detail:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.back-nav {
  margin-bottom: 24px;
}

.back-btn {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--color-border);
  color: var(--color-primary);
  cursor: pointer;
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 10px;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.back-btn:hover {
  background: var(--color-primary);
  color: white;
  transform: translateX(-4px);
  box-shadow: 0 8px 18px rgba(67, 56, 202, 0.28);
}

.dish-detail__header {
  display: flex;
  gap: 40px;
  flex-wrap: wrap;
  margin-bottom: 48px;
}

.image-container {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
  transition: all 0.3s ease;
}

.image-container:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-lg);
}

.dish-detail__image {
  width: 400px;
  height: 400px;
  object-fit: cover;
}

.price {
  font-size: 32px;
  font-weight: 700;
  margin: 16px 0 20px;
  background: linear-gradient(135deg, var(--color-accent), #ff6b35);
  background-clip: text;
  color: transparent;
  text-shadow: 0 2px 4px rgba(249, 115, 22, 0.2);
  transition: all 0.3s ease;
}

.dish-detail:hover .price {
  transform: translateY(-2px);
  text-shadow: 0 4px 8px rgba(249, 115, 22, 0.3);
}

.dish-detail__info {
  flex: 1;
  min-width: 300px;
}

.dish-name {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 16px;
  color: var(--color-text-main);
  line-height: 1.2;
}

.rating {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.stars {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.star {
  color: #ddd;
  font-size: 24px;
  transition: color 0.2s ease;
}

.star.active {
  color: #ffc107;
  text-shadow: 0 0 10px rgba(255, 193, 7, 0.5);
}

.rating-value {
  margin-left: 8px;
  font-weight: 700;
  font-size: 18px;
  color: var(--color-text-main);
}

.review-count {
  color: var(--color-text-sub);
  font-size: 14px;
  background: rgba(0, 0, 0, 0.05);
  padding: 4px 12px;
  border-radius: 12px;
}

.tags {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.tag {
  background: rgba(67, 56, 202, 0.1);
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  color: var(--color-primary);
  font-weight: 500;
  transition: all 0.2s ease;
  border: 1px solid rgba(67, 56, 202, 0.2);
}

.tag:hover {
  background: var(--color-primary);
  color: white;
  transform: scale(1.05);
  box-shadow: 0 8px 18px rgba(67, 56, 202, 0.28);
}

.desc {
  font-size: 16px;
  color: var(--color-text-sub);
  margin-bottom: 24px;
  line-height: 1.6;
  background: rgba(255, 255, 255, 0.7);
  padding: 16px;
  border-radius: 16px;
  backdrop-filter: blur(8px);
  border: 1px solid var(--color-border);
}

.extra-info {
  padding: 20px;
  margin-bottom: 24px;
  font-size: 14px;
  border-radius: 16px;
  transition: all 0.3s ease;
}

.extra-info:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.extra-info div {
  margin-bottom: 8px;
  color: var(--color-text-main);
  line-height: 1.4;
}

.extra-info div:last-child {
  margin-bottom: 0;
}

.btn-primary {
  padding: 16px 32px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 40px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);
  display: inline-block;
  margin-top: 8px;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(67, 56, 202, 0.28);
  filter: brightness(1.05);
}

/* 评价区域 */
.dish-detail__reviews {
  margin-top: 48px;
}

.section-title {
  margin-bottom: 24px;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-main);
  position: relative;
  padding-left: 24px;
}

.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 24px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  border-radius: 2px;
}

/* 评价统计样式 */
.rating-stats {
  padding: 24px;
  margin-bottom: 32px;
  border-radius: 20px;
  transition: all 0.3s ease;
}

.rating-stats:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stats-overview {
  display: flex;
  justify-content: space-around;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}

.overview-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.overview-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.overview-label {
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
}

.star-distribution {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.star-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.star-label {
  width: 48px;
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
}

.progress-bar {
  flex: 1;
  height: 10px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 5px;
  overflow: hidden;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  transition: width 0.5s ease;
  border-radius: 5px;
  box-shadow: 0 0 10px rgba(59, 47, 127, 0.3);
}

.star-count {
  width: 56px;
  font-size: 14px;
  color: var(--color-text-sub);
  text-align: right;
  font-weight: 500;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.review-item {
  padding: 20px;
  border-radius: 16px;
  transition: all 0.3s ease;
}

.review-item:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.review-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.review-user {
  font-weight: 600;
  color: var(--color-text-main);
  font-size: 16px;
}

.review-stars .star {
  font-size: 16px;
}

.review-date {
  font-size: 14px;
  color: var(--color-text-muted);
  background: rgba(0, 0, 0, 0.05);
  padding: 4px 12px;
  border-radius: 12px;
}

.review-content {
  font-size: 16px;
  color: var(--color-text-sub);
  line-height: 1.6;
  padding: 16px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 12px;
  backdrop-filter: blur(8px);
  border: 1px solid var(--color-border);
}

.loading {
  text-align: center;
  padding: 80px 40px;
  color: var(--color-text-muted);
  background: var(--color-bg-glass);
  border-radius: 24px;
  border: 1px solid var(--color-border);
  backdrop-filter: blur(8px);
  max-width: 600px;
  margin: 40px auto;
}

.loading-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.7;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text {
  font-size: 18px;
  font-weight: 500;
  color: var(--color-text-sub);
}

.empty {
  text-align: center;
  padding: 60px 40px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.7);
  border-radius: 24px;
  border: 1px solid var(--color-border);
  backdrop-filter: blur(8px);
  margin: 20px 0;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.7;
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-sub);
}

.gradient-text {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  background-clip: text;
  color: transparent;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .dish-detail {
    padding: 24px;
    margin: 16px;
  }
  
  .dish-detail__header {
    flex-direction: column;
    gap: 24px;
  }
  
  .image-container {
    width: 100%;
  }
  
  .dish-detail__image {
    width: 100%;
    height: 300px;
  }
  
  .dish-name {
    font-size: 24px;
  }
  
  .price-overlay {
    font-size: 20px;
  }
  
  .stats-overview {
    flex-direction: column;
    gap: 16px;
  }
  
  .overview-item {
    flex-direction: row;
    justify-content: space-between;
    width: 100%;
  }
  
  .review-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .review-date {
    align-self: flex-end;
  }
}

/* 容器样式 */
.dish-detail-container {
  position: relative;
  min-height: 100vh;
}

/* 购物车样式 */
.cart-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
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
  box-shadow: 0 14px 30px rgba(67, 56, 202, 0.32);
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
.ai-analysis{margin:14px 0;padding:14px;border-radius:18px;background:linear-gradient(145deg,rgba(255,255,255,.86),rgba(243,240,255,.62));border:1px solid rgba(59,47,127,.12)}.analysis-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}.analysis-head span{color:var(--color-gradient-accent);font-size:12px;font-weight:900;letter-spacing:.08em;text-transform:uppercase}.analysis-head strong{padding:5px 10px;border-radius:999px;background:rgba(15,23,42,.86);color:#fff;font-size:12px}.analysis-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:10px}.analysis-grid div{padding:9px;border-radius:12px;background:rgba(255,255,255,.78);border:1px solid var(--color-border)}.analysis-grid label{display:block;font-size:11px;color:var(--color-text-muted)}.analysis-grid b{font-size:13px;color:var(--color-text-main)}.ai-analysis p{margin:0;color:var(--color-text-sub);font-size:13px}.taboo-warning{margin:10px 0 14px;padding:10px 12px;border-radius:14px;background:rgba(239,68,68,.1);border:1px solid rgba(239,68,68,.16);color:#b91c1c;font-size:13px;font-weight:700}@media(max-width:768px){.analysis-grid{grid-template-columns:1fr}}.dish-detail__header{display:grid!important;grid-template-columns:minmax(280px,360px) minmax(0,1fr)!important;align-items:start!important;gap:28px!important}.dish-detail .image-container{width:100%;height:auto;align-self:start;position:sticky;top:18px;background:rgba(255,255,255,.72);padding:10px;border-radius:24px;box-shadow:var(--shadow-md)}.dish-detail__image{width:100%!important;height:clamp(260px,32vw,360px)!important;border-radius:18px;display:block;object-fit:cover}.dish-detail__info{min-width:0!important}.extra-info{margin-bottom:12px!important}.ai-analysis{margin:12px 0!important}.desc{margin-bottom:14px!important}.tags{margin-bottom:14px!important}.rating{margin-bottom:14px!important}.price{margin:10px 0 14px!important}@media(max-width:900px){.dish-detail__header{grid-template-columns:1fr!important}.dish-detail .image-container{position:relative;top:auto}.dish-detail__image{height:260px!important}}</style>