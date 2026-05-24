---
name: upVue
description: 需要优化页面时触发
---

# Skill：Vue 点餐系统界面优化（时尚现代风格）

## 1. 任务背景
你正在协助优化一个**基于 Vue 的点餐系统前端**。后端接口与功能模块已经全部对接完成，当前需要**只对界面样式和用户体验进行重构**，不改变任何业务逻辑、API 调用、路由跳转和状态管理。目标是将界面风格转变为 **时尚、现代、高级感**，同时保持操作直观、干净大气。

## 2. 技术栈约束
- **框架**：Vue 2 / 3（根据现有项目）
- **UI 组件库**：若已使用（如 Element Plus、Vant、Ant Design Vue）可继续沿用，但需调整为时尚风格；若无组件库，推荐按需引入轻量级方案（如 Tailwind CSS + 自定义组件）。
- **样式预处理**：支持 SCSS / CSS / Tailwind，优先使用 **Tailwind CSS** 并结合自定义 CSS 变量实现时尚效果。
- **响应式**：需适配 PC 端（主要）和 平板/手机端（基础可用）。
- **图标**：推荐使用 Lucide、Heroicons 或 Feather 图标，线形纤细、风格统一。

## 3. 优化目标：时尚现代
**时尚现代** 的具体含义：
- **玻璃态/毛玻璃**：半透明背景 + 背景模糊（backdrop-filter: blur()），用于导航栏、卡片、弹窗。
- **微渐变**：按钮、标题、装饰条使用柔和的线性渐变，增加层次感。
- **高级阴影**：阴影更细腻、扩散范围更大，营造悬浮感和深度。
- **圆角更柔和**：默认圆角 12-24px，大卡片使用 24px，小元素 8-12px。
- **动态微交互**：悬停时元素轻微上浮、阴影加深、过渡平滑（0.2-0.3s）。
- **字体更现代**：使用 Inter、SF Pro、Poppins 等，字重变化丰富，行高舒适。
- **配色有记忆点**：主色可使用深紫、靛蓝、玫瑰金等，辅以高饱和点缀色（如荧光绿、橙色），但整体不超过 3 种主色。

## 4. 优化范围（按模块）

### 4.1 全局布局
- **顶部导航栏**：毛玻璃效果（背景半透白 + blur），下边框极细或无边。Logo 字体加粗时尚，导航项间距宽松，激活项使用渐变下划线。
- **侧边栏（若有）**：半透明深色或玻璃态，图标为线形，悬停时背景微亮。
- **主内容区**：最大宽度 1280px，居中，两侧留白。背景使用微妙的渐变或低饱和度纯色。
- **底部**：极简，半透明，版权信息小字。

### 4.2 用户管理模块（登录/注册/个人中心）
- 登录/注册页：**玻璃态卡片**居中，卡片背景半透白，模糊 10px，边框 1px 半透白。输入框无边框，底部有细线或内阴影。按钮采用渐变（主色到辅色）。
- 个人中心：头像圆形带细白边，偏好设置使用滑块或时尚开关，字段标签使用小写加粗。
- 历史订单：纵向卡片，左侧细条装饰显示订单状态颜色，右侧信息清晰。

### 4.3 自然语言交互模块（聊天式点餐）
- 对话框：用户气泡渐变背景（例如靛蓝到紫），机器人气泡白色带轻微阴影。气泡圆角 18px，有 tail 尖角。
- 输入框：毛玻璃底部条，圆角 30px，发送按钮为渐变圆形背景 + 图标。
- 滚动条：极细、圆角、深色半透明。

### 4.4 菜品管理模块（菜单浏览页）
- 菜品卡片：玻璃态或纯白卡片，圆角 20px，悬停上浮 4px，图片上叠加渐变蒙层显示价格。
- 加购按钮：圆形浮动按钮，渐变背景，悬停旋转或放大。
- 分类筛选：胶囊式标签，激活项为渐变背景白色文字，未激活为半透背景。
- 搜索框：圆角 40px，背景半透，放大镜图标为纤细线形。

