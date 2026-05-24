<template>
  <div class="recommend-view card-glass">
    <div class="view-header">
      <div class="title-wrap">
        <h2 class="view-title">智能推荐中心</h2>
        <p class="view-subtitle">融合历史偏好、评分行为与菜品热度，生成带推荐理由的个性化推荐</p>
      </div>
      <div class="view-chip">可解释推荐</div>
      <div class="smart-metrics">
        <div><strong>{{ recommendations.length }}</strong><span>推荐菜品</span></div>
        <div><strong>{{ averageMatchScore }}%</strong><span>平均匹配</span></div>
        <div><strong>{{ uniqueTasteCount }}</strong><span>覆盖口味</span></div>
      </div>
    </div>
    
    <!-- 推荐信息区域 -->
    <div class="recommend-info-section">
      <div class="recommend-info">
        <span class="info-icon">AI</span>
        <span class="info-text">本轮推荐综合考虑：长期偏好、菜品评分、热度与多样性。</span>
      </div>
      <div class="strategy-tags">
        <span>偏好匹配</span>
        <span>忌口规避</span>
        <span>评分加权</span>
      </div>
      <button class="btn-secondary refresh-btn" @click="loadRecommendationsByUserId">
        <span>刷新推荐</span>
      </button>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <div class="loading-text">智能推荐中...</div>
    </div>
    
    <!-- 推荐结果 -->
    <div class="recommend-layout">
      <aside class="recommend-side">
        <div class="side-card">
          <span class="side-kicker">Recommendation Strategy</span>
          <h3>本轮推荐策略</h3>
          <p>综合用户长期偏好、评分行为和菜品热度，优先展示匹配度更高且口味多样的菜品。</p>
          <div class="side-list">
            <div><strong>{{ averageMatchScore }}%</strong><span>平均匹配度</span></div>
            <div><strong>{{ uniqueTasteCount }}</strong><span>覆盖口味标签</span></div>
            <div><strong>{{ pageLimit }}</strong><span>单页推荐上限</span></div>
          </div>
        </div>
      </aside>

      <div class="dish-list compact-grid six-grid">
        <article v-for="dish in recommendations" :key="dish.id" class="recommend-card" @click="goToDetail(dish)">
          <div class="recommend-cover">
            <img :src="resolveImageUrl(dish.image || defaultImage)" :alt="dish.name" />
            <span class="ai-chip">AI 推荐</span>
            <span class="score-chip">{{ dish.matchScore || 88 }}% 匹配</span>
          </div>
          <div class="recommend-body">
            <div class="recommend-top"><h3>{{ dish.name }}</h3><strong>￥{{ Number(dish.price || 0).toFixed(2) }}</strong></div>
            <p class="recommend-desc">{{ dish.description }}</p>
            <div class="recommend-reason">{{ dish.reason }}</div>
            <div class="recommend-meta">
              <span v-for="tag in dish.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
              <span v-if="dish.rating !== undefined && dish.rating !== null" class="rating-pill">★ {{ dish.rating.toFixed(1) }}</span>
            </div>
            <button type="button" class="add-mini" @click.stop="addToCart(dish)">加入购物车</button>
          </div>
        </article>
        <div v-if="recommendations.length === 0 && !loading" class="empty"><div class="empty-icon">🍽️</div><div class="empty-text">暂无推荐</div></div>
      </div>
    </div>
    
    <!-- 分页控制 -->
    <div v-if="recommendations.length > 0 && !loading" class="pagination">
      <button 
        class="pagination-btn" 
        :disabled="currentPage === 1" 
        @click="changePage(currentPage - 1)"
      >
        上一页
      </button>
      <span class="page-info">第 {{ currentPage }} 页</span>
      <button 
        class="pagination-btn" 
        :disabled="!hasMoreData"
        @click="changePage(currentPage + 1)"
      >
        下一页
      </button>
      <div class="limit-control">
        <span>每页显示：</span>
        <select v-model="pageLimit" @change="changeLimit" disabled><option value="6">6</option></select>
      </div>
    </div>
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
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { recommendAPI, cartAPI, orderAPI, ratingAPI, imageAPI, preferenceAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import { resolveImageUrl } from '@/utils/image'

const router = useRouter()
const userStore = useUserStore()
const recommendations = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageLimit = ref(6)
const hasMoreData = ref(true) // 是否有更多数据
const defaultImage = 'https://picsum.photos/200/150?random=default'
const currentPreference = ref({ taste: [], ingredient: [], taboo: [] })
const allTasteTags = computed(() => recommendations.value.flatMap(dish => dish.tags || []))
const uniqueTasteCount = computed(() => new Set(allTasteTags.value).size)
const averageMatchScore = computed(() => {
  if (!recommendations.value.length) return 0
  const avg = recommendations.value.reduce((sum, dish) => sum + (dish.matchScore || 88), 0) / recommendations.value.length
  return Math.round(avg)
})

// 购物车相关
const cartItems = ref([])
const isCartExpanded = ref(false)

const parsePreferenceField = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value.map(i => String(i).trim()).filter(Boolean)
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.map(i => String(i).trim()).filter(Boolean)
  } catch (_) {}
  return String(value).split(/[,，、\s]+/).map(i => i.trim()).filter(Boolean)
}

