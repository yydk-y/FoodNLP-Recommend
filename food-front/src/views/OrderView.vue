<template>
  <div class="order-view card-glass">
    <div class="header">
      <h3 class="gradient-text">我的订单</h3>
      <div class="filters">
        <select v-model="filterStatus" @change="handleFilterChange" class="filter-select">
          <option value="">全部状态</option>
          <option value="1">待支付</option>
          <option value="2">已支付</option>
          <option value="3">已完成</option>
          <option value="4">已取消</option>
        </select>
        <div class="date-range">
          <span>下单时间：</span>
          <input type="date" v-model="startTime" @change="handleDateChange" class="date-input" />
          <span>至</span>
          <input type="date" v-model="endTime" @change="handleDateChange" class="date-input" />
        </div>
      </div>
    </div>
    
    <div v-if="orders.length === 0" class="empty">
      <div class="empty-icon">📦</div>
      <div class="empty-text">暂无订单</div>
      <router-link to="/" class="btn-secondary">去点餐</router-link>
    </div>
    <div v-else>
      <div v-for="order in orders" :key="order.id" class="order-card card-glass">
        <div class="order-header">
          <div class="order-info">
            <span class="order-id">订单号：{{ order.orderNo }}</span>
            <span class="order-time">下单时间：{{ formatTime(order.createTime) }}</span>
            <span v-if="order.payTime" class="order-time">支付时间：{{ formatTime(order.payTime) }}</span>
          </div>
          <span :class="['status-badge', statusClass(order.status)]">{{ statusText(order.status) }}</span>
        </div>
        <div class="order-details">
          <div v-for="item in order.items" :key="item.dishId" class="order-item">
            <div class="item-info">
              <div class="item-name">
                <span>{{ item.dishName }} x {{ item.quantity }}</span>
                <span v-if="item.remark" class="item-remark">({{ item.remark }})</span>
              </div>
              <span class="item-price">￥{{ (item.price * item.quantity).toFixed(2) }}</span>
            </div>
            <div class="item-actions">
              <button
                v-if="(order.status === 2 || order.status === 3) && !isReviewed(order.id, item.dishId)"
                class="review-btn"
                @click="openReviewModal(order.id, item.dishId, item.dishName)"
              >
                写评价
              </button>
              <span v-else-if="isReviewed(order.id, item.dishId)" class="reviewed-tag">已评价</span>
            </div>
          </div>
          <div class="order-total">
            <span>总计：</span>
            <span class="total-price gradient-text">{{ order.totalPrice.toFixed(2) }}</span>
          </div>
        </div>
        <div v-if="order.status === 1" class="order-actions">
          <button @click="openPayModal(order)" class="btn-primary">去支付</button>
          <button @click="cancelOrder(order.id)" class="btn-secondary">取消订单</button>
        </div>
      </div>
    </div>
    
    <!-- 分页组件 -->
    <div v-if="orders.length > 0" class="pagination">
      <div class="pagination-info">
        共 {{ total }} 条记录，第 {{ currentPage }} 页
      </div>
      <div class="pagination-controls">
        <button 
          @click="goToPage(1)" 
          :disabled="currentPage === 1"
          class="page-btn"
        >
          首页
        </button>
        <button 
          @click="goToPage(currentPage - 1)" 
          :disabled="currentPage === 1"
          class="page-btn"
        >
          上一页
        </button>
        
        <span 
          v-for="page in visiblePages" 
          :key="page"
          @click="goToPage(page)"
          :class="['page-number', { active: page === currentPage }]"
        >
          {{ page }}
        </span>
        
        <button 
          @click="goToPage(currentPage + 1)" 
          :disabled="currentPage === totalPages"
          class="page-btn"
        >
          下一页
        </button>
        <button 
          @click="goToPage(totalPages)" 
          :disabled="currentPage === totalPages"
          class="page-btn"
        >
          末页
        </button>
      </div>
      <div class="page-size">
        <span>每页显示：</span>
        <select v-model="pageSize" @change="handlePageSizeChange" class="page-size-select">
          <option value="5">5</option>
          <option value="10">10</option>
          <option value="20">20</option>
        </select>
      </div>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <div class="loading-text">数据加载中...</div>
    </div>
  </div>
  
  <!-- 支付模态框 -->
  <div v-if="showPayModal" class="modal" @click.self="closePayModal">
    <div class="modal-content card-glass pay-modal">
      <div class="modal-header">
        <h4 class="modal-title">收银台</h4>
        <button class="modal-close" @click="closePayModal" :disabled="paying">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
          </svg>
        </button>
      </div>

      <div v-if="currentPayOrder" class="pay-body">
        <div class="pay-row"><span>订单号</span><strong>{{ currentPayOrder.orderNo }}</strong></div>
        <div class="pay-row"><span>支付金额</span><strong class="pay-amount">￥{{ currentPayOrder.totalPrice.toFixed(2) }}</strong></div>
        <div class="pay-row">
          <span>支付方式</span>
          <select v-model="payForm.channel" class="filter-select" :disabled="paying || payStatus === 'success'">
            <option value="ALIPAY">支付宝</option>
            <option value="WECHAT">微信支付</option>
          </select>
        </div>

        <div class="pay-code">收银台码：{{ paySession.payCode || '生成中...' }}</div>
        <div class="pay-timer" v-if="payStatus !== 'success' && payExpireSeconds > 0">请在 {{ payExpireSeconds }} 秒内完成支付</div>
        <div class="pay-timer error" v-if="payStatus === 'expired'">支付会话已超时，请关闭后重新发起支付。</div>
        <div class="pay-timer error" v-if="payStatus === 'failed'">支付处理失败，请重试。</div>
        <div class="pay-timer" v-if="payStatus === 'processing'">支付处理中，请稍候...</div>
        <div class="pay-success" v-if="payStatus === 'success'">
          支付成功，交易单号：{{ payResult?.paymentNo || '生成中' }}
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn-secondary" @click="closePayModal" :disabled="paying">取消</button>
        <button class="btn-primary" :disabled="paying || payStatus === 'expired' || payStatus === 'success'" @click="confirmPay">
          {{ paying ? '支付处理中...' : '我已完成支付' }}
        </button>
      </div>
    </div>
  </div>

  <!-- 评论模态框（移到根元素外部，确保全屏显示） -->
  <div v-if="showReviewModal" class="modal" @click.self="closeReviewModal">
    <div class="modal-content card-glass">
      <!-- 模态框头部 -->
      <div class="modal-header">
        <h4 class="modal-title">评价菜品</h4>
        <button class="modal-close" @click="closeReviewModal">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
          </svg>
        </button>
      </div>
      
      <!-- 菜品信息区域 -->
      <div class="dish-info-section">
        <div v-if="currentDishImage" class="dish-image-wrapper">
          <img :src="resolveImageUrl(currentDishImage)" :alt="currentDishName" class="dish-image" />
        </div>
        <div class="dish-text-info">
          <h5 class="dish-name">{{ currentDishName }}</h5>
          <p class="dish-subtitle">请分享您的用餐体验</p>
        </div>
      </div>
      
      <!-- 评分区域 -->
      <div class="rating-section">
        <div class="rating-label">
          <span class="rating-title">评分</span>
          <span class="rating-value">{{ reviewForm.rating }}.0</span>
        </div>
        <div class="stars-container">
          <div v-for="i in 5" :key="i" class="star-item" :class="{ active: i <= reviewForm.rating }" @click="reviewForm.rating = i">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
            </svg>
          </div>
        </div>
      </div>
      
      <!-- 评价内容区域 -->
      <div class="review-section">
        <textarea 
          v-model="reviewForm.content" 
          placeholder="写下您的评价..." 
          rows="3" 
          class="review-textarea"
        ></textarea>
      </div>
      
      <!-- 操作按钮 -->
      <div class="modal-actions">
        <button class="btn-secondary" @click="closeReviewModal">取消</button>
        <button class="btn-primary" @click="submitReview">提交</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { orderAPI, ratingAPI, dishInfoAPI, imageAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import { resolveImageUrl } from '@/utils/image'

const userStore = useUserStore()
const orders = ref([])
const loading = ref(false)
const reviewedMap = ref({}) // 存储已评论的 { orderId_dishId: true }

// 分页相关变量
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterStatus = ref('')
const startTime = ref('')
const endTime = ref('')

// 评论弹窗相关
const showReviewModal = ref(false)
const currentOrderId = ref(null)
const currentDishId = ref(null)
const currentDishName = ref('')
const currentDishImage = ref('')
const reviewForm = ref({ rating: 5, content: '' })

// 支付相关
const showPayModal = ref(false)
const currentPayOrder = ref(null)
const payForm = ref({ channel: 'ALIPAY' })
const paySession = ref({})
const payExpireSeconds = ref(0)
const paying = ref(false)
const payStatus = ref('ready')
const payResult = ref(null)
let payTimer = null

// 计算总页数
const totalPages = computed(() => {
  return Math.ceil(total.value / pageSize.value)
})

// 计算可见页码
const visiblePages = computed(() => {
  const pages = []
  const maxVisible = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2))
  let end = Math.min(totalPages.value, start + maxVisible - 1)
  
  if (end - start + 1 < maxVisible) {
    start = Math.max(1, end - maxVisible + 1)
  }
  
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
})

