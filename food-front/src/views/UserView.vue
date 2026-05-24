<template>
  <div class="user-view">
    <div class="card">
      <h3 class="gradient-text">个人信息与偏好</h3>
      <div class="info-item"><span class="label">昵称：</span><span>{{ userInfo?.nickname || userStore.nickname }}</span></div>
      <div class="info-item"><span class="label">手机号：</span><span>{{ userInfo?.phone || userStore.phone }}</span></div>
      <div class="info-item"><span class="label">账户状态：</span><span :class="['status', userInfo?.status === 1 ? 'ok' : 'ban']">{{ userInfo?.status === 1 ? '正常' : '禁用' }}</span></div>

      <div class="info-item"><span class="label">口味偏好</span><input v-model="prefInput" @input="handlePrefInput" class="input" placeholder="如：辣,酸,甜" /></div>
      <div class="info-item"><span class="label">食材偏好</span><input v-model="ingredInput" @input="handleIngredInput" class="input" placeholder="如：鸡肉,牛肉" /></div>
      <div class="info-item"><span class="label">饮食忌口</span><input v-model="restInput" @input="handleRestInput" class="input" placeholder="如：海鲜,花生" /></div>

      <div class="actions">
        <button class="btn-primary" @click="savePreferences">保存</button>
        <button class="btn-secondary" @click="resetPreferences">重置</button>
      </div>
    </div>

    <div class="card">
      <h3 class="gradient-text">偏好画像</h3>
      <div class="block">
        <div class="btitle">口味</div>
        <div class="pref-row"><div class="pref-label">历史偏好</div><div class="tags"><span class="tag" v-for="t in savedPrefs" :key="`saved-t-${t}`">{{ t }}</span><span v-if="!savedPrefs.length" class="muted">暂无</span></div></div>
        <div class="pref-row"><div class="pref-label">临时偏好</div><div class="tags"><span class="tag temp" v-for="t in selectedPrefs" :key="`temp-t-${t}`">{{ t }}</span><span v-if="!selectedPrefs.length" class="muted">暂无</span></div></div>
      </div>
      <div class="block">
        <div class="btitle">食材</div>
        <div class="pref-row"><div class="pref-label">历史偏好</div><div class="tags"><span class="tag" v-for="i in savedIngres" :key="`saved-i-${i}`">{{ i }}</span><span v-if="!savedIngres.length" class="muted">暂无</span></div></div>
        <div class="pref-row"><div class="pref-label">临时偏好</div><div class="tags"><span class="tag temp" v-for="i in selectedIngres" :key="`temp-i-${i}`">{{ i }}</span><span v-if="!selectedIngres.length" class="muted">暂无</span></div></div>
      </div>
      <div class="block">
        <div class="btitle">忌口</div>
        <div class="pref-row"><div class="pref-label">历史偏好</div><div class="tags"><span class="tag warn" v-for="r in savedRests" :key="`saved-r-${r}`">{{ r }}</span><span v-if="!savedRests.length" class="muted">暂无</span></div></div>
        <div class="pref-row"><div class="pref-label">临时偏好</div><div class="tags"><span class="tag warn temp" v-for="r in selectedRests" :key="`temp-r-${r}`">{{ r }}</span><span v-if="!selectedRests.length" class="muted">暂无</span></div></div>
      </div>
      <div class="summary-row">
        <div class="summary"><div class="slabel">完整度</div><div class="sval gradient-text">{{ profileCompleteness }}%</div></div>
        <div class="summary"><div class="slabel">主偏好</div><div class="sval">{{ primaryTaste }}</div></div>
        <div class="summary"><div class="slabel">编辑状态</div><div class="sval" :class="{ 'gradient-text': hasUnsavedChanges }">{{ hasUnsavedChanges ? '有未保存改动' : '已与历史一致' }}</div></div>
      </div>
    </div>

    <div class="card full">
      <div class="card-title-row">
        <div>
          <h3 class="gradient-text">营养趋势（近7天）</h3>
          <div class="source">数据来源：{{ aiNutritionTrend.length ? 'AI 分析' : (aiError ? 'AI 不可用，本地估算' : '本地估算（兜底）') }}</div>
        </div>
        <button class="refresh-trend-btn" :disabled="nutritionLoading" @click="refreshNutritionTrend">
          {{ nutritionLoading ? '刷新中...' : '刷新趋势' }}
        </button>
      </div>
      <div class="chart-wrap">
        <div ref="nutritionChartRef" class="chart"></div>
        <div v-if="nutritionLoading" class="chart-loading">
          <div class="loading-spinner"></div>
          <div class="loading-text">DeepSeek 正在分析营养数据...</div>
        </div>
        <div v-else-if="!hasTrendData && orders.length > 0" class="chart-empty">近7天无可用于分析的已支付订单</div>
        <div v-else-if="!hasTrendData && orders.length === 0" class="chart-empty">暂无订单数据</div>
      </div>
      <div class="summary-row four">
        <div class="summary"><div class="slabel">平均热量</div><div class="sval">{{ avgCalories }} kcal</div></div>
        <div class="summary"><div class="slabel">平均蛋白</div><div class="sval">{{ avgProtein }} g</div></div>
        <div class="summary"><div class="slabel">平均脂肪</div><div class="sval">{{ avgFat }} g</div></div>
        <div class="summary"><div class="slabel">平均碳水</div><div class="sval">{{ avgCarbs }} g</div></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/stores/user'
