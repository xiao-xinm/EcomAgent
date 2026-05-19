# SmartCS-Agent：AI Agent 智能客服系统

> 面向电商场景的 AI Agent 智能客服系统——通过自然语言交互自动处理常见客服请求，对敏感操作发起人工审核，实现 **"Agent 自动处理 + 人工兜底"** 的混合服务模式。

---

## 核心特性

- **四级风险路由** — L0 自动执行 → L1 执行+通知 → L2 预执行+确认 → L3 人工审核，逐级递进、宁严勿松
- **动态风控评估** — 同一意图根据订单状态、金额阈值、用户情绪等维度实时升降级风险等级
- **混合服务模式** — Agent 处理 60%+ 常见请求，敏感操作无缝转人工，上下文完整透传
- **多轮对话管理** — 槽位填充 + 状态机驱动，支持指代消解、意图切换、超时兜底
- **RAG 知识增强** — 向量检索 + 关键词检索混合策略，实时数据走 API 不走 RAG
- **可插拔技能引擎** — 技能注册式扩展，新增技能 ≤ 3 人天，API 调用链声明式编排

---

## 系统架构概览

```
┌─────────────────────────────────────────────────────────┐
│                   用户触点层 (Touchpoint)                 │
│              APP-H5  │  H5 网页                          │
├─────────────────────────────────────────────────────────┤
│              接入网关 / 会话管理层 (Gateway)               │
│     消息标准化 │ 会话管理 │ 状态机 │ 限流                 │
├─────────────────────────────────────────────────────────┤
│                AI Agent 核心层 (Core)                     │
│   NLU │ 对话管理器(DM) │ 技能编排(Skill Router) │ 风控路由 │
├─────────────────────────────────────────────────────────┤
│                 后端支撑层 (Backend)                      │
│   电商业务API │ 人工工作台 │ 知识库/RAG │ 通知服务        │
├─────────────────────────────────────────────────────────┤
│               基础设施层 (Infrastructure)                 │
│   Redis │ Milvus │ ES │ RocketMQ │ Prometheus+Grafana    │
└─────────────────────────────────────────────────────────┘
```

📖 详细架构设计见 [架构设计文档](./docs/架构设计文档.md)

---

## 当前工程落地说明

当前仓库已按根 `pom.xml` 的 Maven 多模块声明落地工程骨架，实际 Java 模块以 `smartcs-*` 命名为准：

| 概念层 | 实际模块 |
|------|----------|
| 接入网关 / 会话管理 | `smartcs-gateway` |
| Agent 核心编排 | `smartcs-agent-core` |
| 技能编排引擎 | `smartcs-skill-engine` |
| 坐席工作台后端 | `smartcs-workbench` |
| 知识库 / RAG | `smartcs-knowledge` |
| 通知服务 | `smartcs-notification` |
| 公共契约与工具 | `smartcs-common` |

本阶段只搭建框架、目录、依赖和文档契约，不包含订单、退款、换货、修改地址等业务实现代码。

---

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| **前端（用户端）** | React / Vue 3 + TypeScript、WebSocket (SockJS)、TailwindCSS |
| **前端（坐席工作台）** | React 18 + Ant Design Pro、WebSocket 实时推送 |
| **Agent 核心** | Python 3.11+、FastAPI、LangChain / 自研 Agent 框架 |
| **NLU / LLM** | Qwen-Max（主） / GPT-4o（备）、text2vec-large-chinese（Embedding） |
| **后端服务** | Java 17 / Spring Boot 3、MyBatis-Plus、Spring Security + JWT |
| **消息中间件** | RocketMQ 5.x |
| **缓存 & 状态** | Redis 7.x Cluster |
| **向量库** | Milvus 2.x |
| **搜索引擎** | Elasticsearch 8.x |
| **数据库** | MySQL 8.x |
| **监控** | Prometheus + Grafana、SkyWalking（链路追踪） |
| **容器 & 部署** | Docker、Kubernetes、Helm |

---

## 项目结构