### 4.5 智能推荐模块（推荐列表页）
- 推荐理由：小字带浅色背景圆角标签，配合发光点。
- 标题：“猜你喜欢”左侧加渐变装饰条，字重 600。
- 推荐卡片可带“热门”角标（小火焰图标 + 渐变）。

### 4.6 购物车模块
- 侧边抽屉：毛玻璃背景，从右侧滑出。菜品列表每行有下划线装饰。数量调节器为圆形加减号，带微渐变。
- 总价栏：渐变文字或强调色，结算按钮为渐变宽按钮，带微光效。

### 4.7 订单管理模块
- 订单卡片：玻璃态，左侧状态条颜色编码（待支付橙、已支付绿）。展开明细时使用淡入动画。
- 模拟支付页：居中卡片，动态支付成功打勾动画（SVG 描边动画）。

### 4.8 系统管理模块（管理员后台）
- 后台界面保持同样时尚感但更克制：表格使用圆角、行高较大，表头半透背景。统计图表使用渐变色填充，去除多余网格线。

## 5. 具体样式指导（全局 CSS 变量 + Tailwind 扩展）

### 5.1 色彩系统（时尚版）

```css
:root {
  /* 主色 – 深邃靛蓝 + 渐变方向 */
  --color-primary: #4f46e5;      /* 靛蓝 */
  --color-primary-dark: #4338ca;
  --color-primary-light: #e0e7ff;
  --color-gradient-start: #4f46e5;
  --color-gradient-end: #8b5cf6; /* 紫罗兰 */

  /* 点缀色 – 荧光绿/橙，用于价格、重要操作 */
  --color-accent: #f97316;       /* 亮橙 */
  --color-accent-glow: #ffedd5;

  /* 功能色 – 保持鲜艳 */
  --color-success: #10b981;
  --color-danger: #ef4444;
  --color-warning: #f59e0b;

  /* 背景 – 深色或浅色模式可选，这里提供浅色时尚版 */
  --color-bg-page: #f9fafb;      /* 极浅灰 */
  --color-bg-glass: rgba(255, 255, 255, 0.7);  /* 玻璃态背景 */
  --color-bg-card: rgba(255, 255, 255, 0.9);
  
  /* 文字 – 高对比 */
  --color-text-main: #111827;
  --color-text-sub: #4b5563;
  --color-text-muted: #9ca3af;

  /* 边框 – 极细半透 */
  --color-border: rgba(0, 0, 0, 0.08);
  --color-border-light: rgba(255, 255, 255, 0.2);

  /* 阴影 – 多层次 */
  --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.02), 0 4px 12px rgba(0, 0, 0, 0.03);
  --shadow-md: 0 8px 20px rgba(0, 0, 0, 0.05), 0 2px 4px rgba(0, 0, 0, 0.02);
  --shadow-lg: 0 20px 35px -8px rgba(0, 0, 0, 0.1), 0 4px 12px rgba(0, 0, 0, 0.02);
  --shadow-glass: 0 8px 32px rgba(0, 0, 0, 0.08);
}
```

### 5.2 全局重置与字体

```css
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  background: var(--color-bg-page);
  font-family: 'Inter', 'Poppins', 'PingFang SC', system-ui, -apple-system, sans-serif;
  color: var(--color-text-main);
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

/* 玻璃态通用类 */
.glass {
  background: var(--color-bg-glass);
  backdrop-filter: blur(12px);
  border: 1px solid var(--color-border-light);
  border-radius: 24px;
  box-shadow: var(--shadow-glass);
}
```

### 5.3 按钮样式（时尚微渐变 + 悬停动效）

- **主要按钮**：  
  背景: 线性渐变 `135deg, var(--color-gradient-start), var(--color-gradient-end)`  
  无边框，圆角 40px，内边距 12px 28px，白色文字，字重 500。  
  悬停：亮度增加 5%，阴影扩大，轻微上浮 `transform: translateY(-2px)`，过渡 0.25s。

