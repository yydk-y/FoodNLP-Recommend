<template>
  <div class="dish-manage card">
    <div class="header">
      <h3 class="page-title">菜品管理</h3>
      <div class="header-actions">
        <button class="btn-primary" @click="openEdit(null)">新增菜品</button>
        <button class="btn-secondary" @click="openCategoryModal">管理分类</button>
      </div>
    </div>
    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>图片</th>
            <th>名称</th>
            <th>分类</th>
            <th>价格</th>
            <th>口味</th>
            <th>食材</th>
            <th>状态</th>
            <th>销量</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="dish in dishes" :key="dish.id" class="table-row">
            <td>{{ dish.id }}</td>
            <td class="table-image">
              <img v-if="dish.image" :src="dish.image" :alt="dish.name" />
            </td>
            <td>{{ dish.name }}</td>
            <td class="category-cell" :title="dish.category">{{ dish.category }}</td>
            <td class="gradient-text">￥{{ dish.price }}</td>
            <td class="flavors-cell">
              <div class="flavors-wrap">
                <template v-if="dish.flavors && dish.flavors.length">
                  <span v-for="flavor in dish.flavors" :key="`${dish.id}-${flavor}`" class="flavor-tag">{{ flavor }}</span>
                </template>
                <span v-else>-</span>
              </div>
            </td>
            <td>{{ dish.ingredients ? dish.ingredients.join('、') : '-' }}</td>
            <td>
              <span class="status-badge" :class="dish.isAvailable ? 'status-active' : 'status-inactive'">
                {{ dish.isAvailable ? '上架' : '下架' }}
              </span>
            </td>
            <td>{{ dish.salesCount }}</td>
            <td class="action-buttons">
              <button class="action-btn edit-btn" @click="openEdit(dish)">编辑</button>
              <button class="action-btn status-btn" @click="toggleStatus(dish)">{{ dish.isAvailable ? '下架' : '上架' }}</button>
              <button class="action-btn delete-btn" @click="deleteDish(dish.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination" v-if="pagination.total > 0">
      <button 
        @click="handlePageChange(pagination.current - 1)" 
        :disabled="pagination.current <= 1"
        class="page-btn"
      >
        上一页
      </button>
      
      <span class="page-info">
        第 {{ pagination.current }} 页，共 {{ Math.ceil(pagination.total / pagination.pageSize) }} 页
        （{{ pagination.total }} 条记录）
      </span>
      
      <button 
        @click="handlePageChange(pagination.current + 1)" 
        :disabled="pagination.current >= Math.ceil(pagination.total / pagination.pageSize)"
        class="page-btn"
      >
        下一页
      </button>
      
      <select v-model="pagination.pageSize" @change="handlePageSizeChange" class="page-size-select">
        <option value="5">5条/页</option>
        <option value="10">10条/页</option>
        <option value="20">20条/页</option>
        <option value="50">50条/页</option>
      </select>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <div class="loading-text">加载中...</div>
    </div>

    <!-- 编辑弹窗 -->
    <teleport to="body">
      <div v-if="editVisible" class="modal">
      <div class="modal-content card-glass">
        <div class="modal-header">
          <button class="back-btn" @click="editVisible = false">← 返回</button>
          <h4>{{ editingDish ? '编辑菜品' : '新增菜品' }}</h4>
        </div>
        <input v-model="form.dishName" placeholder="菜名" class="form-input" />
        <div class="category-select-wrapper">
          <div class="category-select-header">
            <span>菜品分类：</span>
            <input 
              v-model="dishCategorySearch" 
              placeholder="搜索分类..." 
              class="category-select-search"
              @input="handleDishCategorySearch"
            />
          </div>
          <select v-model="form.categoryId" class="category-select form-input">
            <option value="">请选择分类</option>
            <option v-for="category in filteredDishCategories" :key="category.id" :value="category.id">
              {{ category.name }}
            </option>
          </select>
          <div v-if="filteredDishCategories.length === 0" class="no-categories">
            暂无分类，请先添加分类
          </div>
        </div>
        <input v-model="form.price" type="number" step="0.01" placeholder="价格" class="form-input" />
        <input v-model="form.flavorsStr" placeholder="口味（多个用逗号分隔，如：辣,香）" class="form-input" />
        <input v-model="form.ingredientsStr" placeholder="食材（多个用逗号分隔，如：鸡肉,花生,辣椒）" class="form-input" />
        <textarea v-model="form.description" placeholder="菜品描述" rows="3" class="form-input"></textarea>
        <div class="image-upload">
          <label>菜品图片：</label>
          <input type="file" accept="image/*" @change="handleImageUpload" class="file-input" />
          <div v-if="form.imageUrl" class="image-preview">
            <img :src="resolveImageUrl(form.imageUrl)" alt="预览" />
            <button class="remove-image" @click="removeImage">×</button>
          </div>
          <div v-else class="image-placeholder">暂无图片</div>
        </div>
        <div class="modal-actions">
          <button class="btn-primary" @click="saveDish">保存</button>
          <button class="btn-secondary" @click="editVisible = false">取消</button>
        </div>
      </div>
    </div>
    </teleport>

    <!-- 菜品分类管理弹窗 -->
    <teleport to="body">
      <div v-if="categoryModalVisible" class="modal">
      <div class="modal-content card-glass" style="width: 600px;">
        <div class="modal-header">
          <button class="back-btn" @click="categoryModalVisible = false">← 返回</button>
          <h4>菜品分类管理</h4>
        </div>
        
        <!-- 分类搜索和新增 -->
        <div class="category-header">
          <div class="category-search">
            <input v-model="categorySearch" placeholder="搜索分类..." @input="handleCategorySearch" class="form-input" />
            <span class="search-icon">🔍</span>
          </div>
          <div class="category-form">
            <input v-model="categoryForm.categoryName" placeholder="分类名称" class="form-input" />
            <button @click="addCategory" class="btn-primary">添加分类</button>
          </div>
        </div>
        
        <!-- 分类列表 -->
        <div class="category-list">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>分类名称</th>
                <th>菜品数量</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="category in filteredCategories" :key="category.id" class="table-row">
                <td>{{ category.id }}</td>
                <td>
                  <span v-if="!category.editing">{{ category.name }}</span>
                  <input v-else v-model="category.editName" class="form-input" />
                </td>
                <td>{{ getDishCountByCategory(category.name) }}</td>
                <td class="action-buttons">
                  <button v-if="!category.editing" class="action-btn edit-btn" @click="startEditCategory(category)">编辑</button>
                  <button v-else class="action-btn save-btn" @click="saveCategoryEdit(category)">保存</button>
                  <button v-if="category.editing" class="action-btn cancel-btn" @click="cancelEditCategory(category)">取消</button>
                  <button class="action-btn delete-btn" @click="deleteCategory(category)" :disabled="getDishCountByCategory(category.name) > 0">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <div class="modal-actions">
          <button class="btn-secondary" @click="categoryModalVisible = false">关闭</button>
        </div>
      </div>
    </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { dishInfoAPI, dishCategoryAPI, imageAPI } from '@/api'