```
smartcs-agent/
├── README.md
├── docs/                          # 项目文档
│   ├── 架构设计文档.md
│   └── ...
├── requirements/                  # 需求文档
│   └── AI客服Agent需求文档.md
├── gateway/                       # 接入网关服务 (Java/Spring Boot)
│   ├── src/main/java/com/smartcs/gateway/
│   │   ├── controller/            # REST & WebSocket 入口
│   │   ├── filter/                # 限流、鉴权过滤器
│   │   ├── protocol/              # 统一消息格式定义
│   │   └── session/               # 会话管理
│   └── src/main/resources/
├── agent-core/                    # Agent 核心引擎 (Python/FastAPI)
│   ├── nlu/                       # 意图识别 & 实体抽取
│   ├── dm/                        # 对话管理器 & 状态机
│   ├── skill/                     # 技能编排引擎
│   ├── risk/                      # 风控路由 & 规则引擎
│   ├── rag/                       # RAG 检索增强
│   └── prompt/                    # Prompt 模板管理
├── backend/                       # 后端支撑服务 (Java/Spring Boot)
│   ├── business-api/              # 电商业务 API 适配层
│   ├── ticket/                    # 工单 & 审批服务
│   ├── notification/              # 通知服务
│   └── knowledge/                 # 知识库管理服务
├── workstation/                   # 坐席工作台前端 (React)
│   ├── src/
│   │   ├── pages/                 # 工单列表、审批、对话接管
│   │   ├── components/            # 上下文透传、推荐话术组件
│   │   └── websocket/             # WS 实时推送
│   └── ...
├── client-sdk/                    # 用户端 SDK / 嵌入组件
│   ├── h5/                        # H5 网页端聊天组件
│   └── app-h5/                    # APP 内嵌 H5 聊天组件
├── infra/                         # 基础设施配置
│   ├── docker/                    # Dockerfile
│   ├── k8s/                       # Kubernetes manifests / Helm charts
│   ├── rocketmq/                  # MQ topic & consumer 配置
│   └── sql/                       # DDL & 迁移脚本
├── scripts/                       # 开发 & 部署脚本
│   ├── setup.sh
│   └── seed-data.sh
├── .github/                       # CI/CD
│   └── workflows/
│       ├── ci.yml
│       └── deploy.yml
├── .gitignore
└── LICENSE
```

---

## 快速开始

### 前置条件

- JDK 17+
- Python 3.11+
- Node.js 18+
- Docker & Docker Compose
- MySQL 8.x / Redis 7.x / RocketMQ 5.x（本地开发可用 Docker Compose 起全套）

### 1. 克隆项目

```bash
git clone https://github.com/your-org/smartcs-agent.git
cd smartcs-agent
```

### 2. 启动基础设施

```bash
cd infra/docker
docker compose up -d          # 启动 MySQL、Redis、RocketMQ、Milvus、ES
```

### 3. 初始化数据库

```bash
cd infra/sql
mysql -u root -p < 01-schema.sql      # 建库建表
mysql -u root -p < 02-seed-data.sql   # 初始化技能注册表等
```

### 4. 启动 Agent 核心服务

```bash
cd agent-core
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env               # 编辑 .env 填入 LLM API Key 等配置
uvicorn app.main:app --reload --port 8001
```

### 5. 启动后端支撑服务

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 6. 启动接入网关

```bash
cd gateway
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 7. 启动坐席工作台

```bash
cd workstation
npm install
cp .env.example .env
npm run dev
```

### 8. 启动用户端 H5

```bash
cd client-sdk/h5
npm install
npm run dev
```

访问 `http://localhost:3000` 打开客服对话界面，`http://localhost:3001` 打开坐席工作台。

---

## 开发规范

### 分支策略

| 分支 | 说明 |
|------|------|
| `main` | 生产分支，保护分支，仅通过 PR 合入 |
| `develop` | 开发主线，日常合并目标 |
| `feature/*` | 功能分支，从 develop 拉出，完成后 PR 回 develop |
| `hotfix/*` | 紧急修复，从 main 拉出，修复合并到 main 和 develop |
| `release/*` | 发版分支，从 develop 拉出，测试通过后合入 main |

### 提交规范（Conventional Commits）

```
<type>(<scope>): <subject>

type: feat | fix | docs | style | refactor | perf | test | chore | ci
scope: gateway | agent-core | backend | workstation | client-sdk | infra
```

示例：
```
feat(agent-core): add slot filling retry logic for order.modify_address
fix(gateway): resolve WebSocket reconnection race condition
docs: update architecture diagram for risk routing
```

### 代码风格

- **Java**：遵循 Alibaba Java Coding Guidelines，使用 Checkstyle 校验
- **Python**：遵循 PEP 8，使用 Black + Ruff 格式化和检查
- **TypeScript**：遵循 ESLint + Prettier 配置
- **SQL**：关键字大写、表名蛇形命名、必须包含注释

### 代码审查

- 所有合入 `develop` / `main` 的代码必须经过至少 1 人 Review
- 涉及风控路由、技能编排的变更需 Tech Lead + 业务方 Review
- PR 描述必须包含：变更说明、测试方式、影响范围

---

## 文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 需求文档 | [./requirements/AI客服Agent需求文档.md](../AI客服Agent需求文档.md) | 完整功能需求和非功能需求定义 |
| 架构设计文档 | [./docs/架构设计文档.md](./docs/架构设计文档.md) | 五层架构、核心模块设计、数据模型、部署方案 |
| API 文档 | 启动后访问 Swagger UI | 网关 `/swagger-ui.html`、Agent Core `/docs` |

---

## License

[MIT](./LICENSE)
