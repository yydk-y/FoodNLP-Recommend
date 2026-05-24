<template>
  <div class="order-manage card">
    <div class="header">
      <h3 class="page-title">订单管理</h3>
      <div class="filters">
        <select v-model="filterStatus" @change="handleFilterChange" class="form-input">
          <option value="">全部状态</option>
          <option value="1">待支付</option>
          <option value="2">已支付</option>
          <option value="3">已完成</option>
          <option value="4">已取消</option>
        </select>
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索订单号或用户手机号"
          @input="handleSearch"
          class="form-input"
        />
        <button @click="exportOrders" class="btn-primary">导出订单</button>
      </div>
    </div>
    
    <div class="stats">
      <div class="stat-item card">
        <span class="stat-value gradient-text">{{ orderStats.total }}</span>
        <span class="stat-label">总订单数</span>
      </div>
      <div class="stat-item card">
        <span class="stat-value status-pending">{{ orderStats.pending }}</span>
        <span class="stat-label">待支付</span>
      </div>
      <div class="stat-item card">
        <span class="stat-value status-paid">{{ orderStats.paid }}</span>
        <span class="stat-label">已支付</span>
      </div>
      <div class="stat-item card">
        <span class="stat-value status-completed">{{ orderStats.completed }}</span>
        <span class="stat-label">已完成</span>
      </div>
      <div class="stat-item card">
        <span class="stat-value status-cancelled">{{ orderStats.cancelled }}</span>
        <span class="stat-label">已取消</span>
      </div>
    </div>
    
    <div class="table-container">
      <table class="order-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>用户手机号</th>
            <th>总价</th>
            <th>状态</th>
            <th>下单时间</th>
            <th>支付时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in orders" :key="order.id" class="order-row">
            <td class="order-id">{{ order.orderNo || order.id }}</td>
            <td class="user-phone">{{ order.userPhone }}</td>
            <td class="total-price gradient-text">￥{{ order.totalPrice.toFixed(2) }}</td>
            <td class="status">
              <span class="status-badge" :class="statusClass(order.status)">{{ statusText(order.status) }}</span>
            </td>
            <td class="create-time">{{ formatTime(order.createTime) }}</td>
            <td class="pay-time">{{ formatTime(order.payTime) }}</td>
            <td class="actions">
              <select v-model="order.status" @change="updateStatus(order)" class="status-select form-input">
                <option value="1">待支付</option>
                <option value="2">已支付</option>
                <option value="3">已完成</option>
                <option value="4">已取消</option>
              </select>
              <button @click="viewDetails(order)" class="action-btn detail-btn">详情</button>
              <button @click="deleteOrder(order)" class="action-btn delete-btn" v-if="order.status === 4">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="orders.length === 0" class="empty">
        <div class="empty-icon">📦</div>
        <div class="empty-text">暂无订单数据</div>
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
          <option value="10">10</option>
          <option value="20">20</option>
          <option value="50">50</option>
        </select>
      </div>
    </div>
    
    <!-- 订单详情模态框 -->
    <div v-if="showDetailModal" class="modal" @click.self="closeDetailModal">
      <div class="modal-content card-glass">
        <div class="modal-header">
          <h4>订单详情 - {{ currentOrder.orderNo || currentOrder.id }}</h4>
          <button @click="closeDetailModal" class="close-btn">×</button>
        </div>
        <div class="modal-body">
          <div class="order-info">
            <div class="info-row">
              <span class="label">用户手机号：</span>
              <span>{{ currentOrder.userPhone }}</span>
            </div>
            <div class="info-row">
              <span class="label">总金额：</span>
              <span class="gradient-text">￥{{ currentOrder.totalPrice.toFixed(2) }}</span>
            </div>
            <div class="info-row">
              <span class="label">订单状态：</span>
              <span class="status-badge" :class="statusClass(currentOrder.status)">{{ statusText(currentOrder.status) }}</span>
            </div>
            <div class="info-row">
              <span class="label">下单时间：</span>
              <span>{{ formatTime(currentOrder.createTime) }}</span>
            </div>
            <div class="info-row">
              <span class="label">支付时间：</span>
              <span>{{ formatTime(currentOrder.payTime) }}</span>
            </div>
          </div>
          
          <div class="order-items">
            <h5>订单明细</h5>
            <div v-if="currentOrder.items && currentOrder.items.length > 0">
              <div v-for="item in currentOrder.items" :key="item.dishId" class="item-row card">
                <span class="item-name">{{ item.dishName }}</span>
                <span class="item-quantity">x{{ item.quantity }}</span>
                <span v-if="item.remark" class="item-remark">({{ item.remark }})</span>
                <span class="item-price gradient-text">￥{{ (item.price * item.quantity).toFixed(2) }}</span>
              </div>
            </div>
            <div v-else class="empty-items">暂无明细数据</div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <div class="loading-text">数据加载中...</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { orderAPI, userAPI } from '@/api'