import { dishAPI, orderAPI, preferenceAPI, userAPI } from '@/api'
const userStore = useUserStore()



const userInfo = ref(null)
const userPreference = ref(null)
const redisHistoryPreference = ref({ taste: '', ingredient: '', taboo: '' })
const orders = ref([])
const dishList = ref([])
const aiNutritionTrend = ref([])
const nutritionLoading = ref(false)
const aiError = ref(false)

const prefInput = ref('')
const ingredInput = ref('')
const restInput = ref('')
const selectedPrefs = ref([])
const selectedRests = ref([])
const selectedIngres = ref([])

const nutritionChartRef = ref(null)
let nutritionChart = null

const dishMap = computed(() => {
  const m = new Map()
  dishList.value.forEach(d => m.set(Number(d.dishId || d.id), d))
  return m
})

const parseInput = v => v.split(/[,，\s]+/).map(s => s.trim()).filter(Boolean).filter((x, i, a) => a.indexOf(x) === i)
const handlePrefInput = () => { selectedPrefs.value = parseInput(prefInput.value) }
const handleIngredInput = () => { selectedIngres.value = parseInput(ingredInput.value) }
const handleRestInput = () => { selectedRests.value = parseInput(restInput.value) }

const savedPrefs = computed(() => (redisHistoryPreference.value.taste || '').split(',').map(i => i.trim()).filter(Boolean))
const savedIngres = computed(() => (redisHistoryPreference.value.ingredient || '').split(',').map(i => i.trim()).filter(Boolean))
const savedRests = computed(() => (redisHistoryPreference.value.taboo || '').split(',').map(i => i.trim()).filter(Boolean))
const normalize = (arr) => [...arr].sort().join('|')
const hasUnsavedChanges = computed(() => (
  normalize(savedPrefs.value) !== normalize(selectedPrefs.value)
  || normalize(savedIngres.value) !== normalize(selectedIngres.value)
  || normalize(savedRests.value) !== normalize(selectedRests.value)
))

const profileCompleteness = computed(() => {
  let n = 0
  if (selectedPrefs.value.length) n++
  if (selectedIngres.value.length) n++
  if (selectedRests.value.length) n++
  return Math.round((n / 3) * 100)
})
const primaryTaste = computed(() => selectedPrefs.value[0] || '未设置')

const localDateStr = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const parseDateStr = (s) => (s || '').length >= 10 ? (s || '').slice(0, 10) : ''

const localTrend = computed(() => {
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (6 - i))
    return localDateStr(d)
  })
  const grouped = new Map(days.map(d => [d, { calories: 0, protein: 0, fat: 0, carbs: 0 }]))

  orders.value.forEach(order => {
    // 仅统计已支付/已完成订单
    if (order.status !== 2 && order.status !== 3) return
    const date = parseDateStr(order.createTime)
    if (!grouped.has(date)) return
    const b = grouped.get(date)
    ;(order.items || []).forEach(it => {
      const dish = dishMap.value.get(Number(it.dishId))
      const qty = Number(it.quantity || it.num || 1)
      // dishMap查不到则直接用订单明细中的价格
      const price = Number(dish?.price ?? it.price ?? 0)
      b.calories += price * 8 * qty
      b.protein += price * 0.5 * qty
      b.fat += price * 0.3 * qty
      b.carbs += price * 1.1 * qty
    })
  })
  const trendResult = days.map(d => ({ date: d.slice(5), ...grouped.get(d) }))
  console.log('[营养趋势] localTrend:', JSON.stringify(trendResult))
  return trendResult
})