const includesAny = (text, list) => list.some(item => item && text.includes(item))

const buildDishText = (dish, tags = []) => [
  dish.name,
  dish.dishName,
  dish.category,
  dish.categoryName,
  dish.description,
  dish.taste,
  dish.dishTaste,
  dish.ingredient,
  dish.ingredients,
  tags.join(' ')
].filter(Boolean).join(' ').toLowerCase()

const calculatePreferenceMatch = (dish, tags) => {
  const pref = currentPreference.value
  const dishText = buildDishText(dish, tags)
  const tasteHits = pref.taste.filter(item => includesAny(dishText, [item.toLowerCase()])).length
  const ingredientHits = pref.ingredient.filter(item => includesAny(dishText, [item.toLowerCase()])).length
  const tabooHits = pref.taboo.filter(item => includesAny(dishText, [item.toLowerCase()])).length
  const baseScore = Number(dish.matchScore || dish.score || 70)
  const tasteScore = Math.min(tasteHits * 10, 20)
  const ingredientScore = Math.min(ingredientHits * 8, 16)
  const tagDiversityScore = Math.min(tags.length * 2, 6)
  const ratingScore = Math.min(Number(dish.rating || 0) * 2, 10)
  const penalty = tabooHits * 24
  return Math.max(35, Math.min(99, Math.round(baseScore + tasteScore + ingredientScore + tagDiversityScore + ratingScore - penalty)))
}

const buildScoreReason = (dish, tags) => {
  const pref = currentPreference.value
  const dishText = buildDishText(dish, tags)
  const tasteMatches = pref.taste.filter(item => item && dishText.includes(item.toLowerCase()))
  const ingredientMatches = pref.ingredient.filter(item => item && dishText.includes(item.toLowerCase()))
  const tabooMatches = pref.taboo.filter(item => item && dishText.includes(item.toLowerCase()))
  const parts = []
  if (tasteMatches.length) parts.push(`当前口味偏好命中：${tasteMatches.slice(0, 2).join('、')}`)
  if (ingredientMatches.length) parts.push(`食材偏好命中：${ingredientMatches.slice(0, 2).join('、')}`)
  if (tabooMatches.length) parts.push(`包含忌口项：${tabooMatches.slice(0, 2).join('、')}，已降低匹配度`)
  if (!parts.length) parts.push(tags.length ? `与 ${tags.slice(0, 2).join('、')} 等菜品特征相关` : '根据综合评分与热度推荐')
  return parts.join('；')
}

