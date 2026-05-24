import request from './request'
import { dishes, getRecommendations, parseNaturalLanguage, hotDishes, getDishDetail } from '@/utils/mockData'
import { addReview, getReviewsByDish, getReviewByUserAndOrder } from '@/utils/mockData';

// 图片上传API（对接真实后端接口）
export const imageAPI = {
  // 上传菜品图片
  uploadDishImage: async (file, dishId) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('dishId', dishId)
    
    return request.post('/image/dish/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  
  // 删除菜品图片
  deleteDishImage: async (imageUrl) => {
    return request.delete('/image/dish/delete', {
      params: { imageUrl }
    })
  },
  
  // 根据菜品ID获取图片URL
  getDishImageUrl: async (dishId) => {
    return request.get('/image/dish/url', {
      params: { dishId: Number(dishId) }
    })
  },

  // 获取图片存储信息
  getImageInfo: async () => {
    return request.get('/image/info')
  }
}

// 评论API
// 用户API（对接真实后端接口）
export const userAPI = {
  // 根据用户ID查询用户信息
  getUserInfo: async (userId) => {
    return request.get(`/user/${Number(userId)}`)
  },
  
  // 更新用户信息
  updateUser: async (userInfo) => {
    return request.put('/user/update', userInfo)
  },
  
  // 修改密码
  changePassword: async (oldPassword, newPassword) => {
    return request.put('/user/change-password', {
      oldPassword,
      newPassword
    })
  },
  
  // 重置密码
  resetPassword: async (userId) => {
    return request.put(`/user/reset-password?userId=${Number(userId)}`)
  },
  
  // 获取所有用户列表（管理员用）
  getAllUsers: async () => {
    return request.get('/user/all')
  },
  
  // 用户分页查询
  getUserPage: async (params) => {
    return request.get('/user/page', { params })
  },
  
  // 更新用户状态
  updateStatus: async (userId, status) => {
    return request.put(`/user/status?userId=${Number(userId)}&status=${Number(status)}`)
  },
  
  // 删除用户
  deleteUser: async (userId) => {
    return request.delete(`/user/${Number(userId)}`)
  }
}

// 用户偏好API（对接真实后端接口）
export const preferenceAPI = {
  // 获取用户偏好
  getPreference: async (userId) => {
    return request.get(`/preference/${Number(userId)}`)
  },
  
  // 获取用户历史偏好（Redis）
  getHistoryPreference: async (userId) => {
    return request.get(`/preference/history/${Number(userId)}`)
  },

  // 创建或更新用户偏好
  savePreference: async (preference) => {
    return request.post('/preference/save', preference)
  },
  
  // 更新口味偏好
  updateTaste: async (userId, taste) => {
    return request.put('/preference/taste', {
      userId: Number(userId),
      taste: JSON.stringify(taste)
    })
  },
  
  // 更新食材偏好
  updateIngredient: async (userId, ingredient) => {
    return request.put('/preference/ingredient', {
      userId: Number(userId),
      ingredient: JSON.stringify(ingredient)
    })
  },
  
  // 更新忌口
  updateTaboo: async (userId, taboo) => {
    return request.put('/preference/taboo', {
      userId: Number(userId),
      taboo: JSON.stringify(taboo)
    })
  },
  
  // 删除用户偏好
  deletePreference: async (userId) => {
    return request.delete(`/preference/${Number(userId)}`)
  },
  
  // 初始化用户偏好
  initPreference: async (userId) => {
    return request.post(`/preference/init/${Number(userId)}`)
  }
}

// 统计API（对接真实后端接口）
export const statisticsAPI = {
  // 获取用户总数
  getUserTotalCount: async () => {
    return request.get('/statistics/user-total')
  },
  
  // 获取热销菜品Top5
  getHotDishesTop5: async () => {
    return request.get('/statistics/hot-dishes')
  },

  // 获取本周热销菜品Top5
  getWeeklyHotDishesTop5: async () => {
    return request.get('/statistics/hot-dishes/week')
  },

  // 获取本月热销菜品Top5
  getMonthlyHotDishesTop5: async () => {
    return request.get('/statistics/hot-dishes/month')
  },
  
  // 获取总订单数
  getTotalOrderCount: async () => {
    return request.get('/statistics/order-total')
  },
  
  // 获取今日订单数
  getTodayOrderCount: async () => {
    return request.get('/statistics/today-orders')
  },
  
  // 获取今日销售额
  getTodaySalesAmount: async () => {
    return request.get('/statistics/today-sales')
  },
  
  // 获取活跃用户数
  getActiveUserCount: async () => {
    return request.get('/statistics/active-users')
  },

  // 获取近7天销售额+订单数趋势
  getSalesOrderTrend7d: async () => {
    return request.get('/statistics/overview').then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data?.data?.salesOrderTrend7d || []
      }
    }))
  },

  // 获取近7天客单价趋势
  getAverageOrderAmountTrend7d: async () => {
    return request.get('/statistics/overview').then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data?.data?.averageOrderAmountTrend7d || []
      }
    }))
  },

  // 获取复购率（7日/30日）
  getRepurchaseRates: async () => {
    return request.get('/statistics/overview').then(res => ({
      ...res,
      data: {
        ...res.data,
        data: {
          repurchaseRate7d: res.data?.data?.repurchaseRate7d || 0,
          repurchaseRate30d: res.data?.data?.repurchaseRate30d || 0
        }
      }
    }))
  },
  
  // 获取所有统计信息（综合接口）
  getStatisticsOverview: async () => {
    return request.get('/statistics/overview')
  }
}

