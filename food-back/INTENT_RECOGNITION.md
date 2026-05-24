# 意图识别功能介绍

## 概述

基于 BERT NER 的自然语言理解能力，聊天系统实现了 6 类意图的自动识别与分发，支持从"推荐菜品"到"直接下单"的完整对话流程。

## 意图分类

| 意图 | 触发条件 | 示例输入 |
|---|---|---|
| `RECOMMEND` | 包含口味/偏好关键词，或短文本（≤8 字） | "推荐辣的不要海鲜"、"想吃点什么"、"辣" |
| `ORDER` | 包含下单指令关键词 | "帮我点一份麻婆豆腐"、"来一份宫保鸡丁"、"就要这个" |
| `ADD_TO_CART` | 包含购物车关键词 | "加入购物车"、"先收藏" |
| `EXPLAIN` | 包含追问原因关键词 | "为什么推荐这些"、"原因" |
| `CONFIRM_ORDER` | 有待确认订单时发送确认词 | "确认"、"好"、"好的"、"可以" |
| `CANCEL_ORDER` | 有待确认订单时发送取消词 | "取消"、"算了"、"不要了" |
| `CHAT` | 以上均不匹配时的兜底 | "你好"、"今天天气怎么样" |

## 核心流程

### 下单流程

```
用户: "帮我点一份麻婆豆腐"
    │
    ▼
classifyIntent() ──► ORDER
    │
    ▼
NER 提取: dishes=["麻婆豆腐"]
正则槽位: quantity=1
    │
    ▼
DB 查询菜品 ──► DishInfo { dishId=5, price=28 }
    │
    ▼
生成 pendingOrder ──► Redis (TTL 5分钟)
    │
    ▼
回复: "确认下单：麻婆豆腐 × 1份，共 ¥28.00
      回复「确认」下单，或「取消」放弃。"
    │
    ▼  [前端展示确认卡片，含确认/取消按钮]
    │
用户: "确认"
    │
    ▼
classifyIntent() ──► CONFIRM_ORDER
    │
    ▼
CartService.addToCart() ──► CartService.batchCheckout()
    │
    ▼
生成 OrderMain ──► 清除 pendingOrder
    │
    ▼
回复: "已为您下单！订单号 #202604180001，共 ¥28.00"
```

### 推荐流程（原有，重构为独立 handler）

```
用户: "推荐辣的不要海鲜"
    │
    ▼
classifyIntent() ──► RECOMMEND
    │
    ▼
NER 提取偏好 → 合并 Redis 多轮记忆 → 推荐服务 → 返回菜品列表
```

## 关键实现

### classifyIntent(String input, String sessionId)

- 优先级：pendingOrder 确认/取消 > ORDER > ADD_TO_CART > EXPLAIN > RECOMMEND > CHAT
- pendingOrder 存在时，额外匹配确认/取消语义，避免误解普通对话

### 槽位填充 parseOrderSlots()

- **菜品名**：优先使用 BERT NER 提取的 `dishes` 列表；NER 未命中时回退到正则匹配 `来/点/要 + 菜品名`
- **数量**：正则匹配 `\d+ 份/个/碗/盘`，默认 1

### pendingOrder 生命周期

- 存储：Redis，key = `chat:pending_order:{sessionId}`
- TTL：5 分钟，超时自动清除
- 状态机：`null → PENDING → CONFIRMED/CANCELED → null`

## 改动文件

### 后端

`admin/.../controller/RecommendationChatController.java`

- `chat()` 方法重构为意图分发架构（switch 路由到各 handler）
- 新增 7 个 handler 方法：`handleRecommendIntent`、`handleOrderIntent`、`handleConfirmOrder`、`handleCancelOrder`、`handleAddToCartIntent`、`handleExplainIntent`、`handleChatIntent`
- 新增内部类 `OrderSlots` 和槽位填充方法 `parseOrderSlots()`
- 新增 pendingOrder Redis 读写方法
- 注入 `CartService`、`OrderService`

### 前端

`food-front/src/views/ChatView.vue`

- 新增订单确认卡片（pendingOrder 渲染）：菜品图片、名称、数量、金额、确认/取消按钮
- 新增下单成功卡片（orderResult 渲染）：订单号、总价
- 抽取 `sendTextMessage()` 公共方法，供输入框和按钮复用
- 新增 `confirmOrder()`、`cancelOrder()` 方法
- 快捷提示增加下单示例