import { resolveImageUrl } from '@/utils/image'

const dishes = ref([])
const editVisible = ref(false)
const editingDish = ref(null)

// 分页相关
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})
const loading = ref(false)
const form = ref({
  dishName: '',
  categoryId: '',
  categoryName: '',
  price: 0,
  flavorsStr: '',
  ingredientsStr: '',
  description: '',
  imageUrl: '',
  uploadFile: null, // 用于保存上传的文件对象
  status: 1
})

// 菜品分类相关
const categoryModalVisible = ref(false)
const categories = ref([])
const filteredCategories = ref([]) // 过滤后的分类列表
const categoryForm = ref({
  categoryName: ''
})
const categorySearch = ref('') // 分类搜索关键词
const dishCategorySearch = ref('') // 菜品表单中的分类搜索关键词
const filteredDishCategories = ref([]) // 菜品表单中过滤后的分类列表

const parseMultiValueField = (value) => {
  if (!value) return []
  if (Array.isArray(value)) {
    return value.map(v => String(v).trim()).filter(Boolean)
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text || text === '[]') return []

    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) {
        return parsed.map(v => String(v).trim()).filter(Boolean)
      }
    } catch (_) {
      // 忽略JSON解析失败，继续走分隔符解析
    }

    return text
      .replace(/[\[\]"]/g, '')
      .split(/[、,，\/\s]+/)
      .map(s => s.trim())
      .filter(Boolean)
  }
  return []
}

