<template>
  <div class="chat-page">
    <section class="hero-panel card-glass">
      <div>
        <span class="eyebrow">FoodNLP Copilot</span>
        <h1>智能饮食顾问</h1>
        <p>从口味、忌口、预算和场景中提取偏好，给出可解释、可追问、可迭代的菜品推荐。</p>
      </div>
      <div class="hero-stats">
        <div><strong>{{ userTurnCount }}</strong><span>对话轮次</span></div>
        <div><strong>{{ latestRecommendations.length }}</strong><span>推荐菜品</span></div>
        <div><strong>{{ preferenceScore }}%</strong><span>画像完整度</span></div>
      </div>
    </section>

    <section class="chat-workbench">
      <aside class="insight-panel card-glass">
        <span class="eyebrow">Preference Insight</span>
        <h2>实时偏好画像</h2>
        <div class="radar-score">{{ preferenceScore }}%</div>
        <p>{{ insightSummary }}</p>
        <div class="profile-item"><span>偏好标签</span><strong>{{ topTags.join(' / ') || '等待输入' }}</strong></div>
        <div class="preference-tags" v-if="preferenceLabels.length">
          <span v-for="item in preferenceLabels" :key="`${item.type}-${item.value}`" :class="['pref-tag', item.type]">{{ item.label }}：{{ item.value }}</span>
        </div>
        <div class="profile-item"><span>平均预算</span><strong>{{ averagePrice }}</strong></div>
        <div class="profile-item"><span>最近推荐</span><strong>{{ latestRecommendations[0]?.name || '暂无菜品' }}</strong></div>
        <div class="strategy-box"><strong>推荐链路</strong><ul><li>语义槽位抽取</li><li>多轮偏好记忆</li><li>菜品匹配与理由生成</li></ul></div>
      </aside>

      <section class="chat-shell card-glass">
      <header class="chat-header"><div class="avatar">AI</div><div class="header-copy"><span class="eyebrow">Multi-turn Semantic Recommendation</span><h2 class="title">对话式菜品推荐</h2><p class="subtitle">支持聊天、推荐、解释原因、换一批和追加约束。</p></div><button type="button" class="ghost reset" @click="resetConversation">重开会话</button></header><div class="scenario-prompts"><button v-for="scene in scenarioPrompts" :key="scene.title" type="button" class="scene-card" @click="usePrompt(scene.prompt)"><strong>{{ scene.title }}</strong><span>{{ scene.desc }}</span></button></div><div class="quick-prompts">
        <button v-for="prompt in quickPrompts" :key="prompt" class="chip" @click="usePrompt(prompt)">{{ prompt }}</button>
      </div>

      <main class="messages" ref="messagesRef">
        <div v-for="msg in messages" :key="msg.id" class="row" :class="`role-${msg.role}`">
          <div v-if="msg.role === 'assistant'" class="role-badge ai">AI</div>

          <div class="content-wrap">
            <ChatBubble :role="msg.role" :message="msg.content" :time="msg.time" />

            <div v-if="msg.role === 'assistant' && msg.recommendations?.length" class="rec-block">
              <div class="rec-head">
                <strong>为你推荐以下菜品</strong>
                <button class="ghost" @click="reuseRecommendations(msg.recommendations)">查看本轮推荐</button>
              </div>

              <button
                v-for="dish in msg.recommendations"
                :key="`${msg.id}-${dish.id}`"
                type="button"
                class="rec-item"
                @click="openDishDetail(dish)"
              >
                <img v-if="dish.image" :src="dish.image" :alt="dish.name" class="rec-img" />
                <div v-else class="rec-img placeholder">暂无图片</div>
                <div class="rec-body">
                  <div class="rec-top">
                    <span class="name">{{ dish.name }}</span>
                    <span class="price">￥{{ dish.price.toFixed(2) }}</span>
                  </div>
                  <div class="desc">{{ dish.description }}</div>
                  <div class="reason">{{ dish.reason }}</div>
                  <div class="meta">
                    <span v-for="tag in dish.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
                    <span v-if="dish.rating !== null" class="rating">★ {{ dish.rating.toFixed(1) }}</span>
                  </div>
                </div>
              </button>
            </div>

            <div v-if="msg.role === 'assistant' && msg.pendingOrder" class="order-card pending">
              <div class="order-card-head">
                <strong>待确认下单</strong>
                <span class="order-expire">5分钟内有效</span>
              </div>
              <div class="order-dish-row">
                <img v-if="msg.pendingOrder.dishImage" :src="resolveImageUrl(msg.pendingOrder.dishImage)" class="order-dish-img" />
                <div v-else class="order-dish-img placeholder">暂无图片</div>
                <div class="order-dish-info">
                  <span class="order-dish-name">{{ msg.pendingOrder.dishName }}</span>
                  <span class="order-dish-qty">× {{ msg.pendingOrder.quantity }} 份</span>
                </div>
                <span class="order-dish-total">¥{{ toFixed(msg.pendingOrder.total, 2) }}</span>
              </div>
              <div class="order-card-actions">
                <button class="order-btn confirm" :disabled="msg._confirming" @click="confirmOrder(msg)">
                  {{ msg._confirming ? '下单中...' : '确认下单' }}
                </button>
                <button class="order-btn cancel" :disabled="msg._confirming" @click="cancelOrder(msg)">取消</button>
              </div>
            </div>

            <div v-if="msg.role === 'assistant' && msg.orderResult" class="order-card success">
              <div class="order-success-icon">✓</div>
              <div class="order-success-info">
                <strong>下单成功！</strong>
                <span>订单号 #{{ msg.orderResult.orderNo || msg.orderResult.orderId }}</span>
                <span>共 ¥{{ toFixed(msg.orderResult.totalPrice, 2) }}</span>
              </div>
            </div>
          </div>

          <div v-if="msg.role === 'user'" class="role-badge me">我</div>
        </div>
      </main>

      <footer class="composer">
        <p class="tip">试试：推荐点辣的不要海鲜 / 帮我点一份宫保鸡丁 / 为什么推荐这些菜？</p>
        <div class="composer-row">
          <input
            v-model="inputText"
            class="chat-input"
            :disabled="isSending"
            @keyup.enter="sendMessage"
            placeholder="描述口味偏好、直接点菜，或者聊聊你想吃的..."
          />
          <button class="send" :disabled="isSending || !inputText.trim()" @click="sendMessage">
            {{ isSending ? '分析中...' : '生成推荐' }}
          </button>
        </div>
      </footer>
    </section>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { recommendationChatAPI, ratingAPI } from '@/api'