// 状态映射
const statusText = (status) => {
  const map = { 
    1: '待支付', 
    2: '已支付', 
    3: '已完成', 
    4: '已取消' 
  }
  return map[status] || '未知状态'
}

// 状态样式类
const statusClass = (status) => {
  const map = { 
    1: 'status-pending', 
    2: 'status-paid', 
    3: 'status-completed', 
    4: 'status-cancelled' 
  }
  return map[status] || 'status-unknown'
}

// 加载订单数据
const loadOrders = async () => {
  loading.value = true
  try {
    // 构建查询参数
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      userId: userStore.userId
    }
    
    // 添加筛选条件
    if (filterStatus.value) {
      params.status = parseInt(filterStatus.value)
    }
    
    if (startTime.value) {
      params.startTime = startTime.value
    }
    
    if (endTime.value) {
      params.endTime = endTime.value
    }
    
    const res = await orderAPI.getOrderPage(params)
    if (res.data.code === 200) {
      const data = res.data.data
      console.log('订单分页数据:', data)
      
      // 转换后端数据格式为前端需要的格式
      orders.value = data.list.map(order => ({
        id: order.orderId,
        orderNo: order.orderNo || order.orderId, // 订单号，如果没有则使用订单ID
        userId: order.userId,
        totalPrice: order.totalPrice,
        status: order.status,
        createTime: order.createTime,
        payTime: order.payTime,
        items: [] // 先设置为空数组，稍后加载明细
      }))
      
      total.value = data.total || 0
      currentPage.value = data.pageNum || 1
      
      // 为每个订单加载明细数据
      await loadOrderDetails()
      // 加载每个订单中菜品的评论状态
      await loadReviewedStatus()
    } else {
      console.error('获取订单数据失败:', res.data.message)
      alert('获取订单数据失败，请重试')
    }
  } catch (error) {
    console.error('加载订单失败:', error)
    orders.value = []
    total.value = 0
    alert('加载订单失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

// 加载订单明细数据
const loadOrderDetails = async () => {
  for (const order of orders.value) {
    try {
      const res = await orderAPI.getOrderDetails(order.id)
      if (res.data.code === 200) {
        order.items = res.data.data.map(detail => ({
          dishId: detail.dishId,
          dishName: detail.dishName,
          price: detail.price,
          quantity: detail.num,
          remark: detail.remark || ''
        }))
      }
    } catch (error) {
      console.error(`加载订单 ${order.id} 明细失败:`, error)
      order.items = []
    }
  }
}

// 筛选条件变化
const handleFilterChange = () => {
  currentPage.value = 1
  loadOrders()
}

// 日期范围变化
const handleDateChange = () => {
  currentPage.value = 1
  loadOrders()
}

// 分页控制函数
const goToPage = (page) => {
  if (page < 1 || page > totalPages.value || page === currentPage.value) {
    return
  }
  currentPage.value = page
  loadOrders()
}

// 处理每页显示数量变化
const handlePageSizeChange = () => {
  currentPage.value = 1
  loadOrders()
}

const clearPayTimer = () => {
  if (payTimer) {
    clearInterval(payTimer)
    payTimer = null
  }
}

const startPayCountdown = (seconds = 300) => {
  clearPayTimer()
  payExpireSeconds.value = seconds
  payTimer = setInterval(() => {
    if (payExpireSeconds.value > 0) {
      payExpireSeconds.value -= 1
    } else {
      payStatus.value = 'expired'
      clearPayTimer()
    }
  }, 1000)
}

const openPayModal = async (order) => {
  currentPayOrder.value = order
  payForm.value.channel = 'ALIPAY'
  paySession.value = {}
  payResult.value = null
  payStatus.value = 'ready'
  showPayModal.value = true

  try {
    const res = await orderAPI.createPaySession(order.id, payForm.value.channel)
    if (res.data.code === 200) {
      paySession.value = res.data.data || {}
      payStatus.value = 'ready'
      startPayCountdown(paySession.value.expireSeconds || 300)
    } else {
      payStatus.value = 'failed'
      alert('创建支付会话失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    payStatus.value = 'failed'
    console.error('创建支付会话失败:', error)
    alert('创建支付会话失败，请稍后重试')
  }
}

const closePayModal = () => {
  showPayModal.value = false
  currentPayOrder.value = null
  paySession.value = {}
  payResult.value = null
  payExpireSeconds.value = 0
  payStatus.value = 'ready'
  paying.value = false
  clearPayTimer()
}

const confirmPay = async () => {
  if (!currentPayOrder.value || !paySession.value.sessionId) {
    alert('支付会话不存在，请重新发起支付')
    return
  }
  if (payExpireSeconds.value <= 0 || payStatus.value === 'expired') {
    payStatus.value = 'expired'
    alert('支付会话已超时，请重新发起支付')
    return
  }

  paying.value = true
  payStatus.value = 'processing'

  try {
    await new Promise((resolve) => setTimeout(resolve, 1000 + Math.floor(Math.random() * 700)))

    const res = await orderAPI.confirmPay(currentPayOrder.value.id, paySession.value.sessionId)
    if (res.data.code === 200) {
      payStatus.value = 'success'
      payResult.value = res.data.data || null
      await loadOrders()
      setTimeout(() => {
        closePayModal()
        alert('支付成功，交易单号：' + (res.data.data?.paymentNo || '生成中'))
      }, 600)
    } else {
      payStatus.value = 'failed'
      alert('支付失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    payStatus.value = 'failed'
    console.error('支付确认失败:', error)
    alert('支付确认失败，请重试')
  } finally {
    paying.value = false
  }
}

// 取消订单
const cancelOrder = async (orderId) => {
  // 确认取消
  if (!confirm('确定要取消此订单吗？')) {
    return
  }
  
  try {
    const res = await orderAPI.cancelOrder(orderId)
    if (res.data.code === 200) {
      await loadOrders()
      alert('订单已取消')
    } else {
      alert('取消失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('取消订单失败:', error)
    alert('取消订单失败，请重试')
  }
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
      minute: '2-digit',
      second: '2-digit'
    })
  } catch (error) {
    console.error('时间格式化失败:', error)
    return timeStr
  }
}

// 评论相关功能
const loadReviewedStatus = async () => {
  const map = {}
  for (const order of orders.value) {
    // 检查所有状态的订单中的菜品评论状态
    // 因为即使订单状态变为已完成，用户仍然可能需要查看评论状态
    for (const item of order.items) {
      try {
        const res = await ratingAPI.checkReviewed(userStore.userId, item.dishId, order.id)
        if (res.data.code === 200 && res.data.data) {
          map[`${order.id}_${item.dishId}`] = true
        }
      } catch (error) {
        console.error(`检查评论状态失败 orderId:${order.id} dishId:${item.dishId}:`, error)
      }
    }
  }
  reviewedMap.value = map
}

const isReviewed = (orderId, dishId) => {
  return !!reviewedMap.value[`${orderId}_${dishId}`]
}

const openReviewModal = async (orderId, dishId, dishName) => {
  // 检查订单状态，只有已支付或已完成的订单才能评论
  const order = orders.value.find(o => o.id === orderId)
  if (!order || (order.status !== 2 && order.status !== 3)) {
    alert('只有已支付或已完成的订单才能评价')
    return
  }
  
  currentOrderId.value = orderId
  currentDishId.value = dishId
  currentDishName.value = dishName
  reviewForm.value = { rating: 5, content: '' }
  
  // 获取菜品图片（优先专用图片接口，失败再降级到菜品接口）
  try {
    const imageRes = await imageAPI.getDishImageUrl(dishId)
    const imageUrl = imageRes?.data?.data?.imageUrl || ''
    if (imageUrl) {
      currentDishImage.value = resolveImageUrl(imageUrl)
    } else {
      const res = await dishInfoAPI.getById(dishId)
      if (res.data.code === 200 && res.data.data) {
        currentDishImage.value = resolveImageUrl(res.data.data.imageUrl || res.data.data.image || '')
      }
    }
  } catch (error) {
    try {
      const res = await dishInfoAPI.getById(dishId)
      if (res.data.code === 200 && res.data.data) {
        currentDishImage.value = resolveImageUrl(res.data.data.imageUrl || res.data.data.image || '')
      }
    } catch (_) {
      currentDishImage.value = ''
    }
  }
  
  showReviewModal.value = true
}

const closeReviewModal = () => {
  showReviewModal.value = false
  currentOrderId.value = null
  currentDishId.value = null
  currentDishName.value = ''
  currentDishImage.value = ''
}

const submitReview = async () => {
  if (!reviewForm.value.content.trim()) {
    alert('请填写评价内容')
    return
  }
  try {
    const res = await ratingAPI.add(
      userStore.userId,
      currentDishId.value,
      currentOrderId.value,
      reviewForm.value.rating,
      reviewForm.value.content
    )
    if (res.data.code === 200) {
      // 更新本地已评论状态
      reviewedMap.value[`${currentOrderId.value}_${currentDishId.value}`] = true
      alert('评价成功，感谢您的反馈！')
      closeReviewModal()
    } else {
      alert('评价失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('提交评价失败:', error)
    alert('评价失败，请重试')
  }
}

onMounted(() => {
  loadOrders()
})

onUnmounted(() => {
  clearPayTimer()
})
</script>

<style scoped>
.order-view {
  margin: 24px 8px;
  border-radius: 24px;
  padding: 32px;
  box-shadow: var(--shadow-glass);
  transition: all 0.3s ease;
  position: relative;
}

.order-view:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
}

.header h3 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.2px;
  background: linear-gradient(135deg, var(--color-gradient-start) 0%, var(--color-gradient-end) 58%, var(--color-gradient-accent) 100%);
  background-clip: text;
  color: transparent;
}

.gradient-text {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end), var(--color-gradient-accent));
  background-clip: text;
  color: transparent;
}

.filters {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-select {
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  transition: all 0.2s ease;
  cursor: pointer;
}

.filter-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.12);
  background: rgba(255, 255, 255, 0.95);
}

.date-range {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.date-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  transition: all 0.2s ease;
}

.date-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.12);
  background: rgba(255, 255, 255, 0.95);
}