const nutritionTrend = computed(() => {
  // 始终读 localTrend 保持其新鲜（即使优先用 AI）
  const local = localTrend.value
  const ai = aiNutritionTrend.value
  if (!ai || !ai.length) return local
  // AI 有实际数据才用，否则回退本地估算
  const aiHasData = ai.some(d => Number(d.calories) > 0 || Number(d.protein) > 0 || Number(d.fat) > 0 || Number(d.carbs) > 0)
  return aiHasData ? ai : local
})

const avgBy = key => {
  const arr = nutritionTrend.value
  if (!arr.length) return '0.0'
  return (arr.reduce((s, i) => s + Number(i[key] || 0), 0) / arr.length).toFixed(1)
}
const avgCalories = computed(() => avgBy('calories'))
const avgProtein = computed(() => avgBy('protein'))
const avgFat = computed(() => avgBy('fat'))
const avgCarbs = computed(() => avgBy('carbs'))

const hasTrendData = computed(() => {
  return nutritionTrend.value.some(d => Number(d.calories) > 0 || Number(d.protein) > 0 || Number(d.fat) > 0 || Number(d.carbs) > 0)
})

const renderChart = () => {
  if (!nutritionChartRef.value) return
  if (!nutritionChart) nutritionChart = echarts.init(nutritionChartRef.value)
  const x = nutritionTrend.value.map(i => i.date)
  nutritionChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['热量', '蛋白', '脂肪', '碳水'] },
    grid: { left: 30, right: 20, top: 36, bottom: 20 },
    xAxis: { type: 'category', data: x },
    yAxis: { type: 'value' },
    series: [
      { name: '热量', type: 'line', smooth: true, data: nutritionTrend.value.map(i => Number(i.calories || 0)) },
      { name: '蛋白', type: 'line', smooth: true, data: nutritionTrend.value.map(i => Number(i.protein || 0)) },
      { name: '脂肪', type: 'line', smooth: true, data: nutritionTrend.value.map(i => Number(i.fat || 0)) },
      { name: '碳水', type: 'line', smooth: true, data: nutritionTrend.value.map(i => Number(i.carbs || 0)) }
    ]
  })
}

const loadDishes = async () => {
  try {
    const res = await dishAPI.getAll()
    if (res.data.code === 200 && Array.isArray(res.data.data)) {
      dishList.value = res.data.data
      console.log('[营养趋势] 菜品加载成功:', dishList.value.length, '道菜')
    } else {
      console.warn('[营养趋势] 菜品接口返回异常:', res.data)
    }
  } catch (e) {
    console.warn('[营养趋势] 菜品接口请求失败，将使用订单明细价格:', e.message)
    // dishList保持空，localTrend会用订单明细价格兜底
  }
}