// 菜品评价API（对接真实后端接口）
export const ratingAPI = {
  // 添加菜品评价
  add: async (userId, dishId, orderId, score, comment) => {
    return request.post('/rating/add', {
      userId: Number(userId),
      dishId: Number(dishId),
      orderId: orderId.toString(),
      score: Number(score),
      comment: comment
    })
  },
  
  // 更新菜品评价
  update: async (ratingId, score, comment) => {
    return request.put(`/rating/update?ratingId=${Number(ratingId)}&score=${Number(score)}&comment=${encodeURIComponent(comment || '')}`)
  },
  
  // 删除菜品评价
  delete: async (ratingId) => {
    return request.delete(`/rating/delete/${Number(ratingId)}`)
  },
  
  // 获取菜品的所有评价
  getByDish: async (dishId) => {
    return request.get(`/rating/dish/${Number(dishId)}`)
  },
  
  // 获取用户的评价列表
  getByUser: async (userId) => {
    return request.get(`/rating/user/${Number(userId)}`)
  },
  
  // 获取订单的评价列表
  getByOrder: async (orderId) => {
    return request.get(`/rating/order/${orderId}`)
  },
  
  // 获取菜品的平均评分
  getAverage: async (dishId) => {
    return request.get(`/rating/average/${Number(dishId)}`)
  },
  
  // 获取菜品的评价统计
  getStatistics: async (dishId) => {
    return request.get(`/rating/statistics/${Number(dishId)}`)
  },
  
  // 检查用户是否已评价菜品
  checkReviewed: async (userId, dishId, orderId) => {
    return request.get(`/rating/check?userId=${Number(userId)}&dishId=${Number(dishId)}&orderId=${orderId}`)
  }
};
// 菜品管理API
export const dishAPI = {
  // 获取所有菜品（支持分类筛选）
  getList: async (params) => {
    await delay(300)
    let result = dishes.filter(d => d.isAvailable)
    if (params.category) {
      result = result.filter(d => d.category === params.category)
    }
    if (params.keyword) {
      result = result.filter(d => d.name.includes(params.keyword) || d.ingredients.some(i => i.includes(params.keyword)))
    }
    return { data: result }
  },
 getDetail: async (id) => {
    await delay(300);
    const dish = getDishDetail(id);
    if (dish) {
      return { data: dish };
    }
    throw new Error('菜品不存在');
  },
  // 从后端获取全部菜品（真实数据，非 mock）
  getAll: async () => {
    return request.get('/dish/dishes')
  },
  // 管理端接口（增删改）
  add: async (dish) => {
    await delay(500)
    const newId = Math.max(...dishes.map(d => d.id)) + 1
    const newDish = { ...dish, id: newId, isAvailable: true, salesCount: 0 }
    dishes.push(newDish)
    return { data: newDish }
  },
  update: async (id, data) => {
    await delay(500)
    const index = dishes.findIndex(d => d.id == id)
    if (index !== -1) {
      dishes[index] = { ...dishes[index], ...data }
      return { data: dishes[index] }
    }
    throw new Error('菜品不存在')
  },
  delete: async (id) => {
    await delay(500)
    const index = dishes.findIndex(d => d.id == id)
    if (index !== -1) {
      dishes.splice(index, 1)
      return { data: { success: true } }
    }
    throw new Error('菜品不存在')
  }
}