const buildDishTags = (dish) => {
  if (Array.isArray(dish.tags) && dish.tags.length) return dish.tags
  const rawTaste = dish.taste || dish.dishTaste || dish.categoryName || dish.category || ''
  return rawTaste ? String(rawTaste).replace(/[\[\]"]/g, '').split(/[,、，/]/).map(t => t.trim()).filter(Boolean) : []
}

const buildMatchScore = (dish, tags) => calculatePreferenceMatch(dish, tags)

const buildReason = (dish, tags) => {
  const scoreReason = buildScoreReason(dish, tags)
  const price = Number(dish.price || 0)
  return `${scoreReason}${price ? `，价格约￥${price.toFixed(0)}` : ''}。`
}

const loadCurrentPreference = async () => {
  if (!userStore.userId) return
  try {
    const res = await preferenceAPI.getPreference(userStore.userId)
    if (res.data.code === 200 && res.data.data) {
      currentPreference.value = {
        taste: parsePreferenceField(res.data.data.taste),
        ingredient: parsePreferenceField(res.data.data.ingredient),
        taboo: parsePreferenceField(res.data.data.taboo)
      }
    }
  } catch (error) {
    console.error('加载当前偏好失败:', error)
    currentPreference.value = { taste: [], ingredient: [], taboo: [] }
  }
}

// 根据用户ID加载推荐
const loadRecommendationsByUserId = async () => {
  if (!userStore.userId) {
    // 如果没有用户ID，显示提示
    recommendations.value = []
    hasMoreData.value = false
    return
  }

  loading.value = true
  try {
    const res = await recommendAPI.recommendByUserId(userStore.userId, currentPage.value, pageLimit.value)
    if (res.data.code === 200) {
      // 兼容后端返回结构：Page 或 { recommendation: Page, ...explain }
      const payload = res.data.data
      const pagePayload = payload?.recommendation?.records ? payload.recommendation : payload

      let dishList = []
      if (pagePayload && pagePayload.records) {
        dishList = pagePayload.records
      } else if (Array.isArray(pagePayload)) {
        dishList = pagePayload
      }

      // 转换后端返回的数据格式
      const dishData = dishList.map(dish => {
        const tags = buildDishTags(dish)
        const normalizedDish = {
          id: dish.dishId || dish.id,
          name: dish.dishName || dish.name,
          price: dish.price,
          image: resolveImageUrl(dish.imageUrl || dish.image),
          category: dish.categoryName || dish.category,
          description: dish.description || '这是一道美味的菜品',
          taste: dish.taste || dish.dishTaste || '',
          ingredient: dish.ingredient || dish.ingredients || '',
          tags
        }
        return {
          ...normalizedDish,
          matchScore: Number(dish.matchScore || dish.score || buildMatchScore(normalizedDish, tags)),
          reason: dish.reason || dish.recommendReason || buildReason(normalizedDish, tags)
        }
      })

      // 图片兜底：推荐结果未带图片时，按 dishId 再查一次图片接口
      const dishDataWithImageFallback = await Promise.all(
        dishData.map(async (dish) => {
          if (dish.image) return dish
          try {
            const imageRes = await imageAPI.getDishImageUrl(dish.id)
            const backendUrl = imageRes?.data?.data?.imageUrl || ''
            return {
              ...dish,
              image: resolveImageUrl(backendUrl)
            }
          } catch (_) {
            return dish
          }
        })
      )
      
      // 为每个菜品获取评分
      const dishesWithRating = await Promise.all(
        dishDataWithImageFallback.map(async (dish) => {
          try {
            const ratingRes = await ratingAPI.getAverage(dish.id)
            if (ratingRes.data.code === 200) {
              const ratingData = ratingRes.data.data
              let rating = 4.5
              
              // 确保评分数据是有效的数字
              if (ratingData !== null && ratingData !== undefined) {
                rating = parseFloat(ratingData)
                // 确保评分在0-5之间
                if (isNaN(rating) || rating < 0 || rating > 5) {
                  rating = 4.5
                }
              }
              
              return {
                ...dish,
                rating: rating,
                matchScore: calculatePreferenceMatch({ ...dish, rating }, dish.tags || []),
                reason: buildReason({ ...dish, rating }, dish.tags || []),
                description: dish.description || '精选优质食材，匠心制作，口感丰富，营养均衡，是您的理想选择。'
              }
            }
            return {
              ...dish,
              rating: 4.5,
              matchScore: calculatePreferenceMatch({ ...dish, rating: 4.5 }, dish.tags || []),
              reason: buildReason({ ...dish, rating: 4.5 }, dish.tags || []),
              description: dish.description || '精选优质食材，匠心制作，口感丰富，营养均衡，是您的理想选择。'
            }
          } catch (error) {
            console.error(`获取菜品${dish.id}评分失败:`, error)
            return {
              ...dish,
              rating: 4.5,
              matchScore: calculatePreferenceMatch({ ...dish, rating: 4.5 }, dish.tags || []),
              reason: buildReason({ ...dish, rating: 4.5 }, dish.tags || []),
              description: dish.description || '精选优质食材，匠心制作，口感丰富，营养均衡，是您的理想选择。'
            }
          }
        })
      )
      
      recommendations.value = dishesWithRating
      
      // 检查是否有更多数据
      console.log('后端返回的数据:', res.data.data)
      console.log('当前页码:', currentPage.value)
      console.log('每页显示数量:', pageLimit.value)
      
      if (pagePayload && pagePayload.total) {
        // 后端返回了分页信息，根据总记录数和当前页码判断
        const total = pagePayload.total
        const current = pagePayload.current || currentPage.value
        const size = pagePayload.size || pageLimit.value
        const totalPages = Math.ceil(total / size)
        console.log('总记录数:', total)
        console.log('当前页码:', current)
        console.log('每页显示数量:', size)
        console.log('总页数:', totalPages)

        // 分页越界自动纠正：例如 total=14,size=6 只有3页，但本地记住了第4页
        if (totalPages > 0 && currentPage.value > totalPages) {
          currentPage.value = totalPages
          await loadRecommendationsByUserId()
          return
        }

        hasMoreData.value = current < totalPages
        console.log('是否有更多数据:', hasMoreData.value)
      } else {
        // 后端没有返回分页信息，根据返回的数据数量判断
        console.log('返回的数据数量:', dishDataWithImageFallback.length)
        hasMoreData.value = dishDataWithImageFallback.length === pageLimit.value
        console.log('是否有更多数据:', hasMoreData.value)
      }
    } else {
      console.error('推荐失败:', res.data.message)
      recommendations.value = []
      hasMoreData.value = false
    }
  } catch (error) {
    console.error('推荐失败:', error)
    recommendations.value = []
    hasMoreData.value = false
  } finally {
    loading.value = false
  }
}

// 分页控制
const changePage = (page) => {
  currentPage.value = page
  loadRecommendationsByUserId()
}

// 更改每页显示数量
const changeLimit = () => {
  currentPage.value = 1
  hasMoreData.value = true // 重置为有更多数据
  loadRecommendationsByUserId()
}

// 获取推荐理由
const getRecommendReason = (dish) => {
  return dish.reason || '智能推荐'
}

// 添加到购物车
const addToCart = async (dish) => {
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
    alert('已加入购物车')
  } catch (error) {
    console.error('添加到购物车失败:', error)
    alert('添加到购物车失败，请重试')
  }
}

// 跳转到菜品详情
const goToDetail = (dish) => {
  const query = dish.matchScore ? { matchScore: Math.round(dish.matchScore) } : {}
  router.push({ path: `/dish/${dish.id}`, query })
}

// 计算总价
const totalPrice = computed(() => {
  return cartItems.value.reduce((total, item) => {
    return total + (item.dishPrice * item.quantity)
  }, 0)
})

// 加载购物车数据
const loadCart = async () => {
  if (!userStore.userId) return
  
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
  if (!userStore.userId) {
    alert('请先登录')
    router.push('/login')
    return
  }
  isCartExpanded.value = !isCartExpanded.value
  if (isCartExpanded.value) {
    loadCart()
  }
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

// 结算
const checkout = async () => {
  if (!userStore.userId) {
    alert('请先登录')
    router.push('/login')
    return
  }
  
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
      // 跳转到订单页面
      router.push('/orders')
    } else {
      alert('下单失败：' + res.data.message)
    }
  } catch (error) {
    console.error('结算失败:', error)
    alert('结算失败，请重试')
  }
}

