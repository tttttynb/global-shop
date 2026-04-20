# React 全球购电商前端开发计划

## 技术选型

| 技术 | 用途 |
|------|------|
| React 18 + Vite | 核心框架与构建工具 |
| React Router v6 | 路由管理 |
| Axios | HTTP 请求 |
| TailwindCSS 3 | 原子化样式，快速实现精美布局 |
| Framer Motion | 页面过渡与交互动画 |
| React Hot Toast | 轻量通知提示 |
| Lucide React | 精美图标库 |
| Zustand | 轻量状态管理（用户认证状态、购物车） |

## 设计风格

- **整体风格**: 现代简约 + 渐变玻璃拟态（Glassmorphism），深色/浅色主题
- **配色方案**: 主色调采用靛蓝渐变（Indigo-Purple），辅以暖色点缀
- **动态效果**: 页面切换淡入动画、卡片悬停 3D 倾斜效果、滚动视差、骨架屏加载、按钮微交互
- **布局**: 响应式设计，支持桌面端与移动端

## 项目结构

```
g:\shop\global-shop-frontend\
├── index.html
├── package.json
├── vite.config.js
├── tailwind.config.js
├── postcss.config.js
├── src\
│   ├── main.jsx                    # 应用入口
│   ├── App.jsx                     # 路由配置 + 全局布局
│   ├── index.css                   # TailwindCSS 入口 + 全局样式
│   ├── api\
│   │   └── index.js                # Axios 实例 + 所有 API 请求封装
│   ├── store\
│   │   └── useAuthStore.js         # Zustand 用户认证状态管理
│   ├── components\
│   │   ├── Navbar.jsx              # 顶部导航栏（搜索框、购物车图标、用户菜单）
│   │   ├── Footer.jsx              # 底部栏
│   │   ├── ProductCard.jsx         # 商品卡片（带悬停动画）
│   │   ├── CartDrawer.jsx          # 购物车侧拉抽屉
│   │   ├── OrderStatusBadge.jsx    # 订单状态标签
│   │   ├── ReviewCard.jsx          # 评价卡片
│   │   ├── StarRating.jsx          # 星级评分组件
│   │   ├── SearchBar.jsx           # 智能搜索框（支持 AI 语义搜索切换）
│   │   ├── SkeletonCard.jsx        # 骨架屏加载
│   │   └── AnimatedPage.jsx        # 页面切换动画包装器
│   └── pages\
│       ├── HomePage.jsx            # 首页：商品列表 + 搜索 + Banner
│       ├── ProductDetailPage.jsx   # 商品详情：图片、描述、评价、加购
│       ├── CartPage.jsx            # 购物车：按店铺分组、结算
│       ├── OrdersPage.jsx          # 我的订单：状态筛选、支付/收货/评价
│       ├── LoginPage.jsx           # 登录/注册（双面卡片切换）
│       ├── MerchantPage.jsx        # 商家中心：开店、发布商品、订单管理、发货
│       └── SearchResultPage.jsx    # 搜索结果页（ES 搜索 + AI 语义搜索）
```

## 页面设计概要

### 1. 登录/注册页（LoginPage）
- 全屏渐变背景 + 居中浮动玻璃卡片
- 登录/注册双面翻转切换动画
- 表单输入带焦点高亮和验证反馈

### 2. 首页（HomePage）
- 顶部渐变 Hero Banner 带动态粒子/波浪背景
- 搜索框居中突出，支持普通搜索和 AI 语义搜索模式切换
- 商品网格布局（响应式 2-4 列），卡片带悬停缩放 + 阴影动画
- 滚动加载动画（fade-in-up）

### 3. 商品详情页（ProductDetailPage）
- 左图右信息经典布局
- 加入购物车按钮带弹性动画
- 底部评价区域，星级评分可视化
- 立即购买 + 加入购物车双按钮

### 4. 购物车页（CartPage）
- 按店铺分组展示，每组带店铺名称标题
- 数量调节器、删除按钮、小计计算
- 底部固定结算栏，显示总金额 + 结算按钮

### 5. 我的订单页（OrdersPage）
- 顶部 Tab 筛选：全部 / 待支付 / 已支付 / 已发货 / 已收货
- 订单卡片：订单号、状态标签（不同颜色）、商品列表、操作按钮
- 操作按钮：支付、确认收货、评价（根据订单状态动态显示）
- 评价弹窗：星级评分 + 文字输入

### 6. 搜索结果页（SearchResultPage）
- 支持 ES 全文搜索和 AI 语义搜索两种模式
- AI 模式带特殊视觉标识（渐变光晕）
- 搜索结果网格展示

### 7. 商家中心页（MerchantPage）
- 左侧菜单：开店申请 / 商品发布 / 店铺订单
- 开店表单：店铺名称 + 描述
- 商品发布表单：完整的商品信息录入
- 订单管理表格：状态筛选、发货按钮

## 任务拆分

### Task 1: 项目初始化与基础架构
- 使用 Vite 创建 React 项目
- 安装和配置所有依赖（TailwindCSS、Framer Motion、Axios、Zustand、React Router、Lucide、react-hot-toast）
- 创建 `api/index.js`（Axios 实例 + 拦截器 + 全部 23 个 API 封装）
- 创建 `store/useAuthStore.js`（JWT 存储与管理）
- 创建 `App.jsx`（路由配置）、`main.jsx`（入口）、`index.css`（全局样式 + TailwindCSS）

### Task 2: 公共组件开发
- Navbar、Footer、AnimatedPage、SkeletonCard
- ProductCard、SearchBar、StarRating、OrderStatusBadge、ReviewCard

### Task 3: 登录注册页 + 首页
- LoginPage（登录/注册切换）
- HomePage（Hero Banner + 商品网格）

### Task 4: 商品详情页 + 搜索结果页
- ProductDetailPage（商品信息 + 评价列表 + 加购/购买）
- SearchResultPage（ES 搜索 + AI 语义搜索）

### Task 5: 购物车页 + 订单页
- CartPage（按店铺分组 + 结算）
- OrdersPage（状态筛选 + 支付/收货/评价）

### Task 6: 商家中心页
- MerchantPage（开店 + 商品发布 + 订单管理 + 发货）

### Task 7: 联调验证
- 启动前端，进行页面浏览和交互验证

## API 对接（共 23 个接口）

后端基地址: `http://localhost:8080/api`，JWT Token 通过 `Authorization` Header 传递。

| 页面 | 调用接口 |
|------|---------|
| 登录/注册 | POST `/user/login`、POST `/user/register` |
| 首页 | GET `/product/list` |
| 商品详情 | GET `/product/detail/{id}`、GET `/product/{id}/reviews` |
| 搜索 | GET `/product/search`、GET `/ai/semantic` |
| 购物车 | POST `/cart/add`、GET `/cart/list`、DELETE `/cart/remove/{id}` |
| 订单 | POST `/order/create`、POST `/order/checkout`、GET `/order/my`、POST `/order/pay/{id}`、POST `/order/confirm-receipt/{id}`、POST `/order/review` |
| 商家中心 | POST `/merchant/shop/apply`、POST `/merchant/product/publish`、GET `/merchant/order/list`、POST `/merchant/order/deliver/{id}` |