// 推荐API
export const recommendAPI = {
  getRecommendations: async (userId, preferences, restrictions, intent, flavor) => {
    await delay(400)
    const recs = getRecommendations(userId, preferences, restrictions, intent, flavor)
    return { data: recs }
  },
  getHotDishes: async () => {
    await delay(300)
    return { data: hotDishes.slice(0, 6) }
  },
  // 新增：根据用户输入推荐菜品
  recommendByInput: async (userInput, limit = 5) => {
    return request.post('/recommendation/recommend-by-input', {
      userInput,
      limit
    })
  },
  // 新增：根据用户偏好推荐菜品
  recommendByPreferences: async (preferences, limit = 5) => {
    return request.post('/recommendation/recommend-by-preferences', {
      preferences,
      limit
    })
  },
  // 新增：根据用户ID推荐菜品
  recommendByUserId: async (userId, page = 1, size = 10) => {
    return request.post('/recommendation/recommend-by-userId', {
      userId,
      page,
      size
    })
  },
  // 新增：健康检查
  healthCheck: async () => {
    return request.post('/recommendation/health')
  }
}

// 聊天推荐API（对接多轮会话推荐）
export const recommendationChatAPI = {
  chat: async ({ userInput, sessionId, userId }) => {
    return request.post('/recommendation-chat/chat', {
      userInput,
      sessionId,
      userId
    })
  },
  explainByInput: async ({ userInput, sessionId, userId }) => {
    return request.post('/recommendation-chat/explain-by-input', {
      userInput,
      sessionId,
      userId
    })
  },
  explainSelectedDishes: async ({ userInput, dishNames, sessionId, userId }) => {
    return request.post('/recommendation-chat/explain-selected-dishes', {
      userInput,
      dishNames,
      sessionId,
      userId
    })
  },
  getTempPreference: async (sessionId) => {
    return request.get(`/recommendation-chat/temp-preference?sessionId=${encodeURIComponent(sessionId)}`)
  }
}

// 自然语言解析API（模拟BERT服务）
export const nlpAPI = {
  parse: async (text) => {
    await delay(200)
    const result = parseNaturalLanguage(text)
    return { data: result }
  }
}

// 菜品分类API
export const dishCategoryAPI = {
  // 获取所有分类
  getAll: async () => {
    return request.get('/dish/categories')
  },
  
  // 添加分类
  add: async (categoryData) => {
    return request.post('/dish/categories', categoryData)
  },
  
  // 更新分类
  update: async (categoryData) => {
    return request.put('/dish/categories', categoryData)
  },
  
  // 删除分类
  delete: async (categoryId) => {
    return request.delete(`/dish/categories/${categoryId}`)
  },
  
  // 根据名称查询分类
  getByName: async (categoryName) => {
    return request.get(`/dish/categories/name?categoryName=${encodeURIComponent(categoryName)}`)
  }
}