const loadOrders = async () => {
  try {
    const res = await orderAPI.getOrdersByUser(userStore.userId)
    if (res.data.code === 200) {
      const orderList = res.data.data || []
      // 只获取近7天订单的明细，减少API请求
      const sevenDaysAgo = new Date()
      sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 6)
      const todayStr = localDateStr(new Date())
      const sevenDaysAgoStr = localDateStr(sevenDaysAgo)
      const recentOrders = orderList.filter(o => {
        // createTime 格式: "yyyy-MM-dd HH:mm:ss"，取前10字符比较日期
        const dateStr = parseDateStr(o.createTime)
        return dateStr >= sevenDaysAgoStr && dateStr <= todayStr
      })
      for (const order of recentOrders) {
        try {
          const detailRes = await orderAPI.getOrderDetails(order.orderId)
          if (detailRes.data.code === 200) {
            order._items = (detailRes.data.data || []).map(detail => ({
              dishId: detail.dishId,
              dishName: detail.dishName,
              price: detail.price,
              quantity: detail.num,
              remark: detail.remark || ''
            }))
          } else {
            order._items = []
          }
        } catch {
          order._items = []
        }
      }
      orders.value = recentOrders.map(o => ({ createTime: o.createTime, status: o.status, items: o._items || [] }))
      console.log('[营养趋势] 近7天订单:', orders.value.length, '条,',
        '已支付:', orders.value.filter(o => o.status === 2 || o.status === 3).length, '条',
        '含明细:', orders.value.filter(o => (o.items || []).length > 0).length, '条',
        '明细含有效价格:', orders.value.filter(o => (o.items || []).some(it => Number(it.price) > 0)).length, '条')
      if (orders.value.length > 0) {
        console.log('[营养趋势] 订单详情:', JSON.stringify(orders.value.map(o => ({
          date: parseDateStr(o.createTime), status: o.status, itemCount: (o.items || []).length,
          totalPrice: (o.items || []).reduce((s, it) => s + Number(it.price || 0), 0)
        }))))
      }
    }
  } catch {
    orders.value = []
  }
}

const loadAINutrition = async () => {
  nutritionLoading.value = true
  try {
    const res = await orderAPI.getNutritionTrendAI(userStore.userId)
    if (res.data.code === 200 && res.data.data?.trend) {
      aiNutritionTrend.value = (res.data.data.trend || []).map(i => ({
        date: i.date,
        calories: Number(i.calories || 0),
        protein: Number(i.protein || 0),
        fat: Number(i.fat || 0),
        carbs: Number(i.carbs || 0)
      }))
      aiError.value = false
    } else {
      aiNutritionTrend.value = []
      aiError.value = true
    }
  } catch {
    aiNutritionTrend.value = []
    aiError.value = true
  } finally {
    nutritionLoading.value = false
    await nextTick()
    renderChart()
    nutritionChart?.resize()
  }
}

const refreshNutritionTrend = async () => {
  nutritionLoading.value = true
  try {
    await orderAPI.clearNutritionTrendAICache(userStore.userId)
    await loadOrders()
    await loadAINutrition()
  } catch (error) {
    console.error('刷新营养趋势失败:', error)
    await loadAINutrition()
  }
}

const loadRedisHistoryPreference = async () => {

  if (!userStore.userId) {
    redisHistoryPreference.value = { taste: '', ingredient: '', taboo: '' }
    return
  }
  try {
    const res = await preferenceAPI.getHistoryPreference(userStore.userId)
    if (res.data.code === 200 && res.data.data) {
      redisHistoryPreference.value = {
        taste: res.data.data.taste || '',
        ingredient: res.data.data.ingredient || '',
        taboo: res.data.data.taboo || ''
      }
    } else {
      redisHistoryPreference.value = { taste: '', ingredient: '', taboo: '' }
    }
  } catch {
    redisHistoryPreference.value = { taste: '', ingredient: '', taboo: '' }
  }
}

const loadUserData = async () => {
  const userRes = await userAPI.getUserInfo(userStore.userId)
  if (userRes.data.code === 200) userInfo.value = userRes.data.data
  const prefRes = await preferenceAPI.getPreference(userStore.userId)
  if (prefRes.data.code === 200 && prefRes.data.data) {
    userPreference.value = prefRes.data.data
    selectedPrefs.value = JSON.parse(userPreference.value.taste || '[]')
    selectedIngres.value = JSON.parse(userPreference.value.ingredient || '[]')
    selectedRests.value = JSON.parse(userPreference.value.taboo || '[]')
    prefInput.value = selectedPrefs.value.join('，')
    ingredInput.value = selectedIngres.value.join('，')
    restInput.value = selectedRests.value.join('，')
  }
}

const savePreferences = async () => {
  const payload = { userId: userStore.userId, taste: JSON.stringify(selectedPrefs.value), ingredient: JSON.stringify(selectedIngres.value), taboo: JSON.stringify(selectedRests.value) }
  const res = await preferenceAPI.savePreference(payload)
  if (res.data.code === 200) await loadUserData()
}

