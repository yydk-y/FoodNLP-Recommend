<template>
  <div class="default-layout">
    <header class="header glass">
      <div class="logo gradient-text">智能点餐系统</div>
      <nav class="nav">
        <router-link to="/" class="nav-link" active-class="" exact-active-class="router-link-active">点餐</router-link>
        <router-link to="/recommend" class="nav-link">推荐</router-link>
        <router-link to="/cart" class="nav-link">购物车</router-link>
        <router-link to="/orders" class="nav-link">订单</router-link>
        <router-link to="/user" class="nav-link">我的</router-link>
        <a v-if="isAdmin" href="#" @click.prevent="goAdmin" class="nav-link">管理后台</a>
        <a href="#" @click.prevent="handleLogout" class="nav-link logout">退出</a>
      </nav>
    </header>
    <main class="main">
      <router-view />
    </main>
    <footer class="footer glass">
      <p>© 2025 智能点餐系统 | 享受美食，智慧生活</p>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { computed } from 'vue'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const goAdmin = () => {
  router.push('/admin')
}
const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.default-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.header {
  width: min(1320px, calc(100% - 40px));
  margin: 18px auto 0;
  padding: 0 24px;
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 14px;
  z-index: 100;
  border-radius: 20px;
}
.logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.3px;
  background: linear-gradient(135deg, var(--color-gradient-start) 0%, var(--color-gradient-end) 55%, var(--color-gradient-accent) 100%);
  background-clip: text;
  color: transparent;
}
.nav {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.nav-link {
  color: var(--color-text-main);
  text-decoration: none;
  font-weight: 500;
  position: relative;
  transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
  padding: 8px 10px;
  border-radius: 12px;
}
.nav-link:hover {
  color: var(--color-primary);
  background: rgba(59, 47, 127, 0.08);
}
.nav-link::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 2px;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  transition: width 0.3s cubic-bezier(0.2, 0, 0, 1);
  border-radius: 2px;
}
.nav-link:hover::after,
.nav-link.router-link-active::after {
  width: 100%;
}
.nav-link.router-link-active {
  color: var(--color-primary-strong);
  background: rgba(59, 47, 127, 0.1);
}
.logout {
  color: var(--color-danger);
}
.logout:hover {
  color: var(--color-danger);
  background: rgba(239, 68, 68, 0.1);
}
.main {
  flex: 1;
  padding: 30px 24px 40px;
  max-width: 1320px;
  margin: 0 auto;
  width: 100%;
}
.footer {
  text-align: center;
  padding: 20px;
  font-size: 14px;
  color: var(--color-text-muted);
  margin-top: auto;
}

@media (max-width: 980px) {
  .header {
    width: calc(100% - 24px);
    margin-top: 10px;
    padding: 10px 14px;
    height: auto;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .nav {
    width: 100%;
    gap: 8px;
    justify-content: flex-start;
  }

  .nav-link {
    padding: 6px 10px;
    font-size: 14px;
  }

  .main {
    padding: 22px 12px 26px;
  }
}
</style>