// 菜品信息API（对接真实后端接口）
export const dishInfoAPI = {
  // 获取所有菜品
  getAll: async () => {
    return request.get('/dish/dishes')
  },
  
  // 添加菜品
  add: async (dishData) => {
    return request.post('/dish/dishes', dishData)
  },
  
  // 更新菜品
  update: async (dishData) => {
    return request.put('/dish/dishes', dishData)
  },
  
  // 删除菜品
  delete: async (dishId) => {
    return request.delete(`/dish/dishes/${dishId}`)
  },
  
  // 根据ID查询菜品
  getById: async (dishId) => {
    return request.get(`/dish/dishes/${dishId}`)
  },
  
  // 分页查询菜品
  getByPage: async (page = 1, size = 10) => {
    return request.get(`/dish/dishes/page?page=${page}&size=${size}`)
  },
  // 分页查询菜品评分排序（分页）
  getByScore: async (page = 1, size = 10) => {
    return request.get(`/dish/dishes/page/by-rating?page=${page}&size=${size}`)
  },
  // 根据分类查询菜品
  getByCategory: async (categoryId) => {
    return request.get(`/dish/dishes/category/${categoryId}`)
  },
  
  // 根据状态查询菜品
  getByStatus: async (status) => {
    return request.get(`/dish/dishes/status/${status}`)
  },
  
  // 根据名称精确查询
  getByName: async (dishName) => {
    return request.get(`/dish/dishes/name?dishName=${encodeURIComponent(dishName)}`)
  },
  
  // 根据名称模糊查询
  searchByName: async (dishName) => {
    return request.get(`/dish/dishes/search?dishName=${encodeURIComponent(dishName)}`)
  },
  
  // 根据名称模糊查询（分页）
  searchByNamePage: async (dishName, page = 1, size = 10) => {
    return request.get(`/dish/dishes/search/page?dishName=${encodeURIComponent(dishName)}&page=${page}&size=${size}`)
  },
  
  // 查询菜品及其分类信息（根据菜品ID）
  getDishWithCategoryById: async (dishId) => {
    return request.get(`/dish/dishes/${dishId}/with-category`)
  },
  
  // 查询所有菜品及其分类信息
  getAllDishesWithCategory: async () => {
    return request.get('/dish/dishes/with-category')
  },
  
  // 根据分类查询菜品及其分类信息
  getDishesWithCategoryByCategoryId: async (categoryId) => {
    return request.get(`/dish/dishes/with-category/category/${categoryId}`)
  },
  
  // 根据分类查询菜品（分页）
  getDishesByCategoryPage: async (categoryId, page = 1, size = 10) => {
    return request.get(`/dish/dishes/category/${categoryId}/page`, {
      params: {
        page: page,
        size: size
      }
    })
  },
  //根据分类查询菜品评分排序（分页）
  getDishesByCategoryScorePage: async (categoryId, page = 1, size = 10) => {
    return request.get(`/dish/dishes/category/${categoryId}/page/by-rating`, {
      params: {
        page: page,
        size: size
      }
    })
  }
}