- **次要按钮**：  
  背景透明，边框 1px solid `var(--color-border)`，圆角 40px，文字深灰色。  
  悬停：边框颜色变为主色，文字变主色。

- **危险按钮**：  
  背景透明，文字 `var(--color-danger)`，悬停背景淡红 `#fee2e2`。

- **点缀按钮（加购/结算）**：  
  渐变背景（橙色到粉色），圆形或圆角 40px，内边距 8px 20px，白色文字，悬停缩放 1.02 倍。

### 5.4 卡片样式（玻璃态或纯白悬浮）

```css
.card {
  background: var(--color-bg-card);
  backdrop-filter: blur(0px); /* 若纯白无模糊 */
  border-radius: 24px;
  padding: 1.5rem;
  box-shadow: var(--shadow-sm);
  transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
  border: 1px solid var(--color-border);
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: rgba(79, 70, 229, 0.2);
}

/* 玻璃态卡片变体 */
.card-glass {
  composes: glass;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
}
```

### 5.5 其他时尚细节

- **渐变文字**：标题或价格可使用 `background: linear-gradient(135deg, #4f46e5, #8b5cf6); background-clip: text; color: transparent;`。
- **发光效果**：重要按钮或激活项添加 `box-shadow: 0 0 12px rgba(79, 70, 229, 0.4)`。
- **微动效**：页面切换使用淡入淡出，列表项出现使用 stagger 动画。
- **滚动条**：
  ```css
  ::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
  ::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 10px;
  }
  ::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 10px;
  }
  ::-webkit-scrollbar-thumb:hover {
    background: #94a3b8;
  }
  ```

## 6. 模块配色与组件指引（时尚适配）

- **价格数字**：使用渐变文字（靛蓝到紫罗兰），加粗 600，字号 1.25rem。
- **推荐理由标签**：毛玻璃小圆点 + 浅色半透明背景，文字中等粗细。
- **购物车总价**：渐变文字，加粗 700。
- **订单状态**：圆点 + 半透背景圆角标签，例如待支付为橙色半透背景，已支付为翠绿半透背景。
- **导航栏激活项**：渐变下划线（高度 2px，宽度从 0 到 100% 过渡）。

## 7. 保持现有功能不受影响
- **不修改** 任何 `methods`、`computed`、`watch`、`mounted` 中的业务逻辑。
- **不修改** API 调用路径、参数、响应处理。
- **不修改** 路由跳转规则、权限控制逻辑。
- **不修改** 购物车、订单、推荐的核心算法（前端仅展示结果）。
- 可以**添加**新的 CSS 类、组件封装（如将旧按钮替换为新样式组件），但必须保证事件绑定和原组件一致。

## 8. 实施步骤建议
1. **分析当前项目**：检查现有 UI 组件库和样式组织方式。
2. **引入 Tailwind CSS**（若没有）：便于快速实现时尚样式。
3. **定义全局设计 token**：颜色、字体、圆角、阴影、动画曲线。
4. **逐模块重构样式**：从全局布局 → 登录页 → 菜单页 → 购物车 → 订单 → 后台。
5. **每完成一个模块**，在浏览器中验证交互是否正常（点击、输入、弹窗等）。
6. **响应式测试**：缩放窗口至 375px 检查基本可用性。
7. **微调动画**：确保动效流畅不卡顿。

## 9. 交付标准
- 所有页面视觉风格统一、时尚、具有现代感，无明显陈旧元素。
- 关键操作（加购、下单、登录）的视觉反馈丰富（悬停、点击态、加载态）。
- 无控制台报错，原有功能完全正常。
- 代码整洁，注释移除冗余样式，使用 BEM 或 Tailwind 工具类。
- 玻璃态、渐变、阴影等效果在主流浏览器（Chrome、Firefox、Safari）下表现正常。
