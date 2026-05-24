<template>
  <div class="cart-view card-glass">
    <div class="cart-header">
      <h3 class="gradient-text">我的购物车</h3>
      <button v-if="cartData && cartData.items && cartData.items.length" class="clear-btn" @click="clearCart">
        清空购物车
      </button>
    </div>
    <div v-if="!cartData || !cartData.items || cartData.items.length === 0" class="empty">
      <div class="empty-icon">🛒</div>
      <div class="empty-text">购物车空空如也，去点餐吧~</div>
      <router-link to="/" class="btn-secondary">去点餐</router-link>
    </div>
    <div v-else>
      <div class="cart-header-row">
        <div class="header-checkbox">
          <input type="checkbox" v-model="allSelected" @change="handleSelectAll" class="custom-checkbox" />
          <span>全选</span>
        </div>
        <div class="header-info">商品信息</div>
        <div class="header-quantity">数量</div>
        <div class="header-price">单价</div>
        <div class="header-subtotal">小计</div>
        <div class="header-action">操作</div>
      </div>
      <div v-for="item in cartData.items" :key="item.cartId || item.id" class="cart-item">
        <div class="item-checkbox">
          <input type="checkbox" v-model="item.selected" @change="handleItemSelection(item)" class="custom-checkbox" />
        </div>
        <div class="item-info">
          <img :src="resolveImageUrl(item.dishImage || item.image)" :alt="item.dishName || item.name" class="item-image" />
          <div class="info-text">
            <h4>{{ item.dishName || item.name }}</h4>
            <p v-if="item.remark" class="remark">备注：{{ item.remark }}</p>
          </div>
        </div>
        <div class="item-quantity">
          <button @click="updateQuantity(item, -1)" class="quantity-btn">-</button>
          <span class="quantity-value">{{ item.num || item.quantity }}</span>
          <button @click="updateQuantity(item, 1)" class="quantity-btn">+</button>
        </div>
        <div class="item-price">￥{{ (item.dishPrice || item.price).toFixed(2) }}</div>
        <div class="item-subtotal">￥{{ ((item.dishPrice || item.price) * (item.num || item.quantity)).toFixed(2) }}</div>
        <div class="item-action">
          <button class="remove-btn" @click="removeItem(item)">删除</button>
        </div>
      </div>
      <div class="total">
        <div class="total-left">
          <button class="batch-remove" @click="batchRemove" v-if="selectedItems.length > 0">
            删除选中 ({{ selectedItems.length }})
          </button>
        </div>
        <div class="total-right">
          <div class="total-amount">
            <span>合计：</span>
            <span class="amount gradient-text">{{ cartData.selectedAmount || totalPrice.toFixed(2) }}</span>
          </div>
          <button class="btn-primary" @click="checkout" :disabled="selectedItems.length === 0">
            去结算 ({{ cartData.selectedItems || selectedItems.length }})
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { cartAPI, orderAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { resolveImageUrl } from '@/utils/image'

const userStore = useUserStore()
const router = useRouter()
const cartData = ref(null)
const allSelected = ref(false)

// 计算属性：选中的商品
const selectedItems = computed(() => {
  if (!cartData.value || !cartData.value.items) return []
  return cartData.value.items.filter(item => item.selected)
})

// 计算属性：总价格
const totalPrice = computed(() => {
  if (!cartData.value || !cartData.value.items) return 0
  return cartData.value.items
    .filter(item => item.selected)
    .reduce((sum, item) => sum + (item.dishPrice || item.price) * (item.num || item.quantity), 0)
})

// 加载购物车数据
const loadCart = async () => {
  try {
    const res = await cartAPI.getCart(userStore.userId)
    if (res.data.code === 200) {
      // 将后端返回的购物车数据转换为前端需要的格式
      cartData.value = {
        items: res.data.data.map(item => ({
          cartId: item.cartId,
          dishId: item.dishId,
          dishName: item.dishName,
          dishPrice: item.dishPrice,
          dishImage: item.dishImage,
          quantity: item.quantity,
          num: item.quantity, // 兼容旧字段
          price: item.dishPrice, // 兼容旧字段
          image: item.dishImage, // 兼容旧字段
          name: item.dishName, // 兼容旧字段
          remark: item.remark || '',
          selected: false // 前端选中状态
        }))
      }
      // 检查是否全选
      checkAllSelected()
    }
  } catch (error) {
    console.error('加载购物车失败:', error)
    cartData.value = { items: [] }
  }
}

// 检查是否全选
const checkAllSelected = () => {
  if (!cartData.value || !cartData.value.items || cartData.value.items.length === 0) {
    allSelected.value = false
    return
  }
  allSelected.value = cartData.value.items.every(item => item.selected)
}

// 全选/取消全选
const handleSelectAll = () => {
  if (!cartData.value || !cartData.value.items) return
  cartData.value.items.forEach(item => {
    item.selected = allSelected.value
  })
}

// 单个商品选中状态变更
const handleItemSelection = (item) => {
  // 前端本地处理选中状态，不调用后端API
  checkAllSelected()
}

// 更新商品数量
const updateQuantity = async (item, delta) => {
  const newQty = (item.num || item.quantity) + delta
  if (newQty <= 0) {
    await removeItem(item)
  } else {
    try {
      // 尝试从多个可能的字段中获取ID
      const cartId = parseInt(item.cartId)
      if (isNaN(cartId)) {
        console.error('无效的商品ID:', item)
        alert('商品信息有误，请刷新页面重试')
        return
      }
      await cartAPI.updateQuantity(cartId, newQty, item.remark)
      await loadCart()
    } catch (error) {
      console.error('更新数量失败:', error)
    }
  }
}

// 删除商品
const removeItem = async (item) => {
  try {
    // 尝试从多个可能的字段中获取ID
    const cartId = parseInt(item.cartId || item.id || item.dishId)
    if (isNaN(cartId)) {
      console.error('无效的商品ID:', item)
      alert('商品信息有误，请刷新页面重试')
      return
    }
    await cartAPI.removeItem(cartId)
    await loadCart()
  } catch (error) {
    console.error('删除商品失败:', error)
  }
}

// 批量删除
const batchRemove = async () => {
  if (selectedItems.value.length === 0) return
  
  // 显示确认对话框
  if (!confirm(`确定要删除选中的 ${selectedItems.value.length} 件商品吗？`)) {
    return
  }
  
  try {
    const cartIds = selectedItems.value.map(item => {
      // 尝试从多个可能的字段中获取ID
      const id = parseInt(item.cartId || item.id || item.dishId)
      if (isNaN(id)) {
        console.error('无效的商品ID:', item)
        return null
      }
      return id
    }).filter(id => id !== null)
    
    if (cartIds.length === 0) {
      alert('选中的商品信息有误，请刷新页面重试')
      return
    }
    
    const res = await cartAPI.batchRemove(cartIds)
    if (res.data.code === 200) {
      alert(`成功删除 ${selectedItems.value.length} 件商品`)
      await loadCart()
    } else {
      alert('删除失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('批量删除失败:', error)
    alert('删除失败，请重试')
  }
}

// 清空购物车
const clearCart = async () => {
  try {
    await cartAPI.clearCart(userStore.userId)
    await loadCart()
  } catch (error) {
    console.error('清空购物车失败:', error)
  }
}

// 结算
const checkout = async () => {
  if (selectedItems.value.length === 0) {
    alert('请选择要结算的商品')
    return
  }
  
  // 显示结算确认对话框
  const totalAmount = cartData.value.selectedAmount || totalPrice.value
  if (!confirm(`确定要结算 ${selectedItems.value.length} 件商品吗？\n总计：￥${totalAmount.toFixed(2)}`)) {
    return
  }
  
  try {
    // 获取选中的购物车商品ID
    const cartIds = selectedItems.value.map(item => {
      const id = parseInt(item.cartId || item.id || item.dishId)
      if (isNaN(id)) {
        console.error('无效的商品ID:', item)
        return null
      }
      return id
    }).filter(id => id !== null)
    
    if (cartIds.length === 0) {
      alert('选中的商品信息有误，请刷新页面重试')
      return
    }
    
    // 使用新的批量结算API
    const checkoutRes = await cartAPI.batchCheckout(userStore.userId, cartIds)
    
    if (checkoutRes.data.code === 200) {
      // 结算成功，清空购物车
      await cartAPI.clearCart(userStore.userId)
      
      alert('结算成功！订单已生成')
      router.push('/orders')
    } else {
      alert('结算失败：' + (checkoutRes.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('结算失败:', error)
    alert('结算失败，请重试')
  }
}

onMounted(() => {
  loadCart()
})
</script>

<style scoped>
.cart-view {
  border-radius: 24px;
  padding: 32px;
  box-shadow: var(--shadow-glass);
  transition: all 0.3s ease;
  margin: 24px 8px;
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.78));
}

.cart-view:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.cart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
}

.cart-header h3 {
  font-size: 24px;
  font-weight: 700;
  margin: 0;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end), var(--color-gradient-accent));
  background-clip: text;
  color: transparent;
}

.gradient-text {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  background-clip: text;
  color: transparent;
}

.clear-btn {
  background: none;
  border: 1px solid var(--color-border);
  color: var(--color-danger);
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.clear-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: var(--color-danger);
  transform: translateY(-1px);
}

.cart-header-row {
  display: grid;
  grid-template-columns: 80px 1fr 100px 100px 120px 80px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-sub);
  background: rgba(244, 245, 251, 0.85);
  backdrop-filter: blur(8px);
  border-radius: 12px 12px 0 0;
  margin-bottom: 8px;
}