.empty {
  text-align: center;
  padding: 80px 40px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.7);
  border-radius: 24px;
  border: 1px solid var(--color-border);
  backdrop-filter: blur(8px);
  margin: 20px 0;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.7;
}

.empty-text {
  font-size: 18px;
  font-weight: 500;
  color: var(--color-text-sub);
  margin-bottom: 16px;
}

.btn-secondary {
  padding: 12px 24px;
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-primary);
  border-radius: 40px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
  display: inline-block;
  margin-top: 8px;
}

.btn-secondary:hover {
  background: var(--color-primary);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(59, 47, 127, 0.3);
}

.order-card {
  margin-bottom: 24px;
  border-radius: 18px;
  overflow: hidden;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);
}

.order-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-4px);
}

.order-header {
  background: rgba(255, 255, 255, 0.8);
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 1px solid var(--color-border);
  backdrop-filter: blur(12px);
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.order-id {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-main);
}

.order-time {
  font-size: 14px;
  color: var(--color-text-sub);
  background: rgba(0, 0, 0, 0.05);
  padding: 4px 12px;
  border-radius: 12px;
  display: inline-block;
  align-self: flex-start;
}

.status-badge {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  min-width: 80px;
  transition: all 0.2s ease;
}

.status-pending {
  background: rgba(240, 173, 78, 0.1);
  color: #f0ad4e;
  border: 1px solid rgba(240, 173, 78, 0.3);
}