const loadDishes = async () => {
  loading.value = true
  try {
    // 先加载分类数据
    await loadCategories()
    
    const res = await dishInfoAPI.getByPage(pagination.value.current, pagination.value.pageSize)
    console.log('菜品数据返回结果:', res)
    if (res.data.code === 200) {
      // 将后端返回的菜品数据转换为前端需要的格式
      dishes.value = res.data.data.records.map(dish => {
        console.log('单个菜品数据:', dish)
        // 统一解析口味字段（兼容JSON数组字符串/逗号分隔字符串/数组）
        const flavors = parseMultiValueField(dish.taste)

        // 统一解析食材字段
        const ingredients = parseMultiValueField(dish.ingredient)
        
        // 尝试获取分类名称
        let categoryName = dish.categoryName || dish.category || dish.category_name || dish.CategoryName || ''
        
        // 如果没有分类名称，根据categoryId查找
        if (!categoryName) {
          const categoryId = dish.categoryId || dish.category_id || dish.CategoryId
          if (categoryId) {
            const category = categories.value.find(cat => cat.id === categoryId)
            if (category) {
              categoryName = category.name
            }
          }
        }
        
        console.log('分类名称:', categoryName)
        
        return {
          id: dish.dishId || dish.id,
          name: dish.dishName || dish.name,
          category: categoryName || '未分类',
          categoryId: dish.categoryId || dish.category_id || dish.CategoryId,
          price: dish.price,
          flavors: flavors,
          ingredients: ingredients,
          description: dish.description,
          image: resolveImageUrl(dish.imageUrl || dish.image),
          isAvailable: dish.status === 1,
          salesCount: dish.salesCount || 0,
          // 保存原始字段，用于更新操作
          taste: dish.taste || '',
          ingredient: dish.ingredient || ''
        }
      })
      
      // 更新分页信息
      pagination.value.total = res.data.data.total
      pagination.value.current = res.data.data.current
      pagination.value.pageSize = res.data.data.size
      console.log('处理后的菜品数据:', dishes.value)
    }
  } catch (error) {
    console.error('加载菜品失败:', error)
    alert('加载菜品失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

// 加载分类数据
const loadCategories = async () => {
  try {
    console.log('开始加载分类数据')
    const res = await dishCategoryAPI.getAll()
    console.log('分类数据返回结果:', res)
    if (res.data.code === 200) {
      // 将后端返回的分类数据转换为前端需要的格式
      categories.value = res.data.data.map(category => ({
        id: category.categoryId || category.id,
        name: category.categoryName || category.name,
        editing: false,
        editName: ''
      }))
      
      // 初始化过滤后的分类列表
      filteredCategories.value = [...categories.value]
      console.log('分类数据加载成功:', categories.value)
    } else {
      console.error('加载分类失败，返回码:', res.data.code)
      alert('加载分类失败，请检查网络连接')
    }
  } catch (error) {
    console.error('加载分类失败:', error)
    alert('加载分类失败，请检查网络连接')
  }
}

// 分类搜索处理
const handleCategorySearch = () => {
  if (!categorySearch.value.trim()) {
    filteredCategories.value = [...categories.value]
    return
  }
  
  const searchTerm = categorySearch.value.toLowerCase().trim()
  filteredCategories.value = categories.value.filter(category => 
    category.name.toLowerCase().includes(searchTerm)
  )
}

// 菜品表单中的分类搜索处理
const handleDishCategorySearch = () => {
  if (!dishCategorySearch.value.trim()) {
    filteredDishCategories.value = [...categories.value]
    return
  }
  
  const searchTerm = dishCategorySearch.value.toLowerCase().trim()
  filteredDishCategories.value = categories.value.filter(category => 
    category.name.toLowerCase().includes(searchTerm)
  )
}

const openEdit = async (dish) => {
  // 加载分类数据
  await loadCategories()
  
  // 初始化菜品表单中的分类搜索
  dishCategorySearch.value = ''
  filteredDishCategories.value = [...categories.value]
  
  if (dish) {
    editingDish.value = dish
    form.value = {
      dishName: dish.name,
      categoryId: dish.categoryId,
      categoryName: dish.category,
      price: dish.price,
      flavorsStr: dish.flavors ? dish.flavors.join('、') : '',
      ingredientsStr: dish.ingredients ? dish.ingredients.join('、') : '',
      description: dish.description || '',
      imageUrl: dish.image || '',
      status: dish.isAvailable ? 1 : 0
    }
  } else {
    editingDish.value = null
    form.value = {
      dishName: '',
      categoryId: '',
      categoryName: '',
      price: 0,
      flavorsStr: '',
      ingredientsStr: '',
      description: '',
      imageUrl: '',
      status: 1
    }
  }
  editVisible.value = true
}

// 处理图片上传
const handleImageUpload = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  
  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  
  // 验证文件大小
  if (file.size > 2 * 1024 * 1024) {
    alert('图片大小不能超过 2MB')
    return
  }
  
  try {
    // 显示上传中状态
    const originalImageUrl = form.value.imageUrl
    form.value.imageUrl = 'uploading'
    
    // 如果是编辑模式，使用现有菜品ID；如果是新增模式，先保存菜品再上传图片
    if (editingDish.value) {
      // 编辑模式：直接上传图片
      try {
        const res = await imageAPI.uploadDishImage(file, editingDish.value.id)
        console.log('图片上传响应:', res)
        if (res.data.code === 200) {
          console.log('图片上传成功，返回的URL:', res.data.data)
          // 确保返回的是完整的图片URL
          let imageUrl = res.data.data
          // 如果是相对路径，添加基础URL
          if (imageUrl && !imageUrl.startsWith('http://') && !imageUrl.startsWith('https://')) {
            // 假设后端服务运行在同一个域名下
            imageUrl = window.location.origin + '/api' + imageUrl
          }
          form.value.imageUrl = imageUrl
          console.log('最终使用的图片URL:', form.value.imageUrl)
          alert('图片上传成功')
        } else {
          form.value.imageUrl = originalImageUrl
          alert('图片上传失败：' + (res.data.message || '服务器错误'))
        }
      } catch (error) {
        form.value.imageUrl = originalImageUrl
        console.error('图片上传失败:', error)
        alert('图片上传失败，请检查网络连接或联系管理员')
      }
    } else {
      // 新增模式：先显示本地预览，保存菜品时再上传
      const reader = new FileReader()
      reader.onload = (event) => {
        form.value.imageUrl = event.target.result
      }
      reader.readAsDataURL(file)
      
      // 保存文件对象用于后续上传
      form.value.uploadFile = file
    }
  } catch (error) {
    console.error('图片上传失败:', error)
    alert('图片上传失败，请重试')
  }
}

const removeImage = async () => {
  // 如果是编辑模式且图片是远程图片，调用后端API删除
  if (editingDish.value && form.value.imageUrl && !form.value.imageUrl.startsWith('data:image')) {
    try {
      const res = await imageAPI.deleteDishImage(form.value.imageUrl)
      if (res.data.code === 200) {
        alert('图片删除成功')
      } else {
        alert('图片删除失败：' + res.data.message)
      }
    } catch (error) {
      console.error('图片删除失败:', error)
      alert('图片删除失败，请重试')
    }
  }
  
  // 清除表单中的图片信息
  form.value.imageUrl = ''
  form.value.uploadFile = null
  
  // 重置文件输入框
  const fileInput = document.querySelector('.image-upload input[type="file"]')
  if (fileInput) fileInput.value = ''
}

// 辅助函数：将前端图片地址转换为后端可存储路径
const toBackendImageUrl = (url) => {
  if (!url || url === 'uploading' || url.startsWith('data:image')) return ''

  let normalized = String(url).trim()
  if (!normalized) return ''

  // 去掉当前域名
  if (normalized.startsWith(window.location.origin)) {
    normalized = normalized.slice(window.location.origin.length)
  }

  // 开发环境代理前缀 /api
  if (normalized.startsWith('/api/')) {
    normalized = normalized.slice(4)
  }

  return normalized
}

// 辅助函数：将逗号分隔字符串转换为数组，并去除空白项
const parseCommaString = (str) => {
  if (!str) return []
  return str.split(/[,，]+/).map(s => s.trim()).filter(s => s)
}

const saveDish = async () => {
  if (!form.value.dishName.trim()) {
    alert('请填写菜名')
    return
  }
  if (form.value.price <= 0) {
    alert('价格必须大于0')
    return
  }
  if (!form.value.categoryId) {
    alert('请选择分类')
    return
  }

  // 构建后端需要的DTO数据格式
  const dishData = {
    dishName: form.value.dishName.trim(),
    categoryId: form.value.categoryId,
    price: parseFloat(form.value.price),
    taste: parseCommaString(form.value.flavorsStr).join(','), // 使用逗号分隔字符串
    ingredient: parseCommaString(form.value.ingredientsStr).join(','), // 使用逗号分隔字符串
    description: form.value.description || '',
    imageUrl: toBackendImageUrl(form.value.imageUrl),
    status: form.value.status
  }

  try {
    let res
    if (editingDish.value) {
      // 编辑菜品
      dishData.dishId = editingDish.value.id
      res = await dishInfoAPI.update(dishData)
      
      if (res.data.code === 200) {
        alert('菜品更新成功')
        editVisible.value = false
        await loadDishes()
      } else {
        alert('菜品更新失败：' + res.data.message)
      }
    } else {
      // 新增菜品：先保存菜品信息
      res = await dishInfoAPI.add(dishData)
      
      if (res.data.code === 200) {
        const newDishId = res.data.data.dishId || res.data.data.id
        
        // 如果有上传的图片文件，上传图片
        if (form.value.uploadFile) {
          try {
            const imageRes = await imageAPI.uploadDishImage(form.value.uploadFile, newDishId)
            console.log('图片上传响应:', imageRes)
            if (imageRes.data.code === 200) {
              console.log('图片上传成功，返回的URL:', imageRes.data.data)
              // 确保返回的是完整的图片URL
              let imageUrl = imageRes.data.data
              // 如果是相对路径，添加基础URL
              if (imageUrl && !imageUrl.startsWith('http://') && !imageUrl.startsWith('https://')) {
                // 假设后端服务运行在同一个域名下
                imageUrl = window.location.origin + '/api' + imageUrl
              }
              // 更新菜品图片URL
              await dishInfoAPI.update({
                dishId: newDishId,
                imageUrl: toBackendImageUrl(imageUrl)
              })
              console.log('最终使用的图片URL:', imageUrl)
            }
          } catch (imageError) {
            console.error('图片上传失败:', imageError)
            // 图片上传失败不影响菜品保存，只是使用默认图片
          }
        }
        
        alert('菜品添加成功')
        editVisible.value = false
        await loadDishes()
      } else {
        alert('菜品添加失败：' + res.data.message)
      }
    }
  } catch (error) {
    console.error('保存菜品失败:', error)
    alert('保存菜品失败，请检查网络连接')
  }
}

const toggleStatus = async (dish) => {
  try {
    // 只需要更新状态，其他字段保持原样
    const dishData = {
      dishId: dish.id,
      dishName: dish.name,
      categoryId: dish.categoryId,
      price: dish.price,
      taste: dish.taste || '', // 使用原始的taste字段，避免重复序列化
      ingredient: dish.ingredient || '', // 使用原始的ingredient字段
      description: dish.description || '',
      imageUrl: toBackendImageUrl(dish.image),
      status: dish.isAvailable ? 0 : 1 // 切换状态
    }
    
    const res = await dishInfoAPI.update(dishData)
    if (res.data.code === 200) {
      alert(dish.isAvailable ? '下架成功' : '上架成功')
      await loadDishes()
    } else {
      alert(res.data.message || '操作失败')
    }
  } catch (error) {
    console.error('切换状态失败:', error)
    alert('切换状态失败，请检查网络连接')
  }
}

const deleteDish = async (id) => {
  if (confirm('确认删除？')) {
    try {
      const res = await dishInfoAPI.delete(id)
      if (res.data.code === 200) {
        alert('删除菜品成功')
        await loadDishes()
      } else {
        alert(res.data.message || '删除菜品失败')
      }
    } catch (error) {
      console.error('删除菜品失败:', error)
      alert('删除菜品失败，请检查网络连接')
    }
  }
}

// 菜品分类管理函数
const openCategoryModal = async () => {
  categoryModalVisible.value = true
  categoryForm.value.categoryName = ''
  await loadCategories()
}

const addCategory = async () => {
  if (!categoryForm.value.categoryName.trim()) {
    alert('请输入分类名称')
    return
  }
  
  try {
    // 调用后端API添加分类
    const res = await dishCategoryAPI.add({
      categoryName: categoryForm.value.categoryName.trim()
    })
    
    if (res.data.code === 200) {
      alert('添加分类成功')
      categoryForm.value.categoryName = ''
      await loadCategories() // 重新加载分类列表
    } else {
      alert(res.data.message || '添加分类失败')
    }
  } catch (error) {
    console.error('添加分类失败:', error)
    alert('添加分类失败，请检查网络连接')
  }
}

const startEditCategory = (category) => {
  category.editing = true
  category.editName = category.name
}

const saveCategoryEdit = async (category) => {
  if (!category.editName.trim()) {
    alert('分类名称不能为空')
    return
  }
  
  try {
    // 调用后端API更新分类
    const res = await dishCategoryAPI.update({
      categoryId: category.id,
      categoryName: category.editName.trim()
    })
    
    if (res.data.code === 200) {
      alert('更新分类成功')
      category.name = category.editName.trim()
      category.editing = false
      category.editName = ''
      await loadCategories() // 重新加载分类列表
    } else {
      alert(res.data.message || '更新分类失败')
    }
  } catch (error) {
    console.error('更新分类失败:', error)
    alert('更新分类失败，请检查网络连接')
  }
}

const cancelEditCategory = (category) => {
  category.editing = false
  category.editName = ''
}

const deleteCategory = async (category) => {
  if (getDishCountByCategory(category.name) > 0) {
    alert('该分类下还有菜品，无法删除')
    return
  }
  
  if (confirm(`确认删除分类"${category.name}"？`)) {
    try {
      // 调用后端API删除分类
      const res = await dishCategoryAPI.delete(category.id)
      
      if (res.data.code === 200) {
        alert('删除分类成功')
        await loadCategories() // 重新加载分类列表
      } else {
        alert(res.data.message || '删除分类失败')
      }
    } catch (error) {
      console.error('删除分类失败:', error)
      alert('删除分类失败，请检查网络连接')
    }
  }
}

const getDishCountByCategory = (categoryName) => {
  return dishes.value.filter(dish => dish.category === categoryName).length
}

// 分页相关函数
const handlePageChange = (page) => {
  if (page < 1 || page > Math.ceil(pagination.value.total / pagination.value.pageSize)) {
    return
  }
  pagination.value.current = page
  loadDishes()
}

const handlePageSizeChange = () => {
  pagination.value.current = 1 // 切换每页条数时回到第一页
  loadDishes()
}

watch([editVisible, categoryModalVisible], ([editing, category]) => {
  const hasModalOpen = editing || category
  document.body.style.overflow = hasModalOpen ? 'hidden' : ''
})

onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

onMounted(() => {
  loadDishes()
  loadCategories()
})
</script>

<style scoped>
.dish-manage {
  overflow-x: auto;
  position: relative;
}

.dish-manage::before {
  content: '';
  position: absolute;
  top: -120px;
  right: -120px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(67, 56, 202, 0.12), transparent 65%);
  pointer-events: none;
}

.header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
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

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.table-container {
  overflow-x: auto;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(8px);
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 1000px;
}

th, td {
  padding: 16px 12px;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  transition: background-color 0.2s ease;
  white-space: normal;
  vertical-align: middle;
}

.category-cell {
  white-space: nowrap;
}

.flavors-cell {
  min-width: 140px;
}

.flavors-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.flavor-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-primary);
  background: rgba(67, 56, 202, 0.1);
  border: 1px solid rgba(67, 56, 202, 0.2);
  white-space: nowrap;
}