.cart-item {
  display: grid;
  grid-template-columns: 80px 1fr 100px 100px 120px 80px;
  align-items: center;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border);
  transition: all 0.2s ease;
  border-radius: 12px;
  margin-bottom: 8px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
}

.cart-item:hover {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.item-checkbox {
  padding-left: 16px;
}

.custom-checkbox {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: var(--color-primary);
}

.item-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.item-image {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease;
}

.item-image:hover {
  transform: scale(1.05);
  box-shadow: var(--shadow-md);
}

.info-text {
  flex: 1;
}

.info-text h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-main);
}

.remark {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-muted);
  background: rgba(0, 0, 0, 0.05);
  padding: 4px 12px;
  border-radius: 12px;
  display: inline-block;
}

.item-quantity {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
}

.quantity-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  border-radius: 50%;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quantity-btn:hover {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
  transform: scale(1.1);
  box-shadow: 0 0 12px rgba(67, 56, 202, 0.28);
}

.quantity-value {
  min-width: 48px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-main);
  background: rgba(255, 255, 255, 0.9);
  padding: 6px 12px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

.item-price, .item-subtotal {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-main);
}

.item-subtotal {
  color: var(--color-accent);
  font-weight: 700;
}

.item-action {
  text-align: center;
}

.remove-btn {
  background: none;
  border: 1px solid var(--color-border);
  color: var(--color-danger);
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.remove-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: var(--color-danger);
  transform: translateY(-1px);
}