.status-paid {
  background: rgba(91, 192, 222, 0.1);
  color: #5bc0de;
  border: 1px solid rgba(91, 192, 222, 0.3);
}

.status-completed {
  background: rgba(92, 184, 92, 0.1);
  color: #5cb85c;
  border: 1px solid rgba(92, 184, 92, 0.3);
}

.status-cancelled {
  background: rgba(217, 83, 79, 0.1);
  color: #d9534f;
  border: 1px solid rgba(217, 83, 79, 0.3);
}

.status-unknown {
  background: rgba(153, 153, 153, 0.1);
  color: #999;
  border: 1px solid rgba(153, 153, 153, 0.3);
}

.order-details {
  padding: 24px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
}

.order-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
  transition: all 0.2s ease;
}

.order-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.item-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex: 1;
  gap: 16px;
}

.item-name {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.item-remark {
  font-size: 14px;
  color: var(--color-text-muted);
  font-style: italic;
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 8px;
  border-radius: 8px;
}

.item-price {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-accent);
}

.item-actions {
  margin-left: 16px;
}

.review-btn {
  background: rgba(59, 47, 127, 0.1);
  border: 1px solid rgba(59, 47, 127, 0.3);
  color: var(--color-primary);
  padding: 6px 16px;
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.review-btn:hover {
  background: var(--color-primary);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 47, 127, 0.3);
}