// 购物车API（对接真实后端接口）
export const cartAPI = {
  // 添加商品到购物车
  addItem: async (cartItem) => {
    // 确保数字字段类型正确
    const formattedItem = {
      ...cartItem,
      userId: Number(cartItem.userId),
      dishId: Number(cartItem.dishId),
      quantity: Number(cartItem.quantity || cartItem.num || 1),
      dishPrice: Number(cartItem.dishPrice || cartItem.price)
    }
    return request.post('/cart/add', formattedItem)
  },
  
  // 获取购物车列表
  getCart: async (userId) => {
    return request.get(`/cart/list?userId=${Number(userId)}`)
  },
  
  // 更新购物车商品数量
  updateQuantity: async (cartId, quantity, remark) => {
    return request.put(`/cart/update?cartId=${Number(cartId)}&quantity=${Number(quantity)}&remark=${encodeURIComponent(remark || '')}`)
  },
  
  // 删除购物车商品
  removeItem: async (cartId) => {
    return request.delete(`/cart/remove/${Number(cartId)}`)
  },
  
  // 批量删除购物车商品
  batchRemove: async (cartIds) => {
    return request.delete('/cart/batch-remove', { 
      data: cartIds.map(id => Number(id))
    })
  },
  
  // 清空购物车
  clearCart: async (userId) => {
    return request.delete(`/cart/clear?userId=${Number(userId)}`)
  },
  
  // 获取购物车商品数量
  getCount: async (userId) => {
    return request.get(`/cart/count?userId=${Number(userId)}`)
  },
  
  // 批量结算购物车商品
  batchCheckout: async (userId, cartIds) => {
    return request.post(`/cart/batch-checkout?userId=${Number(userId)}`, 
      cartIds.map(id => Number(id))
    )
  }
}

// 订单API（对接真实后端接口）
export const orderAPI = {
  // 订单分页查询
  getOrderPage: async (params) => {
    return request.get('/order/page', { params })
  },
  // 获取用户订单列表
  getOrdersByUser: async (userId) => {
    return request.get(`/order/list?userId=${Number(userId)}`)
  },
  
  // 获取订单详情
  getOrderDetail: async (orderId) => {
    return request.get(`/order/detail/${Number(orderId)}`)
  },
  
  // 获取订单明细列表
  getOrderDetails: async (orderId) => {
    return request.get(`/order/details/${Number(orderId)}`)
  },

  // DeepSeek营养趋势分析（近7天）
  getNutritionTrendAI: async (userId) => {
    return request.get(`/order/nutrition-trend-ai?userId=${Number(userId)}`)
  },

  // 清除DeepSeek营养趋势Redis缓存
  clearNutritionTrendAICache: async (userId) => {
    return request.delete(`/order/nutrition-trend-ai/cache?userId=${Number(userId)}`)
  },
  
  // 更新订单状态
  updateStatus: async (orderId, status) => {
    return request.put(`/order/status?orderId=${Number(orderId)}&status=${Number(status)}`)
  },

  // 支付订单（旧接口，兼容保留）
  payOrder: async (orderId) => {
    return request.post(`/order/pay?orderId=${Number(orderId)}`)
  },

  // 创建模拟支付会话
  createPaySession: async (orderId, channel = 'ALIPAY') => {
    return request.post(`/order/pay/session?orderId=${Number(orderId)}&channel=${encodeURIComponent(channel)}`)
  },

  // 确认模拟支付完成
  confirmPay: async (orderId, sessionId) => {
    return request.post(`/order/pay/confirm?orderId=${Number(orderId)}&sessionId=${encodeURIComponent(sessionId)}`)
  },
  
  // 创建订单
  createOrder: async (orderData) => {
    return request.post('/order/create', orderData)
  },
  
  // 取消订单
  cancelOrder: async (orderId) => {
    return request.post(`/order/cancel?orderId=${Number(orderId)}`)
  },
  
  // 删除订单
  deleteOrder: async (orderId) => {
    return request.delete(`/order/delete?orderId=${Number(orderId)}`)
  },
  
  // 获取所有订单（管理员）
  getAllOrders: async () => {
    return request.get('/order/all')
  },
  
  // 按状态获取订单
  getOrdersByStatus: async (status) => {
    return request.get(`/order/by-status?status=${Number(status)}`)
  },
  
  // 按用户和状态获取订单
  getOrdersByUserAndStatus: async (userId, status) => {
    return request.get(`/order/user-by-status?userId=${Number(userId)}&status=${Number(status)}`)
  },
  
  // 获取订单统计信息
  getStatistics: async (userId = null) => {
    const url = userId ? `/order/statistics?userId=${Number(userId)}` : '/order/statistics'
    return request.get(url)
  }
}