th {
  background: rgba(244, 245, 251, 0.85);
  font-weight: 600;
  color: var(--color-text-sub);
  font-size: 13px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.table-row:hover {
  background: rgba(67, 56, 202, 0.04);
}

td.table-image {
  width: 80px;
}

td.table-image img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

td.table-image img:hover {
  transform: scale(1.05);
  box-shadow: var(--shadow-md);
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
}

.status-active {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.status-inactive {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  border: 1px solid rgba(239, 68, 68, 0.2);
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.9);
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

.edit-btn {
  color: var(--color-primary);
  border-color: var(--color-primary-light);
}

.edit-btn:hover {
  background: var(--color-primary-light);
}

.status-btn {
  color: var(--color-warning);
  border-color: rgba(245, 158, 11, 0.2);
}

.status-btn:hover {
  background: rgba(245, 158, 11, 0.1);
}

.delete-btn {
  color: var(--color-danger);
  border-color: rgba(239, 68, 68, 0.2);
}

.delete-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
}

.delete-btn:disabled {
  background: var(--color-bg-glass);
  color: var(--color-text-muted);
  cursor: not-allowed;
  border-color: var(--color-border);
}

.save-btn {
  color: var(--color-success);
  border-color: rgba(16, 185, 129, 0.2);
}

.save-btn:hover {
  background: rgba(16, 185, 129, 0.1);
}

.cancel-btn {
  color: var(--color-text-sub);
  border-color: var(--color-border);
}

.cancel-btn:hover {
  background: var(--color-bg-glass);
}

/* 弹窗样式 */
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
  border-radius: 18px;
  width: 500px;
  max-width: 90%;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-glass);
}

