# 🌍 Global Shop

> 一个基于 Spring Boot 3 的全栈跨境电商平台，集成 AI 智能助手、直播带货与实时翻译功能。

## 🚀 技术栈

| 类别 | 技术 |
|------|------|
| **核心框架** | Spring Boot 3.4.1, Java 17 |
| **ORM** | MyBatis-Plus 3.5.5 |
| **数据库** | MySQL |
| **缓存** | Redis + Redisson + Caffeine |
| **搜索引擎** | Elasticsearch |
| **消息队列** | RabbitMQ |
| **安全认证** | Spring Security Crypto + JWT (Auth0) |
| **AI 框架** | Spring AI 1.0.4 + LangChain4j 1.12.1 |
| **大模型** | 阿里云百炼 · 通义千问 (Qwen3.5-Plus) |
| **直播** | 阿里云直播 SDK + WebSocket |
| **语音/翻译** | 阿里云智能语音 NLS + 机器翻译 |
| **工具库** | Hutool 5.8.44, Lombok |

## ✨ 核心功能

### 🛒 电商基础
- **用户系统** — 注册/登录、JWT 认证、个人信息管理、收货地址
- **商品管理** — 商品发布、分类浏览、收藏、多维度搜索
- **购物车** — 按店铺分组展示、批量操作
- **订单系统** — 下单、支付状态流转、超时取消（RabbitMQ 延迟队列）
- **退款售后** — 退款申请与审核处理
- **优惠券** — 发放、领取、使用核销
- **商家入驻** — 店铺申请、商品上下架管理

### 🤖 AI 智能
- **AI 客服** — 基于 LangChain4j 的智能对话，支持 Function Calling 查询订单/商品
- **AI 商家助手** — 商品分析、销售洞察，通过 Agent 工具链调用后端服务
- **AI 搜索** — Elasticsearch + 向量嵌入，实现语义化商品检索

### 📹 直播带货
- **直播间管理** — 创建/关闭直播间、商品上下架
- **实时消息** — WebSocket 双向通信，支持聊天与系统通知
- **多语言翻译** — 阿里云机器翻译，实时翻译直播消息（支持英/日/韩/泰）
- **语音交互** — 阿里云 NLS 语音识别与合成
- **AI 直播助手** — 智能回复、商品推荐

### ⚡ 性能优化
- **多级缓存** — Caffeine 本地缓存 + Redis 分布式缓存
- **缓存预热** — 启动时自动加载热点数据
- **定时任务** — 订单超时取消等周期性任务

## 📁 项目结构

```
src/main/java/com/bohao/globalshop/
├── agent/          # AI Agent 工具与客服助手
├── common/         # 通用工具类 (JWT, Result)
├── config/         # 配置类 (Redis, RabbitMQ, MyBatis-Plus, WebSocket)
├── controller/     # REST API 控制器
├── dto/            # 数据传输对象
├── entity/         # 数据库实体
├── exception/      # 全局异常处理
├── interceptor/    # JWT 拦截器
├── listener/       # 消息队列监听器
├── mapper/         # MyBatis Mapper 接口
├── repository/     # ES Repository
├── service/        # 业务服务接口与实现
├── task/           # 定时任务 & 缓存预热
├── vo/             # 视图对象
└── websocket/      # WebSocket 处理器
```

## 🔧 快速开始

### 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 7.0+
- Elasticsearch 8.x
- RabbitMQ 3.x

### 1. 克隆仓库

```bash
git clone https://github.com/tttttynb/global-shop.git
cd global-shop
```

### 2. 配置数据库

创建 MySQL 数据库并配置 `application.yml` 中的数据源连接信息。

```sql
CREATE DATABASE global_shop DEFAULT CHARACTER SET utf8mb4;
```

### 3. 配置环境变量

```bash
# 阿里云直播配置
export LIVE_ACCESS_KEY_ID=your-access-key-id
export LIVE_ACCESS_KEY_SECRET=your-access-key-secret
export LIVE_PUSH_DOMAIN=push.example.com
export LIVE_PULL_DOMAIN=pull.example.com
export LIVE_APP_NAME=globalshop
export LIVE_AUTH_KEY=your-auth-key

# 阿里云语音配置
export NLS_APP_KEY=your-nls-app-key
export NLS_ACCESS_KEY=your-nls-access-key
export NLS_ACCESS_SECRET=your-nls-access-secret
```

### 4. 配置 AI

在 `application.yml` 中填入你的阿里云百炼 API Key：

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key
      base-url: https://dashscope.aliyuncs.com/compatible-mode
langchain4j:
  open-ai:
    chat-model:
      api-key: sk-your-api-key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen3.5-plus
```

### 5. 启动应用

```bash
./mvnw spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

## 📡 API 概览

| 模块 | 端点前缀 | 说明 |
|------|----------|------|
| 健康检查 | `/api/health` | 服务健康状态 |
| 用户 | `/api/user` | 注册、登录、个人信息 |
| 商品 | `/api/products` | 商品浏览、搜索、详情 |
| 分类 | `/api/categories` | 商品分类 |
| 购物车 | `/api/cart` | 购物车 CRUD |
| 订单 | `/api/orders` | 订单管理 |
| 商家 | `/api/merchant` | 店铺申请、商品管理 |
| 优惠券 | `/api/coupons` | 优惠券领取与使用 |
| AI 搜索 | `/api/ai/search` | AI 智能搜索 |
| AI 商家 | `/api/ai/merchant` | 商家 AI 助手 |
| AI 对话 | `/api/chat` | AI 客服对话 |
| 直播 | `/api/live` | 直播间管理 |
| WebSocket | `/ws/live` | 直播实时通信 |

## 📄 License

This project is licensed under the MIT License.

---

**Made with ❤️ by Qing Ling (tttttynb)**