.total {
  margin-top: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-top: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border-radius: 16px;
}

.total-left {
  flex: 1;
}

.batch-remove {
  background: none;
  border: 1px solid var(--color-border);
  color: var(--color-danger);
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.batch-remove:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: var(--color-danger);
  transform: translateY(-1px);
}

.total-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.total-amount {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-main);
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.amount {
  font-size: 24px;
  font-weight: 700;
}

.btn-primary {
  padding: 12px 32px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(67, 56, 202, 0.28);
  filter: brightness(1.05);
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
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
  margin-top: 16px;
}

.btn-secondary:hover {
  background: var(--color-primary);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(59, 47, 127, 0.3);
}

.empty {
  text-align: center;
  padding: 80px 40px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.7);
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
  font-size: 18px;
  font-weight: 500;
  color: var(--color-text-sub);
  margin-bottom: 8px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .cart-view {
    padding: 24px;
    margin: 16px;
  }
  
  .cart-header-row {
    grid-template-columns: 60px 1fr 80px 80px 100px 60px;
    font-size: 12px;
  }
  
  .cart-item {
    grid-template-columns: 60px 1fr 80px 80px 100px 60px;
    padding: 16px 0;
  }
  
  .item-image {
    width: 80px;
    height: 80px;
  }
  
  .info-text h4 {
    font-size: 14px;
  }
  
  .quantity-btn {
    width: 28px;
    height: 28px;
  }
  
  .quantity-value {
    min-width: 40px;
    font-size: 14px;
  }
  
  .item-price, .item-subtotal {
    font-size: 14px;
  }
  
  .total {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  
  .total-right {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  
  .btn-primary {
    width: 100%;
    text-align: center;
  }
  
  .empty {
    padding: 60px 30px;
  }
  
  .empty-icon {
    font-size: 48px;
  }
}
</style>