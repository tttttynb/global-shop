# 🌍 Global Shop — AI 驱动的跨境电商平台

> 基于 **Spring Boot 3 + Vue 3** 的全栈跨境电商平台：AI 购物顾问、直播带货、社交拼团、会员积分、多币种多语言国际化、跨境税费计算一站式集成。
>
> 前端仓库 → **[global-shop-web](https://github.com/tttttynb/global-shop-web)**（Vue 3 + Element Plus + Pinia + vue-i18n 四语言）

## 🚀 技术栈

| 类别 | 技术 |
|------|------|
| **核心框架** | Spring Boot 3.4.1, Java 17 |
| **ORM** | MyBatis-Plus 3.5.5 |
| **数据库** | MySQL 8 |
| **缓存** | Redis + Redisson + Caffeine（多级缓存） |
| **搜索引擎** | Elasticsearch 8（BM25 + kNN 向量检索） |
| **消息队列** | RabbitMQ（延迟队列/死信队列） |
| **安全认证** | Spring Security Crypto + JWT (Auth0) |
| **AI 框架** | Spring AI 1.0.4 + LangChain4j 1.12.1（Function Calling Agent） |
| **大模型** | 阿里云百炼 · 通义千问（对话 qwen-plus / 视觉 qwen-vl-max / 向量 text-embedding） |
| **直播** | 阿里云直播 SDK + WebSocket + NLS 语音转写/TTS |
| **工具库** | Hutool, Lombok |

## ✨ 核心功能

### 🛒 电商基础
- **用户系统** — 注册/登录、JWT 认证、收货地址、用户画像（行为事件异步构建）
- **商品系统** — 商品发布（AI 辅助生成文案）、分类、收藏、**SKU 多规格**（颜色×尺码，独立价格/库存/图片）、**价格历史走势**（近 90 天曲线）
- **购物车** — 按店铺分组、批量加购（AI 一键加购入口）
- **订单系统** — 下单、支付流转、超时取消（MQ 延迟消息）、自动确认收货
- **多渠道支付** — `PaymentGateway` 策略模式：余额 / 支付宝沙箱 / Stripe，可配置启停
- **退款售后** — 申请、审核、原路退款、SKU 库存回补
- **优惠券** — 满减/折扣券、领取、核销、**积分兑换**
- **物流跟踪** — 发货、轨迹查询（预留快递100/17TRACK）
- **商家体系** — 入驻申请、店铺管理、经营看板、订单发货、退款审核

### 👥 社交拼团（Phase 4）
- 商家创建拼团活动：拼团价 / 成团人数（2-10）/ 开团有效时长 / 每人限参次数
- 开团 → 分享链接落地页（成员坑位、还差 N 人、倒计时）→ 参团支付 → 成团发货
- **超时自动解散退款**：定时扫描抢占式置失败，未支付取消 + 已支付原路退款 + 库存回补
- 并发安全：成团链路全条件 UPDATE（回调/定时任务并发无重复副作用）
- 风控：活动级限参 + Redis 每日开/参团次数上限；跨境主打「拼邮费」，成团前不发货

### 🎁 会员积分（Phase 4）
- **四路获取**：下单返积分 / 每日签到（连签加成）/ 评价晒单 / 完善画像奖励 — 全部幂等 + Redis 防刷
- **两路消耗**：下单抵扣（100 积分 = 1 元，单笔封顶 50%，原子扣减防超扣）/ 积分兑换优惠券（乐观锁）
- **等级体系**：青铜→白银→黄金→钻石按累计消费升级，等级折扣自动作用下单
- 积分/等级信息注入 AI 导购提示词，实现「会员尊享」个性化话术

### 🤖 AI 全链路
- **AI 购物顾问**（Phase 4 升级）— 金牌导购人设：多轮需求挖掘（预算/场景/偏好）→ `searchByBudget` 预算内精选 → `getBundleRecommendation` 跨品类成套推荐（共现矩阵 + 语义召回，报全家桶总价）→ `batchAddToCart` 一键加购；推荐商品以**可加购卡片**随回复返回前端
- **提示词工程** — Agent 侧两条铁律：「先查再答」（涉及具体商品必须先调工具拿真实数据）与「金额一律人民币」（历史成交价格带仅作偏好参考，买家预算优先，严禁以档位为由拒答）；`@Tool` 描述 + `@P` 参数说明强化工具调用命中率
- **AI 语义搜索** — Embedding 向量 + ES kNN 检索 + 用户层级个性化重排
- **AI 以图搜图** — qwen-vl-max 视觉理解 → 语义召回，拍照找同款
- **AI 口碑档案 2.0**（Phase 4）— 评价 LLM 沉淀为持久化档案（优缺点 Top3 / 适合人群 / 推荐度 / 买家印象标签墙），新评价防抖合并增量重算，四语言异步补翻
- **个性化推荐** — 用户画像分层（PREMIUM/MID/BUDGET/NEW）、看了还看（ES More Like This）、买了还买（订单共现）、猜你喜欢；A/B 开关对比通用/个性化双 Agent
- **AI 商家助手** — 商品分析、销售洞察
- **AI 直播助理** — 直播间智能回复与商品推荐

### 📹 跨境直播
- 直播间创建/推流/拉流（阿里云）、商品上下架、WebSocket 弹幕
- **直播间闪购秒杀** — 主播一键发起限时秒杀（SKU 预占库存、限购一次、倒计时卡片实时推送、到期自动回补）
- **实时翻译** — NLS 语音转写 + 机器翻译 + TTS，中 → 英/日/韩/泰

### 🌐 多币种多语言国际化（Phase 3）
- **汇率服务** — 双 API 数据源（er-api → frankfurter 降级）定时刷新 + Redis 缓存 + DB 兜底 + 静态汇率三级降级
- **原币定价** — 商品支持 USD/EUR/GBP/JPY/KRW/THB 原币价，发布时实时汇率折算 CNY 记账，下单**锁汇快照**可审计
- **商品文案 AI 翻译** — 发布事件驱动异步翻译四语言，详情按 `Accept-Language` 返回
- **前端四语言** — vue-i18n（中/英/日/韩）+ 七币种参考价切换

### 🧾 跨境税费计算（Phase 3）
- 预估到手价 = 商品价 + 国际运费（¥29，满 ¥199 包邮）+ 跨境综合税
- 税率档位「品类 × 目的国」（美妆 23.06% / 服饰 20% / 珠宝腕表 50% / 通用兜底 9.1%）
- 数量/规格变化 300ms 防抖重算，费用随订单落库全链路展示

### ⚡ 工程能力
- 多级缓存（Caffeine + Redis + Redisson 分布式锁）、缓存/SKU 库存预热
- 事件驱动架构（Spring Event：支付成功/评价创建/商品发布 → 异步监听，积分/拼团/翻译/画像解耦）
- 实时通知体系（WebSocket 站内推送 + 邮件 + 用户偏好）、运维监控与告警巡检

## 📁 项目结构

```
src/main/java/com/bohao/globalshop/
├── agent/          # AI Agent（导购/客服/直播助理）+ 工具集 + 商品卡片收集器
├── common/         # 通用工具 (JWT, Result, UserContext)
├── config/         # 配置类 (Redis, RabbitMQ, MyBatis-Plus, WebSocket, CORS)
├── controller/     # REST API 控制器
├── dto/            # 数据传输对象
├── entity/         # 数据库实体
├── enums/          # 枚举（订单状态/通知类型等）
├── event/          # Spring 事件（OrderPaidEvent, ReviewCreatedEvent...）
├── exception/      # 全局异常处理
├── interceptor/    # JWT 拦截器
├── listener/       # MQ + 事件监听器
├── mapper/         # MyBatis-Plus Mapper
├── repository/     # ES Repository
├── service/        # 业务服务（含 payment 网关策略）
├── task/           # 定时任务（拼团过期/汇率刷新/口碑档案重算等）
├── vo/             # 视图对象
└── websocket/      # WebSocket 处理器

docs/sql/           # 数据库迁移脚本 V1-V12
```

## 🔧 快速开始

### 环境要求
JDK 17+ · MySQL 8 · Redis 7+ · Elasticsearch 8.x · RabbitMQ 3.x · Node.js 18+（前端）

### 1. 克隆仓库

```bash
git clone https://github.com/tttttynb/global-shop.git        # 后端
git clone https://github.com/tttttynb/global-shop-web.git    # 前端
```

### 2. 初始化数据库

```sql
CREATE DATABASE global_shop DEFAULT CHARACTER SET utf8mb4;
```

按顺序执行 `docs/sql/` 下迁移脚本：
- `V1-V7`：用户画像 / 聊天记录 / 评价种子 / 支付单 / 订单支付关联 / 物流
- `V8-V9`：SKU 规格系统、价格历史（另有通知表脚本）
- `V10`：直播闪购秒杀
- `V11`：多币种国际化 + 跨境税费（exchange_rate / product_translation / tax_rule）
- `V12`：社交拼团 + 会员积分 + 口碑档案（group_buy_* / member_points / points_record / review_intelligence）

### 3. 配置环境变量

```bash
# AI（阿里云百炼 API Key，必填）
export AI_API_KEY=sk-your-api-key

# 数据库 / ES
export DB_PASSWORD=your-db-password
export ES_PASSWORD=your-es-password

# 支付（可选，默认仅余额支付）
export ALIPAY_APP_ID=... ALIPAY_PRIVATE_KEY=... ALIPAY_PUBLIC_KEY=...
export STRIPE_SECRET_KEY=... STRIPE_WEBHOOK_SECRET=...

# 直播 / 语音（可选，占位不影响启动）
export LIVE_ACCESS_KEY_ID=... LIVE_ACCESS_KEY_SECRET=...
export NLS_APP_KEY=... NLS_ACCESS_KEY=... NLS_ACCESS_SECRET=...

# 邮件通知（可选）
export MAIL_PASSWORD=...
```

### 4. 启动后端

```bash
./mvnw spring-boot:run    # http://localhost:8080
```

### 5. 启动前端

```bash
cd global-shop-web
npm install
npm run dev               # http://localhost:5173
```

## 📡 API 概览

| 模块 | 端点前缀 | 说明 |
|------|----------|------|
| 用户 | `/api/user` | 注册、登录、画像、地址 |
| 商品 | `/api/product` | 浏览、搜索、SKU、价格历史、评价、**口碑档案**；分页列表支持**价格区间筛选 + 销量排序** |
| 购物车 | `/api/cart` | CRUD、**批量加购** |
| 订单 | `/api/order` | 下单（拼团/积分抵扣/**收货地址快照**）、结算、评价 |
| 支付 | `/api/payment` | 多渠道支付、回调 |
| **拼团** | `/api/group-buy` | 专区、开团/参团、我的拼团、商家活动管理 |
| **积分** | `/api/points` | 总览、签到、流水、兑换商城、抵扣试算 |
| 优惠券 | `/api/coupon` | 领取、我的券 |
| **汇率** | `/api/forex` | 实时汇率查询 |
| **税费** | `/api/tax` | 到手价试算 |
| AI | `/api/ai` | 语义搜索、以图搜图 |
| **AI 导购** | `/api/chat` | 购物顾问对话（返回 `{reply, products}` 可加购卡片） |
| 直播 | `/api/live` | 直播间、闪购秒杀 |
| 物流 | `/api/shipment` | 发货、轨迹 |
| 通知 | `/api/notification` | 站内通知 |
| 商家 | `/api/merchant` | 店铺、商品、订单、退款、看板 |
| 健康检查 | `/api/health` | 服务状态 |

## ⚙️ 功能开关（application.yml）

| 配置 | 默认 | 说明 |
|------|------|------|
| `app.personalization.enabled` | true | 个性化 Agent A/B 开关 |
| `app.forex.enabled` | true | 汇率外呼开关（关闭走兜底汇率） |
| `app.i18n.auto-translate` | true | 商品发布自动 AI 翻译四语言 |
| `app.tax.enabled` | true | 跨境税费计算 |
| `app.group-buy.daily-join-limit` | 5 | 每人每日开/参团上限 |
| `app.points.enabled` | true | 积分体系总开关 |
| `app.reputation.enabled` | true | AI 口碑档案生成 |
| `app.payment.channels` | balance,alipay,stripe | 启用的支付渠道 |

## 📝 最近更新（2026-10）

- **购物车结算支持收货地址**：`checkout` 新增 `addressId`，优先指定地址/回落默认地址，拆单后所有订单共享地址快照（修复结算从不写收货人的缺口）
- **商品分页列表**：新增 `minPrice/maxPrice` 价格区间筛选，返回体透出 `salesCount`（配合前端销量标签与排序）
- **ES 文档扩展**：`EsProduct` 映射 `category_id` / `sales_count`，支撑搜索结果类目聚合与已售标签
- **AI Agent 修复**：见上方「提示词工程」——修复美元表述与预算被拒答问题
- 配套前端改动见 [global-shop-web](https://github.com/tttttynb/global-shop-web#-最近更新2026-10)

## 📄 License

This project is licensed under the MIT License.

---

**Made with ❤️ by Qing Ling (tttttynb)**
