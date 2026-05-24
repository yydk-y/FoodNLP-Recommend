import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 布局组件
import DefaultLayout from '@/components/Layout/DefaultLayout.vue'
import AdminLayout from '@/components/Layout/AdminLayout.vue'

// 前台页面
import LoginView from '@/views/LoginView.vue'
import UserView from '@/views/UserView.vue'
import ChatView from '@/views/ChatView.vue'
import DishView from '@/views/DishView.vue'
import RecommendView from '@/views/RecommendView.vue'
import CartView from '@/views/CartView.vue'
import OrderView from '@/views/OrderView.vue'
import DishDetailView from '@/views/DishDetailView.vue'

// 后台管理页面
import Dashboard from '@/views/system/Dashboard.vue'
import DishManage from '@/views/system/DishManage.vue'
import UserManage from '@/views/system/UserManage.vue'
import OrderManage from '@/views/system/OrderManage.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: DefaultLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Home', component: DishView },
      { path: 'user', name: 'User', component: UserView },
      { path: 'chat', name: 'Chat', component: ChatView },
      { path: 'dishes', name: 'Dishes', component: DishView },
      { path: 'recommend', name: 'Recommend', component: RecommendView },
      { path: 'cart', name: 'Cart', component: CartView },
      { path: 'orders', name: 'Orders', component: OrderView },
      { path: 'dish/:id', name: 'DishDetail', component: DishDetailView }
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: Dashboard },
      { path: 'dishes', name: 'DishManage', component: DishManage },
      { path: 'users', name: 'UserManage', component: UserManage },
      { path: 'orders', name: 'OrderManage', component: OrderManage }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isLoggedIn = userStore.isLoggedIn
  
  // 1. 检查是否需要认证
  if (to.meta.requiresAuth && !isLoggedIn) {
    // 保存目标路由，登录后可以跳转回来
    next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
    return
  }
  
  // 2. 检查是否需要管理员权限
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    // 非管理员访问管理页面，重定向到首页并提示
    alert('您没有权限访问管理页面')
    next('/')
    return
  }
  
  // 3. 已登录用户访问登录页，重定向到首页
  if (to.path === '/login' && isLoggedIn) {
    next('/')
    return
  }
  
  // 4. 正常放行
  next()
})

export default router