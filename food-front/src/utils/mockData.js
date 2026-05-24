// 新增评论存储（模拟数据库）
let reviews = [
  {
    reviewId: 1,
    userId: 1,
    dishId: 1,
    orderId: 1001,
    rating: 5,
    content: '非常好吃！',
    createTime: '2025-03-20'
  }
];

// 添加评论
export const addReview = (userId, dishId, orderId, rating, content) => {
  const newReview = {
    reviewId: reviews.length + 1,
    userId,
    dishId,
    orderId,
    rating,
    content,
    createTime: new Date().toISOString().split('T')[0]
  };
  reviews.push(newReview);
  return newReview;
};

// 获取某道菜的所有评论
export const getReviewsByDish = (dishId) => {
  return reviews.filter(r => r.dishId === dishId);
};

// 获取某用户对某订单中某菜品的评论（用于判断是否已评）
export const getReviewByUserAndOrder = (userId, dishId, orderId) => {
  return reviews.find(r => r.userId === userId && r.dishId === dishId && r.orderId === orderId);
};
// 模拟菜品数据
export const dishes = [
   {
    id: 1,
    name: '宫保鸡丁',
    price: 38,
    image: 'https://picsum.photos/200/150?random=1',
    description: '经典川菜，麻辣鲜香，鸡肉滑嫩，花生酥脆。',
    category: '热菜',
    ingredients: ['鸡丁', '花生', '辣椒'],
    flavors: ['辣', '香'],
    tags: ['辣', '下饭'],
    nutrition: { calories: 420, protein: 28, fat: 24, carbs: 18 },
    isAvailable: true,
    salesCount: 320,
    rating: 4.5,        // 评分（满分5）
    reviewCount: 128,
    reviews: [
      { user: '张三', rating: 5, content: '非常好吃，辣度刚好！', date: '2025-03-20' },
      { user: '李四', rating: 4, content: '花生很脆，推荐。', date: '2025-03-18' },
      { user: '王五', rating: 5, content: '地道川味，下饭神器。', date: '2025-03-15' }
    ]
  },
  {
    id: 2,
    name: '酸菜鱼',
    price: 68,
    image: 'https://picsum.photos/200/150?random=2',
    description: '酸爽开胃，鱼片嫩滑，汤汁浓郁。',
    category: '热菜',
    ingredients: ['鱼片', '酸菜', '辣椒'],
    flavors: ['酸', '微辣'],
    tags: ['酸', '鱼'],
    isAvailable: true,
    salesCount: 280,
    rating: 4.7,
    reviewCount: 95,
    reviews: [
      { user: '小明', rating: 5, content: '汤很好喝，鱼片很嫩。', date: '2025-03-22' },
      { user: '小红', rating: 4, content: '酸味恰到好处。', date: '2025-03-19' }
    ]
  },
  {
    id: 3,
    name: '麻婆豆腐',
    price: 22,
    image: 'https://picsum.photos/200/150?random=3',
    description: '麻辣鲜香，下饭神器',
    category: '热菜',
    ingredients: ['豆腐', '肉末', '豆瓣酱'],
    flavors: ['辣', '麻'],
    tags: ['辣', '豆腐'],
    isAvailable: true,
    salesCount: 410
  },
  {
    id: 4,
    name: '清炒时蔬',
    price: 18,
    image: 'https://picsum.photos/200/150?random=4',
    description: '新鲜时蔬，清淡健康',
    category: '素菜',
    ingredients: ['时蔬'],
    flavors: ['清淡'],
    tags: ['清淡', '健康'],
    isAvailable: true,
    salesCount: 180
  },
  {
    id: 5,
    name: '糖醋里脊',
    price: 48,
    image: 'https://picsum.photos/200/150?random=5',
    description: '酸甜可口，外酥里嫩',
    category: '热菜',
    ingredients: ['里脊肉', '糖醋汁'],
    flavors: ['酸甜'],
    tags: ['酸甜', '肉'],
    isAvailable: true,
    salesCount: 210
  }
]
export const getDishDetail = (id) => {
  return dishes.find(d => d.id == id);
};
// 模拟用户历史订单（用于协同过滤）
export const orderHistory = [
  { userId: 1, dishId: 1 },
  { userId: 1, dishId: 3 },
  { userId: 2, dishId: 2 },
  { userId: 2, dishId: 4 },
  { userId: 1, dishId: 5 }
]

// 热门菜品（基于销量）
export const hotDishes = [...dishes].sort((a,b) => b.salesCount - a.salesCount)

// 模拟用户推荐结果
export function getRecommendations(userId, preferences = [], restrictions = [], currentIntent = null, currentFlavor = null) {
  // 简单内容推荐：基于口味偏好 + 忌口过滤
  let candidates = dishes.filter(d => d.isAvailable)
  if (restrictions.length) {
    candidates = candidates.filter(d => !d.ingredients.some(ing => restrictions.includes(ing)))
  }
  if (preferences.length) {
    candidates = candidates.filter(d => d.flavors.some(f => preferences.includes(f)))
  }
  if (currentFlavor) {
    candidates = candidates.filter(d => d.flavors.includes(currentFlavor))
  }
  // 协同简化：若用户点过某菜品，则推荐同类别或同标签（简化）
  const userOrdered = orderHistory.filter(o => o.userId === userId).map(o => o.dishId)
  if (userOrdered.length > 0) {
    const similar = dishes.filter(d => userOrdered.includes(d.id) && d.id !== userOrdered[0])
    candidates = [...candidates, ...similar]
  }
  // 去重并取前4
  const unique = Array.from(new Map(candidates.map(d => [d.id, d])).values())
  return unique.slice(0, 4).map(d => ({
    ...d,
    reason: preferences.includes(d.flavors[0]) ? `因为您喜欢${d.flavors[0]}口味` : '根据您的偏好推荐'
  }))
}

// 模拟自然语言解析（规则 + 简单意图识别）
export function parseNaturalLanguage(text) {
  // 简单规则模拟意图和实体抽取
  let intent = 'recommend'  // 默认推荐意图
  let entities = { flavors: [], ingredients: [], dishName: null, quantity: null }

  if (text.includes('加') || text.includes('点')) {
    intent = 'add'
    const match = text.match(/(\d+)?份?([\u4e00-\u9fa5]+)/)
    if (match) {
      entities.dishName = match[2]
      entities.quantity = match[1] ? parseInt(match[1]) : 1
    }
  } else if (text.includes('删') || text.includes('不要')) {
    intent = 'delete'
  } else if (text.includes('结账') || text.includes('结算')) {
    intent = 'checkout'
  } else if (text.includes('查询') || text.includes('看看')) {
    intent = 'query'
  } else {
    intent = 'recommend'
    // 抽取口味
    if (text.includes('辣')) entities.flavors.push('辣')
    if (text.includes('酸')) entities.flavors.push('酸')
    if (text.includes('甜')) entities.flavors.push('甜')
    if (text.includes('清淡')) entities.flavors.push('清淡')
    // 抽取食材
    if (text.includes('鸡')) entities.ingredients.push('鸡')
    if (text.includes('鱼')) entities.ingredients.push('鱼')
    if (text.includes('豆腐')) entities.ingredients.push('豆腐')
  }
  return { intent, entities }
  
}