import { useUserStore } from '@/stores/user'
import ChatBubble from '@/components/ChatBubble.vue'
import { resolveImageUrl } from '@/utils/image'

const userStore = useUserStore()
const router = useRouter()

const CHAT_SESSION_KEY = 'chat_session_id'
const CHAT_MESSAGES_KEY_PREFIX = 'chat_messages_state:'

const inputText = ref('')
const messagesRef = ref(null)
const messages = ref([])
const sessionId = ref('')
const isSending = ref(false)
const tempPreference = ref(null)
const ratingCache = new Map()

const quickPrompts = [
  '推荐点辣的，不要海鲜',
  '我想吃点清淡的',
  '今天想吃下饭菜',
  '帮我点一份麻婆豆腐',
  '来点汤类，别太油',
  '想吃便宜点的',
  '换一批推荐',
  '为什么推荐这些菜？'
]

const formatTime = (d) => `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`

const scenarioPrompts = [
  { title: '健身轻食', desc: '低油高蛋白', prompt: '我在控制热量，推荐几道清淡、高蛋白、不要太油的菜' },
  { title: '宿舍聚餐', desc: '高性价比', prompt: '和同学聚餐，想要下饭、价格别太贵、适合分享的菜' },
  { title: '重口开胃', desc: '辣味优先', prompt: '今天想吃辣一点开胃的菜，不要海鲜，推荐并解释原因' },
  { title: '暖胃汤品', desc: '舒适治愈', prompt: '想喝点热乎的汤，口味清淡一点，别太油' }
]