const resetPreferences = () => {
  selectedPrefs.value = JSON.parse(userPreference.value?.taste || '[]')
  selectedIngres.value = JSON.parse(userPreference.value?.ingredient || '[]')
  selectedRests.value = JSON.parse(userPreference.value?.taboo || '[]')
  prefInput.value = selectedPrefs.value.join('，')
  ingredInput.value = selectedIngres.value.join('，')
  restInput.value = selectedRests.value.join('，')
}

onMounted(async () => {
  await Promise.all([loadUserData(), loadDishes(), loadOrders(), loadAINutrition(), loadRedisHistoryPreference()])
  await nextTick()
  renderChart()
  window.addEventListener('resize', () => nutritionChart?.resize())
})

onBeforeUnmount(() => {
  nutritionChart?.dispose()
  nutritionChart = null
})
</script>

<style scoped>
.user-view{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin:24px 8px}.full{grid-column:1/-1}
.card{border-radius:18px;padding:20px;background:rgba(255,255,255,.78);border:1px solid var(--color-border)}
.gradient-text{background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end),var(--color-gradient-accent));background-clip:text;color:transparent}
.info-item{margin-bottom:12px}.label{font-weight:600;font-size:14px}.input{width:100%;margin-top:6px;padding:10px;border:1px solid var(--color-border);border-radius:10px}
.actions{display:flex;gap:10px}.btn-primary,.btn-secondary{padding:8px 14px;border-radius:20px;cursor:pointer}.btn-primary{border:none;color:#fff;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end))}.btn-secondary{border:1px solid var(--color-border);background:#fff}
.status{padding:2px 8px;border-radius:10px}.ok{background:rgba(92,184,92,.12);color:#4caf50}.ban{background:rgba(217,83,79,.12);color:#d9534f}
.block{padding:10px;border:1px solid var(--color-border);border-radius:10px;margin-bottom:10px}.btitle{font-weight:600;margin-bottom:6px}.pref-row{display:flex;gap:10px;align-items:flex-start;margin-top:8px}.pref-label{min-width:72px;font-size:12px;color:var(--color-text-sub);padding-top:6px}.tags{display:flex;flex-wrap:wrap;gap:8px}.tag{padding:4px 10px;border-radius:999px;background:rgba(67,56,202,.1)}.tag.temp{border:1px dashed rgba(67,56,202,.45);background:rgba(99,102,241,.12)}.warn{background:rgba(217,83,79,.12)}.muted{color:var(--color-text-muted);font-size:12px}
.summary-row{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:10px}.summary-row.four{grid-template-columns:repeat(4,minmax(0,1fr))}.summary{padding:10px;border:1px solid var(--color-border);border-radius:10px;background:#fff}.slabel{font-size:12px;color:var(--color-text-sub)}.sval{font-weight:700}
.source{font-size:12px;color:var(--color-text-sub);margin-bottom:8px}.chart-wrap{position:relative}.chart{height:340px}.chart-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:var(--color-text-muted);font-size:14px}.chart-loading{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(255,255,255,.88);backdrop-filter:blur(2px);border-radius:12px}.loading-spinner{width:40px;height:40px;border:3px solid rgba(99,102,241,.2);border-top-color:#6366f1;border-radius:50%;animation:spin 1s linear infinite}.loading-text{margin-top:10px;font-size:13px;color:var(--color-text-sub)}@keyframes spin{to{transform:rotate(360deg)}}
@media (max-width:900px){.user-view{grid-template-columns:1fr}.summary-row{grid-template-columns:1fr}.summary-row.four{grid-template-columns:1fr 1fr}.pref-row{flex-direction:column;gap:6px}.pref-label{min-width:auto;padding-top:0}}
.card-title-row{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:8px}.card-title-row h3{margin:0 0 6px}.refresh-trend-btn{border:none;border-radius:999px;padding:8px 14px;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end));color:#fff;font-weight:800;cursor:pointer;box-shadow:0 8px 18px rgba(59,47,127,.22);transition:.2s}.refresh-trend-btn:hover:not(:disabled){transform:translateY(-2px);box-shadow:0 12px 24px rgba(59,47,127,.28)}.refresh-trend-btn:disabled{opacity:.65;cursor:not-allowed}@media(max-width:640px){.card-title-row{flex-direction:column}.refresh-trend-btn{width:100%}}</style>