.modal-header {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 16px;
}

.back-btn {
  background: var(--color-bg-glass);
  color: var(--color-text-sub);
  border: 1px solid var(--color-border);
  padding: 8px 16px;
  border-radius: 12px;
  cursor: pointer;
  margin-right: 16px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.back-btn:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary-light);
}

.modal-header h4 {
  margin: 0;
  flex: 1;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-main);
}

/* 表单样式 */
.form-input {
  width: 100%;
  margin-bottom: 16px;
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
  box-shadow: 0 0 0 3px rgba(59, 47, 127, 0.12);
}

.file-input {
  width: 100%;
  margin-bottom: 12px;
  padding: 8px;
  font-size: 14px;
}

.image-upload {
  margin-bottom: 16px;
}

.image-upload label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--color-text-sub);
  font-size: 14px;
}

.image-preview {
  position: relative;
  margin-top: 8px;
  display: inline-block;
}

.image-preview img {
  max-width: 200px;
  max-height: 150px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  transition: transform 0.3s ease;
}

.image-preview img:hover {
  transform: scale(1.02);
}

.remove-image {
  position: absolute;
  top: -10px;
  right: -10px;
  background: var(--color-danger);
  color: white;
  border: none;
  border-radius: 50%;
  width: 28px;
  height: 28px;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
  transition: all 0.2s ease;
}