// 处理添加购物车
const handleAddToCart = async (cartItem) => {
  if (!userStore.userId) {
    alert('请先登录')
    router.push('/login')
    return
  }
  
  try {
    // 加载购物车数据
    await loadCart()
    // 显示购物车
    isCartExpanded.value = true
  } catch (error) {
    console.error('添加到购物车失败:', error)
    alert('添加到购物车失败，请重试')
  }
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/login')
}

// 页面加载时自动推荐
onMounted(() => {
  // 推荐页默认从第一页开始
  currentPage.value = 1
  pageLimit.value = 6
  hasMoreData.value = true

  if (userStore.userId) {
    loadCurrentPreference().then(loadRecommendationsByUserId)
    loadCart() // 加载购物车数据
  }
})

watch(() => userStore.userId, (newUserId) => {
  if (!newUserId) {
    recommendations.value = []
    hasMoreData.value = false
    return
  }
  currentPage.value = 1
  hasMoreData.value = true
  loadCurrentPreference().then(loadRecommendationsByUserId)
  loadCart()
})

// 组件卸载时不再保存分页，避免下次进入落在最后一页
onUnmounted(() => {})
</script>

<style scoped>
.recommend-view {
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.78));
  border-radius: 24px;
  padding: 32px;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--color-border);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.recommend-view:hover {
  box-shadow: var(--shadow-lg);
}

