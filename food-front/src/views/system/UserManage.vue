<template>
  <div class="user-manage card">
    <div class="header">
      <h3 class="page-title">用户管理</h3>
      <div class="search-bar">
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索昵称或手机号"
          @input="handleSearch"
          class="form-input"
        />
        <button @click="resetSearch" class="btn-secondary">重置</button>
      </div>
    </div>
    
    <div class="table-container">
      <table class="user-table">
        <thead>
          <tr>
            <th>用户ID</th>
            <th>昵称</th>
            <th>手机号</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.userId" class="user-row">
            <td class="user-id">{{ user.userId }}</td>
            <td class="nickname">{{ user.nickname || '--' }}</td>
            <td class="phone">{{ user.phone || '--' }}</td>
            <td class="email">{{ user.email || '--' }}</td>
            <td class="role">
              <span :class="['status-badge', roleClass(user.isAdmin)]">{{ roleText(user.isAdmin) }}</span>
            </td>
            <td class="status">
              <span :class="['status-badge', statusClass(user.status)]">{{ statusText(user.status) }}</span>
            </td>
            <td class="create-time">{{ formatTime(user.createTime) }}</td>
            <td class="actions">
              <button @click="viewDetails(user)" class="action-btn">详情</button>
              <button @click="editUser(user)" class="action-btn">编辑</button>
              <button 
                @click="toggleStatus(user)" 
                :class="['action-btn', statusBtnClass(user.status)]"
              >
                {{ user.status === 1 ? '禁用' : '启用' }}
              </button>
              <button @click="deleteUser(user)" class="action-btn">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      
      <div v-if="users.length === 0" class="empty">
        <div class="empty-icon">👥</div>
        <div class="empty-text">暂无用户数据</div>
      </div>
    </div>
    
    <!-- 分页组件 -->
    <div class="pagination">
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
        <select v-model="pageSize" @change="handlePageSizeChange" class="form-input">
          <option value="10">10</option>
          <option value="20">20</option>
          <option value="50">50</option>
        </select>
      </div>
    </div>
    
    <!-- 用户详情模态框 -->
    <div v-if="showDetailModal" class="modal" @click.self="closeDetailModal">
      <div class="modal-content card-glass">
        <div class="modal-header">
          <h4>用户详情 - {{ currentUser.nickname }}</h4>
          <button @click="closeDetailModal" class="close-btn">×</button>
        </div>
        <div class="modal-body">
          <div class="user-details">
            <div class="detail-row">
              <span class="label">用户ID：</span>
              <span>{{ currentUser.userId }}</span>
            </div>
            <div class="detail-row">
              <span class="label">昵称：</span>
              <span>{{ currentUser.nickname || '--' }}</span>
            </div>
            <div class="detail-row">
              <span class="label">手机号：</span>
              <span>{{ currentUser.phone || '--' }}</span>
            </div>
            <div class="detail-row">
              <span class="label">邮箱：</span>
              <span>{{ currentUser.email || '--' }}</span>
            </div>
            <div class="detail-row">
              <span class="label">角色：</span>
              <span :class="['status-badge', roleClass(currentUser.isAdmin)]">{{ roleText(currentUser.isAdmin) }}</span>
            </div>
            <div class="detail-row">
              <span class="label">状态：</span>
              <span :class="['status-badge', statusClass(currentUser.status)]">{{ statusText(currentUser.status) }}</span>
            </div>
            <div class="detail-row">
              <span class="label">注册时间：</span>
              <span>{{ formatTime(currentUser.createTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑用户模态框 -->
    <div v-if="showEditModal" class="modal" @click.self="closeEditModal">
      <div class="modal-content card-glass">
        <div class="modal-header">
          <h4>编辑用户 - {{ currentUser.nickname }}</h4>
          <button @click="closeEditModal" class="close-btn">×</button>
        </div>
        <div class="modal-body">
          <form @submit.prevent="saveUser" class="edit-form">
            <div class="form-group">
              <label class="form-label">用户ID</label>
              <input
                type="text"
                :value="currentUser.userId"
                class="form-input"
                disabled
              />
            </div>

            <div class="form-group">
              <label class="form-label">昵称</label>
              <input
                type="text"
                v-model="currentUser.nickname"
                class="form-input"
                placeholder="请输入昵称"
              />
            </div>

            <div class="form-group">
              <label class="form-label">手机号</label>
              <input
                type="text"
                v-model="currentUser.phone"
                class="form-input"
                placeholder="请输入手机号"
              />
            </div>

            <div class="form-group">
              <label class="form-label">邮箱</label>
              <input
                type="email"
                v-model="currentUser.email"
                class="form-input"
                placeholder="请输入邮箱"
              />
            </div>

            <div class="form-group">
              <label class="form-label">角色</label>
              <select v-model="currentUser.isAdmin" class="form-input">
                <option :value="false">普通用户</option>
                <option :value="true">管理员</option>
              </select>
            </div>

            <div class="form-actions">
              <button type="button" @click="closeEditModal" class="btn-secondary">取消</button>
              <button type="submit" class="btn-primary">保存</button>
            </div>
          </form>
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
import { userAPI } from '@/api'

const users = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showDetailModal = ref(false)
const showEditModal = ref(false)
const currentUser = ref({})

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

// 加载用户数据
const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }

    const res = await userAPI.getUserPage(params)
    if (res.data.code === 200) {
      const data = res.data.data
      console.log('后端返回的用户数据:', data) // 调试日志
      // 确保isAdmin字段正确处理
      users.value = (data.list || []).map(user => ({
        ...user,
        isAdmin: user.isAdmin || user.is_admin // 兼容两种字段名
      }))
      total.value = data.total || 0
      currentPage.value = data.pageNum || 1
    } else {
      console.error('获取用户列表失败:', res.data.message)
      alert('获取用户列表失败，请重试')
      users.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('加载用户数据失败:', error)
    alert('加载用户数据失败，请检查网络连接')
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
  loadUsers()
}

// 重置搜索
const resetSearch = () => {
  searchKeyword.value = ''
  currentPage.value = 1
  loadUsers()
}

// 跳转到指定页码
const goToPage = (page) => {
  if (page < 1 || page > totalPages.value || page === currentPage.value) {
    return
  }
  currentPage.value = page
  loadUsers()
}

// 处理每页显示数量变化
const handlePageSizeChange = () => {
  currentPage.value = 1
  loadUsers()
}

// 角色文本映射
const roleText = (isAdmin) => {
  const isAdminBool = Boolean(isAdmin)
  const map = {
    false: '普通用户',
    true: '管理员'
  }
  return map[isAdminBool] || '未知角色'
}

// 角色样式类
const roleClass = (isAdmin) => {
  const isAdminBool = Boolean(isAdmin)
  const map = {
    false: 'role-user',
    true: 'role-admin'
  }
  return map[isAdminBool] || 'role-unknown'
}

// 状态文本映射
const statusText = (status) => {
  const map = {
    0: '禁用',
    1: '正常'
  }
  return map[status] || '未知状态'
}

// 状态样式类
const statusClass = (status) => {
  const map = {
    0: 'status-disabled',
    1: 'status-active'
  }
  return map[status] || 'status-unknown'
}

// 状态按钮样式类
const statusBtnClass = (status) => {
  return status === 1 ? 'disable-btn' : 'enable-btn'
}

// 查看用户详情
const viewDetails = (user) => {
  currentUser.value = user
  showDetailModal.value = true
}

// 编辑用户
const editUser = (user) => {
  currentUser.value = { ...user }
  showEditModal.value = true
}

// 切换用户状态
const toggleStatus = async (user) => {
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'

  if (!confirm(`确定要${action}用户 "${user.nickname}" 吗？`)) {
    return
  }

  try {
    const res = await userAPI.updateStatus(user.userId, newStatus)
    if (res.data.code === 200) {
      alert(`${action}用户成功`)
      await loadUsers()
    } else {
      alert(`${action}用户失败：${res.data.message || '未知错误'}`)
    }
  } catch (error) {
    console.error(`${action}用户失败:`, error)
    alert(`${action}用户失败，请重试`)
  }
}

// 删除用户
const deleteUser = async (user) => {
  if (!confirm(`确定要删除用户 "${user.nickname}" 吗？此操作不可撤销。`)) {
    return
  }

  try {
    const res = await userAPI.deleteUser(user.userId)
    if (res.data.code === 200) {
      alert('删除用户成功')
      await loadUsers()
    } else {
      alert('删除用户失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('删除用户失败:', error)
    alert('删除用户失败，请重试')
  }
}

// 关闭详情模态框
const closeDetailModal = () => {
  showDetailModal.value = false
  currentUser.value = {}
}

// 关闭编辑模态框
const closeEditModal = () => {
  showEditModal.value = false
  currentUser.value = {}
}

// 保存用户信息
const saveUser = async () => {
  try {
    console.log('开始保存用户信息:', currentUser.value)
    
    // 构建更新数据，使用与后端实体类匹配的字段名
    const updateData = {
      userId: currentUser.value.userId,
      nickname: currentUser.value.nickname || '',
      phone: currentUser.value.phone || '',
      email: currentUser.value.email || '',
      isAdmin: currentUser.value.isAdmin,
      status: currentUser.value.status || 1
    }

    console.log('发送的更新数据:', updateData)
    
    const res = await userAPI.updateUser(updateData)
    console.log('更新用户信息返回结果:', res)
    
    if (res.data && res.data.code === 200) {
      alert('用户信息更新成功')
      closeEditModal()
      await loadUsers() // 重新加载用户列表
    } else {
      alert('更新失败：' + (res.data?.message || '未知错误'))
    }
  } catch (error) {
    console.error('保存用户信息失败:', error)
    alert('保存失败，请重试')
  }
}

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return '--'
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

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-manage {
  min-height: 500px;
  position: relative;
  overflow: hidden;
}

.user-manage::before {
  content: '';
  position: absolute;
  top: -120px;
  right: -120px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(67, 56, 202, 0.12), transparent 65%);
  pointer-events: none;
}

/* 头部样式 */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  flex-wrap: wrap;
  gap: 20px;
  position: relative;
  z-index: 1;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(12px);
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

/* 表格容器 */
.table-container {
  margin-bottom: 32px;
  overflow-x: auto;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(8px);
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease;
}

.table-container:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  min-width: 800px;
}

.user-table th {
  background: rgba(244, 245, 251, 0.85);
  padding: 20px 16px;
  font-weight: 600;
  color: var(--color-text-sub);
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
  letter-spacing: 0.3px;
  text-transform: uppercase;
  font-weight: 500;
}

.user-table td {
  padding: 20px 16px;
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
  color: var(--color-text-main);
}

.user-row:hover {
  background: rgba(67, 56, 202, 0.04);
}

/* 表格列样式 */
.user-id {
  font-weight: 600;
  color: var(--color-text-main);
  font-family: 'Inter', 'Poppins', 'PingFang SC', system-ui, -apple-system, sans-serif;
}

.nickname {
  font-weight: 500;
  color: var(--color-text-main);
}

.phone,
.email {
  font-family: 'Inter', 'Poppins', 'PingFang SC', system-ui, -apple-system, sans-serif;
  color: var(--color-text-sub);
}

/* 角色样式 */
.role-user {
  background: var(--color-bg-page);
  color: var(--color-text-sub);
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.role-admin {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.role-unknown {
  background: var(--color-bg-page);
  color: var(--color-text-muted);
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

/* 状态样式 */
.status-active {
  background: rgba(76, 175, 80, 0.1);
  color: #4caf50;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.status-disabled {
  background: var(--color-bg-page);
  color: var(--color-text-muted);
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.status-unknown {
  background: var(--color-bg-page);
  color: var(--color-text-muted);
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.create-time {
  font-family: 'Inter', 'Poppins', 'PingFang SC', system-ui, -apple-system, sans-serif;
  font-size: 13px;
  color: var(--color-text-muted);
}

/* 操作按钮样式 */
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  color: var(--color-text-sub);
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.95);
  border-color: var(--color-primary);
  color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.disable-btn {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.disable-btn:hover {
  background: var(--color-danger);
  color: white;
}

.enable-btn {
  border-color: var(--color-success);
  color: var(--color-success);
}

.enable-btn:hover {
  background: var(--color-success);
  color: white;
}

/* 空状态样式 */
.empty {
  text-align: center;
  padding: 60px 40px;
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.7;
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-muted);
}

/* 分页样式 */
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: none;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 16px;
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
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  color: var(--color-text-sub);
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.page-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.7);
  border-color: var(--color-primary);
  color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  color: var(--color-text-muted);
}

.page-number {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  color: var(--color-text-sub);
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.page-number:hover {
  background: rgba(255, 255, 255, 0.7);
  border-color: var(--color-primary);
  color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.page-number.active {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border-color: transparent;
}

.page-size {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-sub);
  font-size: 14px;
  font-weight: 500;
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
  backdrop-filter: blur(8px);
}

.modal-content {
  border-radius: 12px;
  width: 500px;
  max-width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
  animation: modalFadeIn 0.3s ease;
}

@keyframes modalFadeIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h4 {
  margin: 0;
  font-size: 18px;
  color: var(--color-text-main);
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--color-text-sub);
  transition: color 0.3s ease;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.close-btn:hover {
  color: var(--color-text-main);
  background: rgba(255, 255, 255, 0.7);
}

.modal-body {
  padding: 20px;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: rgba(255, 255, 255, 0.8);
  padding: 24px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-row .label {
  font-weight: 600;
  color: var(--color-text-sub);
  min-width: 80px;
}

.detail-row span:last-child {
  color: var(--color-text-main);
}

/* 编辑表单样式 */
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-weight: 600;
  color: var(--color-text-sub);
  font-size: 14px;
}

.form-input {
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  background: var(--color-bg-card);
  color: var(--color-text-main);
  transition: all 0.3s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.12);
}

.form-input:disabled {
  background: var(--color-bg-page);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-bar .form-input {
  width: 280px;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

/* 加载状态样式 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  border-radius: 12px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid var(--color-bg-page);
  border-top: 3px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.loading-text {
  font-size: 16px;
  color: var(--color-text-sub);
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
    align-items: flex-start;
  }
  
  .search-bar {
    width: 100%;
  }
  
  .search-bar input {
    width: 100%;
  }
  
  .pagination {
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }
  
  .actions {
    flex-direction: column;
    gap: 4px;
  }
  
  .actions button {
    width: 100%;
    text-align: center;
  }
}
</style>