<template>
  <div class="dashboard card">
    <div class="view-header">
      <div>
        <h3 class="page-title">数据统计面板</h3>
        <p class="page-subtitle">实时追踪用户活跃、订单趋势和销售表现</p>
      </div>
      <div class="view-chip">运营总览</div>
    </div>

    <div class="stats-overview">
      <div class="stat-card card" v-for="i in statCards" :key="i.title">
        <div class="stat-icon">{{ i.icon }}</div>
        <div class="stat-content"><div class="title">{{ i.title }}</div><div class="value" :class="{ 'gradient-text': i.highlight }">{{ i.value }}</div></div>
      </div>
    </div>

    <div class="panel card">
      <div class="section-header">
        <div>
          <h4>热销菜品数据图</h4>
          <p class="section-subtitle">支持本周/本月切换（ECharts）</p>
        </div>
        <div class="section-actions">
          <div class="time-toggle">
            <button class="toggle-btn" :class="{ active: activePeriod === 'week' }" @click="activePeriod='week'">本周</button>
            <button class="toggle-btn" :class="{ active: activePeriod === 'month' }" @click="activePeriod='month'">本月</button>
          </div>
          <button class="refresh-btn" @click="loadStatistics">刷新数据</button>
        </div>
      </div>
      <div v-if="currentHotDishes.length" class="chart-grid">
        <div class="chart-panel"><div class="chart-title">{{ activePeriodLabel }}热销菜品 Top5（按销量）</div><div ref="hotDishChartRef" class="echart"></div></div>
        <div class="summary-panel">
          <div class="summary-card"><div class="label">当前榜首</div><div class="value">{{ currentHotDishes[0]?.dishName || '--' }}</div></div>
          <div class="summary-card"><div class="label">累计销量</div><div class="value">{{ totalSalesCount }}</div></div>
          <div class="summary-card"><div class="label">累计销售额</div><div class="value">￥{{ fmt(totalSalesAmount) }}</div></div>
          <div class="summary-card highlight"><div class="label">统计周期</div><div class="value">{{ activePeriodLabel }}</div></div>
        </div>
      </div>
      <div v-else class="empty">暂无{{ activePeriodLabel }}热销菜品数据</div>
    </div>

    <div class="metrics-grid">
      <div class="chart-panel card"><div class="chart-title">近7天销售额与订单数趋势</div><div ref="salesTrendRef" class="echart"></div></div>
      <div class="chart-panel card"><div class="chart-title">近7天客单价趋势</div><div ref="avgTrendRef" class="echart"></div></div>
      <div class="chart-panel card full">
        <div class="chart-title">复购率（按下单用户）</div>
        <div class="repurchase">
          <div class="box"><div class="label">7日复购率</div><div class="value gradient-text">{{ repurchaseRate7d.toFixed(2) }}%</div></div>
          <div class="box"><div class="label">30日复购率</div><div class="value gradient-text">{{ repurchaseRate30d.toFixed(2) }}%</div></div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-overlay"><div class="loading-spinner"></div><div class="loading-text">数据加载中...</div></div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { statisticsAPI } from '@/api'

const stats = ref({})
const hotDishesByPeriod = ref({ week: [], month: [] })
const activePeriod = ref('week')
const loading = ref(false)

const salesOrderTrend7d = ref([])
const averageOrderAmountTrend7d = ref([])
const repurchaseRate7d = ref(0)
const repurchaseRate30d = ref(0)

const hotDishChartRef = ref(null)
const salesTrendRef = ref(null)
const avgTrendRef = ref(null)
let hotDishChart = null
let salesTrendChart = null
let avgTrendChart = null

const statCards = computed(() => [
  { title: '用户总数', value: stats.value.userTotalCount || 0, icon: '👥' },
  { title: '活跃用户', value: stats.value.activeUserCount || 0, icon: '🔥' },
  { title: '总订单数', value: stats.value.totalOrderCount || 0, icon: '📦' },
  { title: '今日订单', value: stats.value.todayOrderCount || 0, icon: '📊' },
  { title: '今日销售额', value: `￥${Number(stats.value.todaySalesAmount || 0).toFixed(2)}`, icon: '💰', highlight: true }
])
const currentHotDishes = computed(() => hotDishesByPeriod.value[activePeriod.value] || [])
const activePeriodLabel = computed(() => activePeriod.value === 'week' ? '本周' : '本月')
const totalSalesCount = computed(() => currentHotDishes.value.reduce((s, i) => s + (Number(i.salesCount) || 0), 0))
const totalSalesAmount = computed(() => currentHotDishes.value.reduce((s, i) => s + (Number(i.totalSales) || 0), 0))