const latestRecommendations = computed(() => [...messages.value].reverse().find(m => m.recommendations?.length)?.recommendations || [])
const latestPreferenceFromMessages = computed(() => [...messages.value].reverse().find(m => m.preferences)?.preferences || null)
const activePreference = computed(() => tempPreference.value || latestPreferenceFromMessages.value || {})
const userTurnCount = computed(() => messages.value.filter(m => m.role === 'user').length)
const preferenceLabels = computed(() => {
  const labels = activePreference.value?.labels
  if (Array.isArray(labels) && labels.length) return labels
  const result = []
  ;[
    ['口味', 'taste', activePreference.value?.tastes],
    ['忌口', 'taboo', activePreference.value?.taboos],
    ['菜品', 'dish', activePreference.value?.dishes]
  ].forEach(([label, type, list]) => {
    if (Array.isArray(list)) list.filter(Boolean).forEach(value => result.push({ label, type, value }))
  })
  return result
})
const preferenceScore = computed(() => Math.min(96, 35 + userTurnCount.value * 8 + preferenceLabels.value.length * 8 + latestRecommendations.value.length * 3))
const topTags = computed(() => preferenceLabels.value.map(item => `${item.label}:${item.value}`))
const averagePrice = computed(() => {
  if (!latestRecommendations.value.length) return '待分析'
  const avg = latestRecommendations.value.reduce((sum, dish) => sum + dish.price, 0) / latestRecommendations.value.length
  return `￥${avg.toFixed(0)}`
})
const insightSummary = computed(() => preferenceLabels.value.length
  ? `已沉淀 ${preferenceLabels.value.length} 个临时偏好，包含口味、忌口和菜品意图，将持续参与本轮推荐。`
  : '开始对话后会自动沉淀口味、忌口、菜品等临时偏好。')

const createWelcomeMessage = () => ({
  id: 1,
  role: 'assistant',
  content: '您好！我是智能饮食顾问。告诉我口味、预算、忌口或场景，我会推荐菜品并解释匹配理由。',
  time: formatTime(new Date()),
  recommendations: []
})

const getMessagesKey = () => `${CHAT_MESSAGES_KEY_PREFIX}${userStore.userId || 'guest'}`

const persistMessages = () => localStorage.setItem(getMessagesKey(), JSON.stringify(messages.value))

function restoreMessages() {
  const raw = localStorage.getItem(getMessagesKey())
  if (!raw) return false
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed) || !parsed.length) return false
    messages.value = parsed.map((m, i) => ({
      id: m.id ?? Date.now() + i,
      role: m.role || 'assistant',
      content: m.content || '',
      time: m.time || formatTime(new Date()),
      recommendations: Array.isArray(m.recommendations) ? m.recommendations : [],
      preferences: m.preferences || null
    }))
    return true
  } catch {
    return false
  }
}

const usePrompt = (prompt) => { inputText.value = prompt }
const openDishDetail = (dish) => {
  if (!dish) return
  const query = dish.matchScore ? { matchScore: Math.round(dish.matchScore) } : {}
  router.push({ path: `/dish/${dish.id}`, query })
}

function resetConversation() {
  sessionId.value = ''
  localStorage.removeItem(CHAT_SESSION_KEY)
  messages.value = [createWelcomeMessage()]
  tempPreference.value = null
  persistMessages()
  scrollToBottom()
}

function reuseRecommendations(recommendations) {
  messages.value.push({
    id: Date.now(),
    role: 'assistant',
    content: '这是当前这轮推荐结果，你也可以继续补充条件，比如“不要太油”“换一批”。',
    time: formatTime(new Date()),
    recommendations
  })
  persistMessages()
  scrollToBottom()
}

