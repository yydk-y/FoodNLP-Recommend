<template>
  <div class="admin-layout">
    <aside class="sidebar glass">
      <div class="logo gradient-text">后台管理</div>
      <ul>
        <li><router-link to="/admin/dashboard" class="sidebar-link">数据统计</router-link></li>
        <li><router-link to="/admin/dishes" class="sidebar-link">菜品管理</router-link></li>
        <li><router-link to="/admin/users" class="sidebar-link">用户管理</router-link></li>
        <li><router-link to="/admin/orders" class="sidebar-link">订单管理</router-link></li>
        <li><a href="#" @click.prevent="backToFront" class="sidebar-link">返回前台</a></li>
      </ul>
    </aside>
    <div class="main-content">
      <header class="glass">
        <span>欢迎，管理员</span>
        <button class="btn-secondary" @click="logout">退出登录</button>
      </header>
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const backToFront = () => {
  router.push('/')
}
const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg-page);
  gap: 18px;
  padding: 18px;
}
.sidebar {
  width: 250px;
  padding: 24px 0;
  margin: 0;
  border-radius: 20px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0.68));
  backdrop-filter: blur(16px);
  border: 1px solid var(--color-border-light);
  box-shadow: var(--shadow-glass);
}
.sidebar .logo {
  font-size: 22px;
  text-align: center;
  margin-bottom: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--color-gradient-start) 0%, var(--color-gradient-end) 58%, var(--color-gradient-accent) 100%);
  background-clip: text;
  color: transparent;
}
.sidebar ul {
  list-style: none;
  padding: 0;
}
.sidebar-link {
  display: block;
  padding: 12px 16px;
  color: var(--color-text-main);
  text-decoration: none;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
  border-radius: 10px;
  margin: 8px 16px;
}
.sidebar-link:hover, .sidebar-link.router-link-active {
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  transform: translateX(4px);
  box-shadow: 0 8px 18px rgba(67, 56, 202, 0.28);
}
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin: 0;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
  background: rgba(255, 255, 255, 0.54);
  backdrop-filter: blur(8px);
}
.main-content header {
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.7);
}
.main-content header span {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-main);
}
.main-content > :last-child {
  padding: 24px;
  flex: 1;
  background: transparent;
}

@media (max-width: 980px) {
  .admin-layout {
    flex-direction: column;
    padding: 10px;
  }

  .sidebar {
    width: 100%;
    padding: 12px 0;
  }

  .sidebar ul {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 0 10px 10px;
  }

  .sidebar-link {
    margin: 0;
  }
}
</style>