.reviewed-tag {
  font-size: 14px;
  color: var(--color-text-muted);
  background: rgba(0, 0, 0, 0.05);
  padding: 6px 16px;
  border-radius: 12px;
}

.order-total {
  margin-top: 24px;
  text-align: right;
  font-weight: 600;
  border-top: 1px dashed var(--color-border);
  padding-top: 20px;
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 8px;
}

.order-total span {
  font-size: 16px;
  color: var(--color-text-main);
}

.total-price {
  font-size: 20px;
  font-weight: 700;
}

.order-actions {
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.8);
  text-align: right;
  border-top: 1px solid var(--color-border);
  backdrop-filter: blur(12px);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-primary {
  padding: 12px 24px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 40px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(59, 47, 127, 0.3);
  filter: brightness(1.05);
}

/* 分页样式 */
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 32px;
  padding: 20px 0;
  border-top: 1px solid var(--color-border);
  flex-wrap: wrap;
  gap: 16px;
}

.pagination-info {
  color: var(--color-text-sub);
  font-size: 14px;
  font-weight: 500;
}

.pagination-controls {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
}

.page-btn:disabled {
  background: rgba(248, 249, 250, 0.9);
  color: var(--color-text-muted);
  cursor: not-allowed;
  border-color: var(--color-border);
}