.recommend-view::before {
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

/* 推荐信息区域 */
.recommend-info-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(12px);
  border-radius: 16px;
  border: 1px solid var(--color-border);
  position: relative;
  z-index: 1;
}

.recommend-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.info-icon {
  font-size: 20px;
}

.info-text {
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: #fff;
  box-shadow: 0 10px 20px rgba(59, 47, 127, 0.28);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.refresh-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 24px rgba(59, 47, 127, 0.34);
}

/* 加载状态 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 24px;
  z-index: 10;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(59, 47, 127, 0.2);
  border-top: 3px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text {
  font-size: 16px;
  color: var(--color-text-sub);
  font-weight: 500;
}

/* 推荐结果 */
.dish-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
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
  margin: 0;
}

/* 分页控制 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding: 12px 20px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(8px);
}

.pagination-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.pagination-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(59, 47, 127, 0.28);
}

.pagination-btn:disabled {
  background: #e5e7eb;
  color: var(--color-text-muted);
  cursor: not-allowed;
  box-shadow: none;
}

.page-info {
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
  min-width: 100px;
  text-align: center;
}

.limit-control {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--color-text-sub);
}

.limit-control select {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  font-size: 14px;
  cursor: pointer;
}

.limit-control select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(59, 47, 127, 0.12);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .recommend-view {
    padding: 24px;
  }

  .view-header {
    flex-direction: column;
    gap: 12px;
  }

  .view-title {
    font-size: 24px;
  }

  .recommend-info-section {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .refresh-btn {
    width: 100%;
    justify-content: center;
  }
  
  .dish-list {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 16px;
  }
  
  .empty {
    padding: 60px 30px;
  }
  
  .empty-icon {
    font-size: 48px;
  }
  
  .empty-text {
    font-size: 14px;
  }
  
  .pagination {
    flex-direction: column;
    gap: 12px;
  }
  
  .page-info {
    order: -1;
  }
}

@media (max-width: 480px) {
  .dish-list {
    grid-template-columns: 1fr;
  }
  
  .empty {
    padding: 40px 20px;
  }
  
  .empty-icon {
    font-size: 32px;
  }
  
  .empty-text {
    font-size: 14px;
  }
}

.smart-metrics{display:grid;grid-template-columns:repeat(3,88px);gap:10px}.smart-metrics div{padding:10px;border-radius:14px;background:rgba(255,255,255,.72);border:1px solid var(--color-border);text-align:center}.smart-metrics strong{display:block;font-size:20px;color:var(--color-primary-strong)}.smart-metrics span{font-size:11px;color:var(--color-text-sub)}.strategy-tags{display:flex;gap:8px;flex-wrap:wrap}.strategy-tags span{padding:5px 10px;border-radius:999px;background:rgba(11,122,117,.08);color:#0b615d;font-size:12px;font-weight:700}.info-icon{width:30px;height:30px;border-radius:10px;display:grid;place-items:center;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end));color:#fff;font-size:12px;font-weight:900}@media(max-width:900px){.smart-metrics{grid-template-columns:repeat(3,1fr);width:100%}.recommend-info-section{align-items:stretch}.strategy-tags{justify-content:center}}.recommend-layout{display:grid;grid-template-columns:250px minmax(0,1fr);gap:18px;align-items:start;margin-bottom:28px}.recommend-side{position:sticky;top:16px}.side-card{padding:18px;border-radius:20px;background:linear-gradient(145deg,rgba(15,23,42,.92),rgba(59,47,127,.86));color:#fff;box-shadow:var(--shadow-md)}.side-kicker{font-size:11px;font-weight:900;letter-spacing:.08em;text-transform:uppercase;color:rgba(255,255,255,.62)}.side-card h3{margin:6px 0 8px;font-size:20px}.side-card p{margin:0;color:rgba(255,255,255,.72);font-size:13px;line-height:1.6}.side-list{display:grid;gap:8px;margin-top:16px}.side-list div{padding:10px;border-radius:14px;background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.12)}.side-list strong{display:block;font-size:22px}.side-list span{font-size:12px;color:rgba(255,255,255,.7)}.compact-grid{grid-template-columns:repeat(auto-fill,minmax(220px,1fr))!important;gap:16px!important;margin-bottom:0!important}.recommend-card{overflow:hidden;border-radius:20px;background:rgba(255,255,255,.9);border:1px solid var(--color-border);box-shadow:var(--shadow-sm);cursor:pointer;transition:.25s cubic-bezier(.2,0,0,1);display:flex;flex-direction:column}.recommend-card:hover{transform:translateY(-4px);box-shadow:var(--shadow-lg);border-color:rgba(59,47,127,.24)}.recommend-cover{position:relative;height:145px;overflow:hidden;background:#f1f5f9}.recommend-cover img{width:100%;height:100%;object-fit:cover;display:block;transition:.3s}.recommend-card:hover .recommend-cover img{transform:scale(1.05)}.ai-chip,.score-chip{position:absolute;border-radius:999px;padding:4px 8px;font-size:11px;font-weight:900;color:#fff;backdrop-filter:blur(8px)}.ai-chip{top:10px;left:10px;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end))}.score-chip{right:10px;bottom:10px;background:rgba(15,23,42,.78)}.recommend-body{padding:12px;display:flex;flex-direction:column;gap:8px;min-height:205px}.recommend-top{display:flex;justify-content:space-between;gap:8px;align-items:flex-start}.recommend-top h3{margin:0;font-size:16px;line-height:1.25;color:var(--color-text-main)}.recommend-top strong{color:var(--color-primary-strong);white-space:nowrap}.recommend-desc{margin:0;color:var(--color-text-sub);font-size:12px;line-height:1.45;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.recommend-reason{padding:8px;border-radius:12px;background:rgba(11,122,117,.08);color:#0b615d;font-size:12px;line-height:1.45;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.recommend-meta{display:flex;gap:6px;flex-wrap:wrap;margin-top:auto}.recommend-meta span{padding:3px 8px;border-radius:999px;background:rgba(67,56,202,.08);color:var(--color-primary-strong);font-size:11px;font-weight:800}.recommend-meta .rating-pill{background:var(--color-accent-glow);color:#a16207}.add-mini{width:100%;border:none;border-radius:12px;padding:9px 12px;color:#fff;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end));font-weight:800;cursor:pointer;transition:.2s}.add-mini:hover{transform:translateY(-1px);box-shadow:0 10px 20px rgba(59,47,127,.22)}@media(max-width:1024px){.recommend-layout{grid-template-columns:1fr}.recommend-side{position:static}.side-list{grid-template-columns:repeat(3,1fr)}}@media(max-width:640px){.compact-grid{grid-template-columns:1fr!important}.recommend-cover{height:170px}.side-list{grid-template-columns:1fr}.recommend-view{padding:18px!important}.view-header{gap:10px}.smart-metrics{grid-template-columns:repeat(3,1fr)!important}}.six-grid{grid-template-columns:repeat(3,minmax(0,1fr))!important;grid-auto-rows:1fr}.six-grid .recommend-card{height:100%;min-height:354px}.six-grid .recommend-cover{height:132px}.six-grid .recommend-body{height:222px;min-height:222px}.six-grid .recommend-top h3{display:-webkit-box;-webkit-line-clamp:1;-webkit-box-orient:vertical;overflow:hidden}.six-grid .recommend-meta{min-height:24px;max-height:24px;overflow:hidden}.limit-control select:disabled{opacity:.65;cursor:not-allowed}@media(max-width:1180px){.recommend-layout{grid-template-columns:1fr!important}.six-grid{grid-template-columns:repeat(3,minmax(0,1fr))!important}}@media(max-width:860px){.six-grid{grid-template-columns:repeat(2,minmax(0,1fr))!important}.six-grid .recommend-cover{height:140px}}@media(max-width:560px){.six-grid{grid-template-columns:1fr!important}.six-grid .recommend-cover{height:170px}.six-grid .recommend-body{height:auto;min-height:210px}}/* 购物车样式 */
.cart-container {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 1000;
  transition: all 0.3s ease;
}