function mapDishFromBackend(dish) {
  const rawTaste = dish?.taste || ''
  const tags = rawTaste ? rawTaste.replace(/[\[\]"]/g, '').split(/[,、]/).map(t => t.trim()).filter(Boolean) : []
  const image = resolveImageUrl(dish?.imageUrl || dish?.dishImage || dish?.image || dish?.imgUrl || '')
  return {
    id: dish?.dishId ?? dish?.id,
    name: dish?.dishName || dish?.name || '未知菜品',
    image,
    price: Number(dish?.price ?? dish?.dishPrice ?? 0),
    description: dish?.description?.trim() || '暂无描述',
    tags,
    rating: null,
    matchScore: Number(dish?.matchScore ?? dish?.score ?? Math.min(98, 84 + Math.min(tags.length, 3) * 3 + Number(dish?.dishId ?? dish?.id ?? 0) % 9)),
    reason: dish?.reason || dish?.recommendReason || `${tags.length ? `符合${tags.slice(0, 2).join('、')}偏好` : '适合作为综合推荐'}，可根据你的反馈继续微调。`
  }
}

const buildDefaultReply = (dishes) => dishes?.length ? `已为您推荐：${dishes.map(d => d.name).join('、')}` : '暂时没有匹配菜品，您可以换个口味描述试试。'

function toFixed(val, digits) {
  if (val === null || val === undefined) return '0.00'
  return Number(val).toFixed(digits)
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

const updateTempPreference = (payload) => {
  const summary = payload?.tempPreferenceSummary || payload?.updatedTempPreferences || payload?.usedPreferences || null
  tempPreference.value = summary
  return summary
}

async function enrichRatingsForMessage(messageId) {
  const message = messages.value.find(m => m.id === messageId)
  const list = Array.isArray(message?.recommendations) ? message.recommendations : []
  const need = list.filter(d => d.rating === null)
  if (!need.length) return

  const updates = await Promise.all(need.map(async (dish) => {
    if (ratingCache.has(dish.id)) return { id: dish.id, rating: ratingCache.get(dish.id) }
    try {
      const res = await ratingAPI.getAverage(dish.id)
      if (res?.data?.code === 200 && res.data.data !== null && res.data.data !== undefined) {
        const r = parseFloat(res.data.data)
        ratingCache.set(dish.id, r)
        return { id: dish.id, rating: r }
      }
    } catch {}
    return null
  }))

  const map = new Map(updates.filter(Boolean).map(i => [i.id, i.rating]))
  if (!map.size) return

  messages.value = messages.value.map(m => m.id !== messageId ? m : {
    ...m,
    recommendations: m.recommendations.map(d => map.has(d.id) ? { ...d, rating: map.get(d.id) } : d)
  })
  persistMessages()
}

async function confirmOrder(msg) {
  msg._confirming = true
  try {
    await sendTextMessage('确认')
    delete msg.pendingOrder
  } catch {
    // sendTextMessage 内部已处理错误
  } finally {
    msg._confirming = false
  }
}

async function cancelOrder(msg) {
  msg._confirming = true
  try {
    await sendTextMessage('取消')
    delete msg.pendingOrder
  } catch {
    // sendTextMessage 内部已处理错误
  } finally {
    msg._confirming = false
  }
}

async function sendTextMessage(text) {
  if (isSending.value) return
  isSending.value = true

  messages.value.push({ id: Date.now(), role: 'user', content: text, time: formatTime(new Date()), recommendations: [] })
  persistMessages()
  scrollToBottom()

  try {
    const res = await recommendationChatAPI.chat({ userInput: text, sessionId: sessionId.value, userId: userStore.userId || null })
    const payload = res?.data?.data || {}

    if (payload.sessionId) {
      sessionId.value = payload.sessionId
      localStorage.setItem(CHAT_SESSION_KEY, payload.sessionId)
    }

    const dishList = Array.isArray(payload.recommendations)
      ? payload.recommendations
      : Array.isArray(payload?.recommendation?.records)
        ? payload.recommendation.records
        : []

    const shouldRecommend = payload.shouldRecommend !== undefined ? !!payload.shouldRecommend : dishList.length > 0
    const recs = shouldRecommend ? dishList.map(mapDishFromBackend) : []
    const reply = payload.reply || payload.assistantReply || buildDefaultReply(recs)

    const id = Date.now()
    const preferences = updateTempPreference(payload)
    const newMsg = { id, role: 'assistant', content: reply, time: formatTime(new Date()), recommendations: recs, preferences }

    if (payload.pendingOrder) newMsg.pendingOrder = payload.pendingOrder
    if (payload.orderResult) newMsg.orderResult = payload.orderResult
    if (payload.needLogin) newMsg.needLogin = true

    messages.value.push(newMsg)
    persistMessages()
    scrollToBottom()

    if (recs.length) enrichRatingsForMessage(id)
  } catch {
    messages.value.push({ id: Date.now(), role: 'assistant', content: '抱歉，出现了一些问题，请稍后再试。', time: formatTime(new Date()), recommendations: [] })
    persistMessages()
  } finally {
    isSending.value = false
  }
}

async function sendMessage() {
  if (!inputText.value.trim() || isSending.value) return
  const text = inputText.value.trim()
  inputText.value = ''
  await sendTextMessage(text)
}

onMounted(() => {
  const sid = localStorage.getItem(CHAT_SESSION_KEY)
  if (sid) sessionId.value = sid
  if (!restoreMessages()) {
    messages.value = [createWelcomeMessage()]
    persistMessages()
  } else {
    tempPreference.value = latestPreferenceFromMessages.value
  }
  scrollToBottom()
})
</script>

<style scoped>
.chat-page { height: calc(100dvh - 120px); padding: 14px 8px; overflow: hidden; box-sizing: border-box; }
.chat-shell { height: 100%; max-width: 980px; margin: 0 auto; display: flex; flex-direction: column; min-height: 0; border-radius: 22px; overflow: hidden; border: 1px solid var(--color-border); }
.chat-header { display: flex; gap: 12px; align-items: center; padding: 16px 18px; border-bottom: 1px solid var(--color-border); background: rgba(255,255,255,.75); }
.avatar { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; color: #fff; font-weight: 700; background: linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end)); }
.title { margin: 0; font-size: 22px; }
.subtitle { margin: 2px 0 0; font-size: 13px; color: var(--color-text-sub); }
.quick-prompts { display: flex; gap: 8px; overflow-x: auto; padding: 10px 14px; border-bottom: 1px solid var(--color-border); background: rgba(255,255,255,.62); }
.chip { border: 1px solid rgba(67,56,202,.16); background: #fff; border-radius: 999px; padding: 7px 12px; white-space: nowrap; cursor: pointer; }
.messages { flex: 1; min-height: 0; overflow-y: auto; padding: 14px; display: flex; flex-direction: column; gap: 14px; background: linear-gradient(180deg,rgba(255,255,255,.55),rgba(248,250,252,.82)); }
.row { display: flex; gap: 10px; }
.role-user { justify-content: flex-end; }
.role-badge { width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.role-badge.ai { background: linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end)); color: #fff; }
.role-badge.me { background: #0f172a; color: #fff; }
.content-wrap { max-width: min(760px, calc(100% - 42px)); display: flex; flex-direction: column; gap: 8px; }
.rec-block { border: 1px solid rgba(67,56,202,.12); border-radius: 14px; padding: 10px; background: rgba(255,255,255,.92); }
.rec-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; }
.ghost { border: 1px solid var(--color-border); background: #fff; border-radius: 10px; padding: 6px 10px; cursor: pointer; }
.rec-item { width: 100%; margin-top: 8px; border: 1px solid rgba(67,56,202,.1); border-radius: 12px; padding: 8px; display: grid; grid-template-columns: 72px minmax(0,1fr); gap: 10px; background: #fff; text-align: left; cursor: pointer; }
.rec-img { width: 72px; height: 72px; border-radius: 10px; object-fit: cover; }
.rec-img.placeholder { display: grid; place-items: center; background: #f1f5f9; color: #64748b; font-size: 12px; }
.rec-top { display: flex; justify-content: space-between; gap: 8px; }
.name { font-weight: 700; }
.price { color: var(--color-primary-strong); font-weight: 700; }
.desc { font-size: 12px; color: var(--color-text-sub); margin-top: 4px; }
.meta { margin-top: 6px; display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.tag { background: rgba(67,56,202,.08); color: var(--color-primary-strong); border-radius: 999px; padding: 2px 8px; font-size: 11px; }
.rating { color: #f59e0b; font-weight: 700; font-size: 12px; }
.composer { border-top: 1px solid var(--color-border); padding: 10px 12px; background: rgba(255,255,255,.84); }
.tip { margin: 0 0 8px; font-size: 12px; color: var(--color-text-sub); }
.composer-row { display: flex; gap: 8px; }
.chat-input { flex: 1; height: 44px; border: 1px solid var(--color-border); border-radius: 12px; padding: 0 12px; font-size: 16px; }
.send { height: 44px; min-width: 80px; border: none; border-radius: 12px; color: #fff; background: linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end)); }

@media (max-width: 768px) {
  .chat-page { height: calc(100dvh - 100px); padding: 8px; }
  .subtitle, .tip { display: none; }
  .title { font-size: 20px; }
  .rec-item { grid-template-columns: 1fr; }
  .rec-img { width: 100%; height: 128px; }
}
.hero-panel{max-width:1180px;margin:0 auto 14px;padding:20px;display:grid;grid-template-columns:1fr auto;gap:18px}.eyebrow{color:var(--color-gradient-accent);font-size:12px;font-weight:900;letter-spacing:.08em;text-transform:uppercase}.hero-panel h1{margin:4px 0 8px;font-size:38px;background:linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end),var(--color-gradient-accent));background-clip:text;color:transparent}.hero-panel p{margin:0;color:var(--color-text-sub)}.hero-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;align-content:center}.hero-stats div,.profile-item{padding:12px;border-radius:16px;background:rgba(255,255,255,.76);border:1px solid var(--color-border)}.hero-stats strong{display:block;font-size:24px;color:var(--color-primary-strong)}.hero-stats span,.profile-item span{font-size:12px;color:var(--color-text-sub)}.chat-workbench{max-width:1180px;height:calc(100dvh - 260px);min-height:640px;margin:0 auto;display:grid;grid-template-columns:290px minmax(0,1fr);gap:14px}.insight-panel{padding:18px;display:flex;flex-direction:column;gap:12px;overflow:hidden}.insight-panel h2{margin:4px 0}.radar-score{height:170px;border-radius:50%;display:grid;place-items:center;border:1px dashed rgba(59,47,127,.28);font-size:36px;font-weight:900;color:var(--color-primary-strong);background:radial-gradient(circle,#fff 0 35%,rgba(11,122,117,.08) 36%)}.insight-panel p{margin:0;color:var(--color-text-sub)}.profile-item strong{display:block;margin-top:4px}.strategy-box{margin-top:auto;padding:14px;border-radius:18px;background:#111827;color:#fff}.strategy-box ul{margin:8px 0 0;padding-left:18px;color:rgba(255,255,255,.78);font-size:13px}.header-copy{flex:1}.reset{margin-left:auto}.scenario-prompts{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;padding:12px 14px;border-bottom:1px solid var(--color-border);background:rgba(255,255,255,.5)}.scene-card{text-align:left;border:1px solid var(--color-border);border-radius:14px;padding:10px;background:#fff;cursor:pointer}.scene-card strong,.scene-card span{display:block}.scene-card span{font-size:12px;color:var(--color-text-sub)}.reason{margin-top:6px;padding:7px;border-radius:10px;background:rgba(11,122,117,.08);color:#0b615d;font-size:12px}.match{background:rgba(15,23,42,.86)!important;color:#fff!important}@media(max-width:1080px){.chat-workbench{grid-template-columns:1fr;height:auto}.insight-panel{display:none}}@media(max-width:768px){.hero-panel{grid-template-columns:1fr}.hero-stats{grid-template-columns:repeat(3,1fr)}.scenario-prompts{display:flex;overflow-x:auto}.scene-card{min-width:130px}.reset{display:none}}
.preference-tags{display:flex;flex-wrap:wrap;gap:6px}.pref-tag{padding:5px 8px;border-radius:999px;font-size:12px;font-weight:800;background:rgba(67,56,202,.08);color:var(--color-primary-strong);border:1px solid rgba(67,56,202,.1)}.pref-tag.taboo{background:rgba(239,68,68,.1);color:#b91c1c}.pref-tag.dish{background:rgba(11,122,117,.1);color:#0b615d}.pref-tag.taste{background:rgba(245,158,11,.12);color:#a16207}
.layout-fix-anchor { display: none; }
.chat-page { min-height: calc(100dvh - 120px) !important; height: auto !important; padding: 12px 10px 24px !important; overflow: visible !important; }
.hero-panel { max-width: 1180px !important; margin-bottom: 12px !important; padding: 16px 18px !important; align-items: center; }
.hero-panel h1 { font-size: clamp(26px, 3vw, 36px) !important; }
.hero-stats div { min-width: 92px; }
.chat-workbench { height: calc(100dvh - 238px) !important; min-height: 520px !important; max-width: 1180px !important; grid-template-columns: 260px minmax(0, 1fr) !important; align-items: stretch; }
.insight-panel { min-height: 0; overflow-y: auto !important; }
.radar-score { height: 128px !important; font-size: 30px !important; flex-shrink: 0; }
.profile-item { padding: 10px 12px !important; }
.strategy-box { padding: 12px !important; }
.chat-shell { width: 100%; min-width: 0; max-width: none !important; }
.chat-header { padding: 12px 14px !important; flex-shrink: 0; }
.avatar { width: 40px !important; height: 40px !important; }
.title { font-size: 20px !important; }
.scenario-prompts { padding: 8px 12px !important; gap: 8px !important; flex-shrink: 0; }
.scene-card { padding: 8px 10px !important; min-width: 0; }
.quick-prompts { padding: 8px 12px !important; flex-shrink: 0; }
.chip { padding: 6px 10px !important; font-size: 13px; }
.messages { min-height: 0 !important; padding: 12px !important; }
.content-wrap { max-width: min(780px, calc(100% - 42px)) !important; }
.rec-item { grid-template-columns: 88px minmax(0, 1fr) !important; }
.rec-img { width: 88px !important; height: 88px !important; }
.reason { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.composer { padding: 9px 12px !important; flex-shrink: 0; }
.tip { margin-bottom: 6px !important; }

@media (max-width: 1180px) {
  .chat-workbench { grid-template-columns: 230px minmax(0, 1fr) !important; }
  .hero-panel { grid-template-columns: minmax(0, 1fr) !important; }
  .hero-stats { grid-template-columns: repeat(3, minmax(0, 1fr)) !important; }
}

@media (max-width: 1080px) {
  .chat-page { padding: 10px 8px 18px !important; }
  .hero-panel { display: none !important; }
  .chat-workbench { height: calc(100dvh - 120px) !important; min-height: 560px !important; grid-template-columns: 1fr !important; }
  .insight-panel { display: none !important; }
}

@media (max-width: 768px) {
  .chat-page { height: auto !important; min-height: calc(100dvh - 96px) !important; padding: 8px !important; }
  .chat-workbench { height: calc(100dvh - 108px) !important; min-height: 520px !important; }
  .chat-header { gap: 8px !important; }
  .subtitle, .tip, .reset { display: none !important; }
  .scenario-prompts { display: flex !important; overflow-x: auto !important; }
  .scene-card { min-width: 126px !important; }
  .rec-item { grid-template-columns: 76px minmax(0, 1fr) !important; }
  .rec-img { width: 76px !important; height: 84px !important; }
  .composer-row { flex-direction: column; }
  .send { width: 100%; }
}

.order-card { border-radius: 14px; padding: 14px; margin-top: 8px; border: 1px solid; }
.order-card.pending { border-color: rgba(245,158,11,.35); background: rgba(255,251,235,.92); }
.order-card.success { border-color: rgba(16,185,129,.35); background: rgba(240,253,244,.92); }
.order-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.order-expire { font-size: 11px; color: #92400e; background: rgba(245,158,11,.12); padding: 2px 8px; border-radius: 999px; }
.order-dish-row { display: flex; align-items: center; gap: 10px; }
.order-dish-img { width: 56px; height: 56px; border-radius: 10px; object-fit: cover; }
.order-dish-img.placeholder { display: grid; place-items: center; background: #f1f5f9; color: #64748b; font-size: 11px; }
.order-dish-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.order-dish-name { font-weight: 700; font-size: 15px; }
.order-dish-qty { font-size: 12px; color: var(--color-text-sub); }
.order-dish-total { font-weight: 700; font-size: 18px; color: #92400e; }
.order-card-actions { display: flex; gap: 8px; margin-top: 12px; }
.order-btn { flex: 1; height: 40px; border-radius: 10px; border: none; font-size: 15px; font-weight: 600; cursor: pointer; }
.order-btn.confirm { background: linear-gradient(135deg,var(--color-gradient-start),var(--color-gradient-end)); color: #fff; }
.order-btn.cancel { background: #fff; color: var(--color-text-sub); border: 1px solid var(--color-border); }
.order-btn:disabled { opacity: .6; cursor: not-allowed; }
.order-success-icon { width: 44px; height: 44px; border-radius: 50%; background: #10b981; color: #fff; display: grid; place-items: center; font-size: 22px; font-weight: 700; margin-bottom: 8px; }
.order-success-info { display: flex; flex-direction: column; gap: 2px; }
.order-success-info strong { font-size: 16px; }
.order-success-info span { font-size: 13px; color: var(--color-text-sub); }
</style>