.page-btn:hover:not(:disabled) {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 47, 127, 0.3);
}

.page-number {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
}

.page-number:hover {
  background: rgba(59, 47, 127, 0.1);
  border-color: rgba(59, 47, 127, 0.3);
  transform: translateY(-1px);
}

.page-number.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
  box-shadow: 0 4px 12px rgba(59, 47, 127, 0.3);
}

.page-size {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.page-size-select {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  transition: all 0.2s ease;
  cursor: pointer;
}

.page-size-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 47, 127, 0.12);
  background: rgba(255, 255, 255, 0.95);
}

/* 模态框样式 */
.modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  padding: 24px;
  border-radius: 16px;
  width: 420px;
  max-width: 90%;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-glass);
  animation: modalFadeIn 0.3s ease;
}

@keyframes modalFadeIn {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.modal-title {
  margin-bottom: 24px;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-main);
}

/* 模态框头部样式 */
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.modal-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-main);
  letter-spacing: -0.5px;
}

.modal-close {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.2s ease;
  opacity: 0.7;
}

.modal-close:hover {
  background: rgba(0, 0, 0, 0.05);
  opacity: 1;
}

/* 菜品信息区域样式 */
.dish-info-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding: 0;
}

.dish-image-wrapper {
  flex-shrink: 0;
}

