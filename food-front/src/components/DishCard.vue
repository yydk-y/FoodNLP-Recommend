<template>
  <div class="dish-card" @click="goToDetail">
    <div class="image-container">
      <img :src="resolveImageUrl(dish.image)" :alt="dish.name" />
      <div v-if="dish.matchScore || dish.reason" class="ai-ribbon">AI 推荐</div>
      <div v-if="dish.matchScore" class="match-badge">{{ dish.matchScore }}% 匹配</div>
    </div>
    <div class="info">
      <div class="name-rating">
        <h4>{{ dish.name }}</h4>
        <div class="rating" v-if="dish.rating !== undefined && dish.rating !== null">
          <span class="stars">
            <span v-for="i in 5" :key="i" class="star" :class="{ active: i <= dish.rating }">★</span>
          </span>
          <span class="rating-text">{{ dish.rating.toFixed(1) }}</span>
        </div>
      </div>
      <div class="price">￥{{ dish.price }}</div>
      <p class="desc">{{ dish.description }}</p>
      <div v-if="dish.reason" class="reason-card"><strong>推荐理由</strong><span>{{ dish.reason }}</span></div>
      <div class="tags">
        <span v-for="tag in dish.tags" :key="tag">{{ tag }}</span>
      </div>
      <button class="btn-add" @click.stop="addToCart">
        <span class="btn-text">加入购物车</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { cartAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import { resolveImageUrl } from '@/utils/image'

const props = defineProps(['dish'])
const emit = defineEmits(['add-to-cart'])
const router = useRouter()
const userStore = useUserStore()

const goToDetail = () => {
  router.push(`/dish/${props.dish.id}`)
}

const addToCart = async () => {
  try {
    const cartItem = {
      userId: userStore.userId,
      dishId: parseInt(props.dish.id),
      quantity: 1,
      remark: '',
      dishName: props.dish.name,
      dishPrice: props.dish.price,
      dishImage: props.dish.image
    }
    await cartAPI.addItem(cartItem)
    emit('add-to-cart', cartItem)
  } catch (error) {
    console.error('添加到购物车失败:', error)
    alert('添加到购物车失败，请重试')
  }
}
</script>

<style scoped>
.dish-card {
  display: flex;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.8));
  border-radius: 22px;
  overflow: hidden;
  box-shadow: 0 10px 24px rgba(17, 24, 39, 0.08);
  margin-bottom: 20px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
  border: 1px solid var(--color-border);
  min-height: 170px;
}
.dish-card:hover {
  transform: translateY(-6px) scale(1.01);
  box-shadow: 0 18px 36px rgba(59, 47, 127, 0.18);
  border-color: rgba(59, 47, 127, 0.28);
}
.image-container {
  position: relative;
  width: 150px;
  height: 170px;
  overflow: hidden;
  border-radius: 22px 0 0 22px;
}
.dish-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s cubic-bezier(0.2, 0, 0, 1);
}
.dish-card:hover img {
  transform: scale(1.05);
}
.price {
  font-size: 1.45rem;
  font-weight: 700;
  margin: 8px 0 10px;
  background: linear-gradient(135deg, var(--color-accent), #ff6b35);
  background-clip: text;
  color: transparent;
  text-shadow: 0 2px 4px rgba(249, 115, 22, 0.2);
  transition: all 0.3s ease;
}

.dish-card:hover .price {
  transform: translateY(-2px);
  text-shadow: 0 4px 8px rgba(249, 115, 22, 0.3);
}
.info {
  flex: 1;
  padding: 16px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-rating {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

h4 {
  font-weight: 600;
  color: var(--color-text-main);
  margin: 0;
}

.rating {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stars {
  display: flex;
  gap: 2px;
}

.star {
  font-size: 14px;
  color: #ddd;
  transition: color 0.2s ease;
}

.star.active {
  color: #ffc107;
}

.rating-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-main);
  min-width: 30px;
  text-align: right;
}
.desc {
  font-size: 14px;
  color: var(--color-text-sub);
  margin-bottom: 12px;
  line-height: 1.4;
  height: 38px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  text-overflow: ellipsis;
}
.tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.tags span {
  background: rgba(59, 47, 127, 0.1);
  border: 1px solid rgba(59, 47, 127, 0.15);
  padding: 3px 10px;
  border-radius: 16px;
  font-size: 12px;
  color: var(--color-primary);
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
}
.tags span:hover {
  background: var(--color-primary);
  color: white;
  transform: scale(1.05);
}
.btn-add {
  position: static;
  align-self: flex-end;
  margin-top: auto;
  background: linear-gradient(135deg, var(--color-gradient-start), var(--color-gradient-end));
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 40px;
  cursor: pointer;
  font-weight: 500;
  font-size: 14px;
  transition: all 0.25s cubic-bezier(0.2, 0, 0, 1);
  box-shadow: 0 8px 20px rgba(59, 47, 127, 0.25);
}
.btn-add:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(59, 47, 127, 0.34);
  filter: brightness(1.05);
}

.ai-ribbon{position:absolute;top:10px;left:10px;padding:4px 8px;border-radius:999px;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end));color:#fff;font-size:11px;font-weight:800;box-shadow:0 8px 18px rgba(59,47,127,.28)}
.match-badge{position:absolute;left:10px;right:10px;bottom:10px;padding:5px 8px;border-radius:999px;background:rgba(15,23,42,.78);color:#fff;font-size:12px;font-weight:800;text-align:center;backdrop-filter:blur(8px)}
.reason-card{margin:2px 0 10px;padding:8px 10px;border-radius:12px;background:rgba(11,122,117,.08);border:1px solid rgba(11,122,117,.12);color:#0b615d;font-size:12px;display:grid;gap:2px}.reason-card strong{font-size:11px;color:#0f766e}.reason-card span{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}@media (max-width: 768px) {
  .dish-card {
    min-height: auto;
  }

  .image-container {
    width: 118px;
    height: 152px;
  }

  .btn-add {
    margin-top: 6px;
    width: 100%;
    justify-content: center;
    display: flex;
  }
}
.dish-card{align-items:stretch}.dish-card .image-container{height:auto!important;min-height:100%;align-self:stretch;flex:0 0 150px}.dish-card .image-container img{height:100%;min-height:100%;display:block}.dish-card .info{min-height:170px}.dish-card:has(.reason-card) .image-container{height:auto!important}.dish-card:has(.reason-card) .image-container img{height:100%}@media(max-width:768px){.dish-card .image-container{flex-basis:118px;height:auto!important}.dish-card .info{min-height:152px}}</style>