.cart-button {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 16px;
  cursor: pointer;
  box-shadow: 0 14px 30px rgba(59, 47, 127, 0.32);
  transition: all 0.3s ease;
  min-width: 180px;
  justify-content: center;
}

.cart-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(59, 47, 127, 0.4);
  filter: brightness(1.05);
}

.cart-icon {
  font-size: 20px;
  position: relative;
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
  font-weight: 700;
}

.cart-info {
  text-align: left;
}

.cart-total {
  display: block;
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 2px;
}

.cart-text {
  display: block;
  font-size: 12px;
  opacity: 0.9;
}

/* 购物车展开状态 */
.cart-expanded {
  width: 360px;
}

.cart-content {
  position: absolute;
  bottom: 80px;
  right: 0;
  width: 360px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  border-radius: 18px;
  box-shadow: 0 18px 36px rgba(17, 24, 39, 0.16);
  border: 1px solid var(--color-border);
  padding: 24px;
  max-height: 400px;
  overflow-y: auto;
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
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.cart-header h4 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-main);
}

.cart-close {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-sub);
  padding: 4px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.cart-close:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--color-text-main);
}

.cart-empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--color-text-sub);
}

.cart-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.cart-empty p {
  margin: 0;
  font-size: 14px;
}

.cart-items {
  margin-bottom: 20px;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
}

.item-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-main);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-price {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}

.item-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qty-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.qty-btn:hover {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.item-qty {
  min-width: 32px;
  text-align: center;
  font-size: 14px;
  font-weight: 500;
}

.delete-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #ff4757;
  padding: 4px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.delete-btn:hover {
  background: rgba(255, 71, 87, 0.1);
}

.cart-footer {
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.cart-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.total-items {
  font-size: 14px;
  color: var(--color-text-sub);
}

.total-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-primary);
}

.checkout-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(59, 47, 127, 0.3);
}

.checkout-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(59, 47, 127, 0.4);
  filter: brightness(1.05);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .cart-container {
    bottom: 24px;
    right: 24px;
  }
  
  .cart-button {
    padding: 14px 20px;
    min-width: 160px;
  }
  
  .cart-expanded {
    width: 320px;
  }
  
  .cart-content {
    width: 320px;
    bottom: 70px;
  }
}

@media (max-width: 480px) {
  .cart-expanded {
    width: calc(100vw - 48px);
  }
  
  .cart-content {
    width: calc(100vw - 48px);
  }
}
</style>