.dish-image {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--color-border-light);
}

.dish-text-info {
  flex: 1;
}

.dish-name {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-main);
}

.dish-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-sub);
  opacity: 0.8;
}

/* 评分区域样式 */
.rating-section {
  margin-bottom: 24px;
  padding: 0;
}

.rating-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.rating-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-main);
  opacity: 0.9;
}

.rating-value {
  font-size: 14px;
  color: var(--color-primary);
  font-weight: 600;
}

.stars-container {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.star-item {
  color: #e0e0e0;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
}

.star-item:hover {
  color: #ffc107;
  transform: scale(1.1);
}

.star-item.active {
  color: #ffc107;
}

/* 评价内容区域样式 */
.review-section {
  margin-bottom: 24px;
}

.review-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  resize: vertical;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  transition: all 0.2s ease;
  min-height: 80px;
  font-family: inherit;
  line-height: 1.5;
}

.review-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  background: rgba(255, 255, 255, 0.95);
}

.review-textarea::placeholder {
  color: var(--color-text-muted);
  opacity: 0.6;
}

/* 操作按钮样式 */
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}



.pay-modal {
  width: min(520px, 92vw);
}

.pay-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.pay-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  color: var(--color-text-main);
}

.pay-amount {
  color: var(--color-primary-strong);
  font-size: 20px;
}

.pay-code {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px dashed var(--color-border);
  color: var(--color-text-sub);
  background: rgba(255, 255, 255, 0.6);
  font-family: monospace;
}

.pay-timer {
  color: #ef4444;
  font-size: 13px;
}

/* 加载状态 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 100;
  backdrop-filter: blur(8px);
  border-radius: 24px;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 4px solid rgba(59, 47, 127, 0.2);
  border-top: 4px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  box-shadow: 0 0 20px rgba(59, 47, 127, 0.3);
}

.loading-text {
  margin-top: 16px;
  color: var(--color-text-sub);
  font-size: 16px;
  font-weight: 500;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .order-view {
    padding: 24px;
    margin: 16px;
  }
  
  .header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
  
  .filters {
    flex-direction: column;
    align-items: stretch;
  }
  
  .date-range {
    justify-content: space-between;
  }
  
  .order-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  
  .status-badge {
    align-self: flex-start;
  }
  
  .order-item {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  
  .item-info {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  
  .item-actions {
    margin-left: 0;
    align-self: flex-start;
  }
  
  .order-actions {
    flex-direction: column;
  }
  
  .btn-primary,
  .btn-secondary {
    width: 100%;
    text-align: center;
  }
  
  .pagination {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  
  .pagination-controls {
    justify-content: center;
  }
  
  .page-size {
    justify-content: center;
  }
  
  .modal-content {
    padding: 24px;
  }
  
  .rating {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>