const orders = ref([])
const loading = ref(false)
const filterStatus = ref('')
const searchKeyword = ref('')
const showDetailModal = ref(false)
const currentOrder = ref({})

// 分页相关变量
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 订单统计
const orderStats = ref({
  total: 0,
  pending: 0,
  paid: 0,
  completed: 0,
  cancelled: 0
})

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
      pageSize: pageSize.value
    }
    
    // 添加筛选条件
    if (filterStatus.value) {
      params.status = parseInt(filterStatus.value)
    }
    
    if (searchKeyword.value.trim()) {
      const keyword = searchKeyword.value.trim()
      // 作为订单号搜索
      params.orderNo = keyword
      // 作为手机号搜索
      params.userPhone = keyword
    }
    
    const res = await orderAPI.getOrderPage(params)
    if (res.data.code === 200) {
      const data = res.data.data
      console.log('订单管理分页数据:', data)
      
      // 转换后端数据格式为前端需要的格式
      const orderList = data.list.map(order => ({
        id: order.orderId,
        orderNo: order.orderNo || order.order_no,
        userId: order.userId,
        userPhone: order.userId, // 先使用userId作为默认值
        totalPrice: order.totalPrice,
        status: order.status,
        createTime: order.createTime,
        payTime: order.payTime,
        items: [] // 先设置为空数组，详情时再加载
      }))
      
      // 根据用户ID获取手机号
      for (const order of orderList) {
        try {
          const userRes = await userAPI.getUserInfo(order.userId)
          if (userRes.data.code === 200 && userRes.data.data) {
            order.userPhone = userRes.data.data.phone || order.userId
          }
        } catch (error) {
          console.error(`获取用户 ${order.userId} 信息失败:`, error)
        }
      }
      
      orders.value = orderList
      total.value = data.total || 0
      currentPage.value = data.pageNum || 1
      
      // 计算订单统计
      calculateOrderStats()
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

// 计算订单统计
const calculateOrderStats = () => {
  orderStats.value = {
    total: orders.value.length,
    pending: orders.value.filter(order => order.status === 1).length,
    paid: orders.value.filter(order => order.status === 2).length,
    completed: orders.value.filter(order => order.status === 3).length,
    cancelled: orders.value.filter(order => order.status === 4).length
  }
}

// 筛选条件变化
const handleFilterChange = () => {
  currentPage.value = 1
  loadOrders()
}

// 搜索订单
const handleSearch = () => {
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

// 更新订单状态
const updateStatus = async (order) => {
  try {
    const res = await orderAPI.updateStatus(order.id, order.status)
    if (res.data.code === 200) {
      alert('订单状态更新成功')
      loadOrders() // 重新加载数据
    } else {
      alert('更新失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('更新订单状态失败:', error)
    alert('更新失败，请重试')
  }
}

// 查看订单详情
const viewDetails = async (order) => {
  try {
    const res = await orderAPI.getOrderDetails(order.id)
    if (res.data.code === 200) {
      currentOrder.value = {
        ...order,
        userPhone: order.userPhone || order.phone || order.userId,
        items: res.data.data.map(detail => ({
          dishId: detail.dishId,
          dishName: detail.dishName,
          price: detail.price,
          quantity: detail.num,
          remark: detail.remark || ''
        }))
      }
      showDetailModal.value = true
    }
  } catch (error) {
    console.error('获取订单详情失败:', error)
    alert('获取订单详情失败，请重试')
  }
}

// 关闭详情模态框
const closeDetailModal = () => {
  showDetailModal.value = false
  currentOrder.value = {}
}

// 删除订单
const deleteOrder = async (order) => {
  if (!confirm(`确定要删除订单 ${order.id} 吗？此操作不可撤销。`)) {
    return
  }
  
  try {
    const res = await orderAPI.deleteOrder(order.id)
    if (res.data.code === 200) {
      alert('订单删除成功')
      loadOrders()
    } else {
      alert('删除失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('删除订单失败:', error)
    alert('删除失败，请重试')
  }
}

// 导出订单
const exportOrders = async () => {
  try {
    // 构建查询参数
    const params = {}
    
    // 添加筛选条件
    if (filterStatus.value) {
      params.status = parseInt(filterStatus.value)
    }
    
    if (searchKeyword.value.trim()) {
      const keyword = searchKeyword.value.trim()
      // 作为订单号搜索
      params.orderNo = keyword
      // 作为手机号搜索
      params.userPhone = keyword
    }
    
    // 这里应该调用后端API获取所有符合条件的订单数据
    // 暂时使用当前页面的数据进行演示
    const exportData = orders.value
    
    // 生成CSV内容
    const csvContent = generateCSV(exportData)
    
    // 创建下载链接
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.setAttribute('href', url)
    link.setAttribute('download', `订单数据_${new Date().toISOString().slice(0, 10)}.csv`)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    alert('订单导出成功')
  } catch (error) {
    console.error('导出订单失败:', error)
    alert('导出订单失败，请重试')
  }
}

// 生成CSV内容
const generateCSV = (data) => {
  // CSV表头
  const headers = ['订单号', '用户手机号', '总金额', '状态', '下单时间', '支付时间']
  
  // 转换数据行
  const rows = data.map(order => [
    order.orderNo || order.id,
    order.userPhone,
    order.totalPrice.toFixed(2),
    statusText(order.status),
    formatTime(order.createTime),
    formatTime(order.payTime)
  ])
  
  // 组合表头和数据
  const csvRows = [
    headers.join(','),
    ...rows.map(row => row.join(','))
  ]
  
  return csvRows.join('\n')
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

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.order-manage {
  position: relative;
  overflow: hidden;
}

.order-manage::before {
  content: '';
  position: absolute;
  top: -120px;
  right: -120px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(67, 56, 202, 0.12), transparent 65%);
  pointer-events: none;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.2px;
  background: linear-gradient(135deg, var(--color-gradient-start) 0%, var(--color-gradient-end) 58%, var(--color-gradient-accent) 100%);
  background-clip: text;
  color: transparent;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
  position: relative;
  z-index: 1;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(12px);
}

.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.form-input {
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  background: white;
  transition: all 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.12);
}

.filters input {
  width: 240px;
}

/* 统计卡片 */
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-item {
  padding: 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.76));
}

.stat-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  font-family: 'Segoe UI', system-ui, sans-serif;
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
}

/* 表格容器 */
.table-container {
  overflow-x: auto;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(8px);
}

.order-table {
  width: 100%;
  border-collapse: collapse;
  background: white;
}

.order-table th {
  background: rgba(244, 245, 251, 0.85);
  padding: 16px 12px;
  text-align: left;
  font-weight: 600;
  color: var(--color-text-sub);
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.order-table td {
  padding: 16px 12px;
  border-bottom: 1px solid var(--color-border);
  transition: background-color 0.2s ease;
}

.order-row:hover {
  background: rgba(67, 56, 202, 0.04);
}

/* 状态标签 */
.status-badge {
  display: inline-block;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  text-align: center;
  min-width: 80px;
  border: 1px solid transparent;
}

.status-pending {
  background: rgba(245, 158, 11, 0.1);
  color: var(--color-warning);
  border-color: rgba(245, 158, 11, 0.2);
}

.status-paid {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  border-color: rgba(59, 130, 246, 0.2);
}

.status-completed {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success);
  border-color: rgba(16, 185, 129, 0.2);
}

.status-cancelled {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  border-color: rgba(239, 68, 68, 0.2);
}

.status-unknown {
  background: var(--color-bg-glass);
  color: var(--color-text-muted);
  border-color: var(--color-border);
}

/* 操作按钮 */
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.status-select {
  font-size: 14px;
  min-width: 120px;
}

.action-btn {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.detail-btn {
  color: var(--color-primary);
  border-color: var(--color-primary-light);
}

.detail-btn:hover {
  background: var(--color-primary-light);
}

.delete-btn {
  color: var(--color-danger);
  border-color: rgba(239, 68, 68, 0.2);
}

.delete-btn:hover {
  background: rgba(239, 68, 68, 0.1);
}

/* 空状态 */
.empty {
  text-align: center;
  padding: 80px 40px;
  color: var(--color-text-muted);
  background: var(--color-bg-glass);
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
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--color-border);
  border-radius: 16px;
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
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.page-btn:disabled {
  background: var(--color-bg-glass);
  color: var(--color-text-muted);
  cursor: not-allowed;
  border-color: var(--color-border);
}

.page-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border-color: transparent;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.page-number {
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.page-number:hover {
  background: var(--color-bg-glass);
  transform: translateY(-1px);
}

.page-number.active {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border-color: transparent;
  box-shadow: var(--shadow-sm);
}

.page-size {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-size-select {
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: white;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.page-size-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.12);
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
  padding: 32px;
  border-radius: 24px;
  width: 600px;
  max-width: 90%;
  max-height: 80%;
  overflow-y: auto;
  box-shadow: var(--shadow-glass);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h4 {
  margin: 0;
  color: var(--color-text-main);
  font-size: 18px;
  font-weight: 600;
}

.close-btn {
  background: var(--color-bg-glass);
  border: 1px solid var(--color-border);
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text-sub);
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary-light);
  transform: rotate(90deg);
}

.modal-body {
  padding: 0;
}

.order-info {
  margin-bottom: 24px;
}

.info-row {
  display: flex;
  margin-bottom: 12px;
  align-items: center;
}

.info-row .label {
  width: 100px;
  color: var(--color-text-sub);
  font-weight: 600;
  font-size: 14px;
}

.info-row span {
  font-size: 14px;
  color: var(--color-text-main);
  font-weight: 500;
}

.order-items h5 {
  margin: 0 0 16px 0;
  color: var(--color-text-main);
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-items h5::before {
  content: '';
  width: 3px;
  height: 16px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  border-radius: 2px;
}

.item-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  margin-bottom: 12px;
  border-radius: 12px;
}

.item-name {
  flex: 1;
  font-weight: 600;
  color: var(--color-text-main);
  font-size: 14px;
}

.item-quantity {
  margin: 0 12px;
  color: var(--color-text-sub);
  font-size: 14px;
  font-weight: 500;
}

.item-remark {
  font-style: italic;
  color: var(--color-text-muted);
  margin-right: 12px;
  font-size: 13px;
}

.item-price {
  font-weight: 700;
  font-size: 14px;
  font-family: 'Segoe UI', system-ui, sans-serif;
}

.empty-items {
  text-align: center;
  color: var(--color-text-muted);
  padding: 40px;
  background: var(--color-bg-glass);
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

/* 加载状态 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 100;
  border-radius: 24px;
  box-shadow: var(--shadow-glass);
}

.loading-spinner {
  width: 56px;
  height: 56px;
  border: 3px solid var(--color-primary-light);
  border-top: 3px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

.loading-text {
  margin-top: 12px;
  color: var(--color-text-sub);
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .filters {
    flex-wrap: wrap;
  }
  
  .filters input {
    width: 100%;
  }
  
  .stats {
    grid-template-columns: 1fr;
  }
  
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }
  
  .pagination-controls {
    justify-content: center;
  }
  
  .actions {
    flex-direction: column;
    align-items: stretch;
  }
  
  .action-btn {
    width: 100%;
    text-align: center;
  }
  
  .modal-content {
    padding: 24px;
  }
  
  .info-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  
  .info-row .label {
    width: 100%;
  }
}
</style>