.remove-image:hover {
  transform: scale(1.1);
  box-shadow: var(--shadow-md);
}

.image-placeholder {
  color: var(--color-text-muted);
  font-size: 14px;
  padding: 32px;
  border: 2px dashed var(--color-border);
  text-align: center;
  margin-top: 8px;
  border-radius: 12px;
  background: var(--color-bg-glass);
}

.modal-actions {
  text-align: right;
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 菜品分类管理样式 */
.category-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.category-search {
  position: relative;
  flex: 1;
  min-width: 200px;
}

.category-search input {
  width: 100%;
  padding: 12px 40px 12px 16px;
  font-size: 14px;
}

.search-icon {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-muted);
  font-size: 16px;
}

.category-form {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.category-form input {
  width: 200px;
  margin-bottom: 0;
}

.category-list {
  max-height: 400px;
  overflow-y: auto;
  margin-bottom: 24px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.category-list table {
  min-width: auto;
  width: 100%;
}

.category-list th,
.category-list td {
  padding: 12px 16px;
  text-align: center;
}

.category-list td:nth-child(2) {
  text-align: left;
}

.category-list input {
  width: 100%;
  margin-bottom: 0;
  padding: 8px 12px;
}

/* 菜品表单中的分类选择器样式 */
.category-select-wrapper {
  margin-bottom: 16px;
}

.category-select-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  gap: 12px;
  flex-wrap: wrap;
}

.category-select-header span {
  font-weight: 600;
  color: var(--color-text-sub);
  min-width: 80px;
  font-size: 14px;
}

.category-select-search {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  background: white;
  transition: all 0.2s ease;
}

.category-select-search:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 47, 127, 0.12);
}

.category-select {
  width: 100%;
  padding: 12px 16px;
  font-size: 14px;
  margin-bottom: 8px;
}

.no-categories {
  text-align: center;
  color: var(--color-text-muted);
  font-size: 14px;
  padding: 16px;
  background: var(--color-bg-glass);
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

/* 分页样式 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: 24px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-btn {
  padding: 10px 20px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border-color: transparent;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.page-btn:disabled {
  background: var(--color-bg-glass);
  color: var(--color-text-muted);
  cursor: not-allowed;
  border-color: var(--color-border);
}

.page-info {
  font-size: 14px;
  color: var(--color-text-sub);
  font-weight: 500;
}

.page-size-select {
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
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

/* 加载状态 */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 9999;
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
  
  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
  
  .category-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .category-search {
    width: 100%;
  }
  
  .category-form {
    width: 100%;
  }
  
  .category-form input {
    flex: 1;
  }
  
  .modal-content {
    padding: 24px;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .action-btn {
    width: 100%;
    text-align: center;
  }
}
</style>