const normalizeHotDishes = (list = []) => list.map((d, i) => ({ ...d, rank: d.rank || i + 1, salesCount: Number(d.salesCount || 0), totalSales: Number(d.totalSales || 0) }))
const fmt = v => Number(v || 0).toFixed(2)

const ensureCharts = () => {
  if (!hotDishChart && hotDishChartRef.value) hotDishChart = echarts.init(hotDishChartRef.value)
  if (!salesTrendChart && salesTrendRef.value) salesTrendChart = echarts.init(salesTrendRef.value)
  if (!avgTrendChart && avgTrendRef.value) avgTrendChart = echarts.init(avgTrendRef.value)
}

const renderHotDishChart = () => {
  if (!currentHotDishes.value.length) return
  ensureCharts()
  const names = currentHotDishes.value.map(i => i.dishName)
  const sales = currentHotDishes.value.map(i => Number(i.salesCount) || 0)
  hotDishChart?.setOption({
    grid: { left: 90, right: 30, top: 20, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: ps => { const p = ps?.[0]; if (!p) return ''; const d = currentHotDishes.value[p.dataIndex]; return `${d.dishName}<br/>销量：${d.salesCount}<br/>销售额：￥${fmt(d.totalSales)}` } },
    xAxis: { type: 'value' }, yAxis: { type: 'category', data: names, inverse: true },
    series: [{ type: 'bar', data: sales, barWidth: 18, label: { show: true, position: 'right' }, itemStyle: { borderRadius: [0, 9, 9, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#4338ca' }, { offset: .6, color: '#6366f1' }, { offset: 1, color: '#60a5fa' }]) } }]
  })
}

const renderSalesTrend = () => {
  ensureCharts()
  const x = salesOrderTrend7d.value.map(i => (i.date || '').slice(5))
  const sales = salesOrderTrend7d.value.map(i => Number(i.salesAmount || 0))
  const orders = salesOrderTrend7d.value.map(i => Number(i.orderCount || 0))
  salesTrendChart?.setOption({
    tooltip: { trigger: 'axis' }, legend: { data: ['销售额', '订单数'] }, grid: { left: 40, right: 40, top: 40, bottom: 24 },
    xAxis: { type: 'category', data: x }, yAxis: [{ type: 'value', name: '销售额(元)' }, { type: 'value', name: '订单数' }],
    series: [{ name: '销售额', type: 'line', smooth: true, data: sales, areaStyle: { opacity: .1 } }, { name: '订单数', type: 'bar', yAxisIndex: 1, data: orders, barMaxWidth: 24 }]
  })
}

const renderAvgTrend = () => {
  ensureCharts()
  const x = averageOrderAmountTrend7d.value.map(i => (i.date || '').slice(5))
  const avg = averageOrderAmountTrend7d.value.map(i => Number(i.averageOrderAmount || 0))
  avgTrendChart?.setOption({
    tooltip: { trigger: 'axis' }, grid: { left: 40, right: 20, top: 24, bottom: 24 },
    xAxis: { type: 'category', data: x }, yAxis: { type: 'value', name: '元' },
    series: [{ type: 'line', smooth: true, data: avg, symbol: 'circle', symbolSize: 8, lineStyle: { width: 3, color: '#6366f1' }, areaStyle: { color: 'rgba(99,102,241,.12)' } }]
  })
}

const renderAll = async () => { await nextTick(); renderHotDishChart(); renderSalesTrend(); renderAvgTrend() }
const resizeAll = () => { hotDishChart?.resize(); salesTrendChart?.resize(); avgTrendChart?.resize() }

const loadStatistics = async () => {
  loading.value = true
  try {
    const res = await statisticsAPI.getStatisticsOverview()
    if (res.data.code !== 200) { alert('获取统计数据失败，请重试'); return }
    const o = res.data.data || {}
    stats.value = { userTotalCount: o.userTotalCount || 0, activeUserCount: o.activeUserCount || 0, totalOrderCount: o.totalOrderCount || 0, todayOrderCount: o.todayOrderCount || 0, todaySalesAmount: o.todaySalesAmount || 0 }
    hotDishesByPeriod.value = { week: normalizeHotDishes(o.weeklyHotDishesTop5 || o.hotDishesTop5 || []), month: normalizeHotDishes(o.monthlyHotDishesTop5 || o.hotDishesTop5 || []) }
    salesOrderTrend7d.value = o.salesOrderTrend7d || []
    averageOrderAmountTrend7d.value = o.averageOrderAmountTrend7d || []
    repurchaseRate7d.value = Number(o.repurchaseRate7d || 0)
    repurchaseRate30d.value = Number(o.repurchaseRate30d || 0)
    await renderAll()
  } catch (e) {
    console.error('加载统计数据失败:', e)
    alert('加载统计数据失败，请检查网络连接')
  } finally { loading.value = false }
}

watch([activePeriod, currentHotDishes], async () => { await nextTick(); renderHotDishChart() }, { deep: true })
onMounted(async () => { window.addEventListener('resize', resizeAll); await loadStatistics() })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeAll); hotDishChart?.dispose(); salesTrendChart?.dispose(); avgTrendChart?.dispose() })
</script>

<style scoped>
.dashboard{position:relative;min-height:500px}.view-header,.section-header,.section-actions{display:flex;justify-content:space-between;align-items:center;gap:16px}.view-header{margin-bottom:20px}.page-title{font-size:28px;margin:0;font-weight:700;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end) 58%,var(--color-gradient-accent));background-clip:text;color:transparent}.page-subtitle,.section-subtitle,.label{margin:0;color:var(--color-text-sub);font-size:13px}.view-chip{padding:6px 12px;border-radius:999px;border:1px solid rgba(67,56,202,.2);background:rgba(67,56,202,.1);color:var(--color-primary-strong);font-size:12px;font-weight:600}.stats-overview{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin-bottom:24px}.stat-card{display:flex;align-items:center;gap:16px;border:1px solid var(--color-border);border-radius:18px;background:#fff}.stat-icon{width:56px;height:56px;border-radius:16px;display:flex;align-items:center;justify-content:center;font-size:24px;background:linear-gradient(135deg,var(--color-primary-light),#f8fafc)}.title{font-size:14px;color:var(--color-text-sub);margin-bottom:6px}.value{font-weight:700;color:var(--color-text-main)}.panel{margin-top:12px;padding:16px;border:1px solid var(--color-border);border-radius:18px;background:#fff}.time-toggle{display:inline-flex;padding:4px;border-radius:999px;background:rgba(67,56,202,.08);border:1px solid rgba(67,56,202,.12)}.toggle-btn,.refresh-btn{border:none;cursor:pointer}.toggle-btn{min-width:72px;padding:10px 18px;border-radius:999px;background:transparent;color:var(--color-text-sub);font-weight:600}.toggle-btn.active,.refresh-btn{color:#fff;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end))}.refresh-btn{padding:10px 20px;border-radius:12px}.chart-grid{display:grid;grid-template-columns:minmax(0,2fr) minmax(240px,1fr);gap:16px}.chart-panel,.summary-card{border:1px solid var(--color-border);border-radius:16px;background:#fff}.chart-panel{padding:16px}.chart-title{font-weight:600;margin-bottom:10px}.echart{height:320px}.summary-panel{display:grid;gap:12px}.summary-card{padding:14px}.summary-card.highlight{background:linear-gradient(135deg,rgba(67,56,202,.14),rgba(59,130,246,.14))}.metrics-grid{margin-top:16px;display:grid;grid-template-columns:1fr 1fr;gap:16px}.metrics-grid .full{grid-column:1/-1}.repurchase{display:flex;gap:16px;flex-wrap:wrap}.repurchase .box{flex:1;min-width:220px;padding:18px;border:1px dashed var(--color-border);border-radius:12px}.empty{padding:40px;text-align:center;color:var(--color-text-sub)}.loading-overlay{position:absolute;inset:0;background:rgba(255,255,255,.92);display:flex;flex-direction:column;justify-content:center;align-items:center;border-radius:16px}.loading-spinner{width:56px;height:56px;border:3px solid var(--color-primary-light);border-top:3px solid var(--color-primary);border-radius:50%;animation:spin 1s linear infinite;margin-bottom:20px}.loading-text{font-size:16px;color:var(--color-text-sub)}@keyframes spin{0%{transform:rotate(0)}100%{transform:rotate(360deg)}}@media (max-width:960px){.metrics-grid,.chart-grid{grid-template-columns:1fr}}@media (max-width:768px){.view-header,.section-header{flex-direction:column;align-items:flex-start}.stats-overview{grid-template-columns:1fr}.section-actions{width:100%;justify-content:space-between}.page-title{font-size:24px}.echart{height:280px}}
</style>
