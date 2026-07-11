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
│ Redis │ PostgreSQL+pgvector │ ES │ RocketMQ │ 监控组件    │
└─────────────────────────────────────────────────────────┘
```

📖 详细架构设计见 [架构设计文档](./docs/架构设计文档.md)

---

## 当前工程落地说明

当前仓库已调整为前后端分离结构：

- `backend/`：Java / Spring Boot 后端聚合工程，包含网关、Agent Core、技能引擎、坐席后端、知识库、通知和公共模块。
- `frontend/`：前端工程集合，包含用户端 H5、APP 内嵌 H5 和坐席工作台。
- `docs/`、`requirements/`、`infra/`、`scripts/`：作为项目级文档、需求、基础设施和脚本入口。

本阶段只搭建框架、目录、依赖和文档契约，不包含订单、退款、换货、修改地址等业务实现代码。

---

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| **前端（用户端）** | React / Vue 3 + TypeScript、WebSocket (SockJS)、TailwindCSS |
| **前端（坐席工作台）** | React 18 + Ant Design Pro、WebSocket 实时推送 |
| **Agent 核心** | Java 17 / Spring Boot 3、Spring Statemachine、LLM 适配器 / 自研 Agent 框架 |
| **NLU / LLM** | Qwen-Max（主） / GPT-4o（备）、DashScope `text-embedding-v4`（Embedding） |
| **后端服务** | Java 17 / Spring Boot 3、MyBatis-Plus、Spring Security + JWT |
| **消息中间件** | RocketMQ 5.x |
| **缓存 & 状态** | Redis 7.x Cluster |
| **向量库** | PostgreSQL 16 + pgvector 0.8.x |
| **搜索引擎** | Elasticsearch 8.x + `analysis-smartcn` |
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
├── backend/                       # 后端聚合工程 (Java/Spring Boot)
│   ├── pom.xml                    # Maven 聚合入口
│   ├── smartcs-gateway/           # 接入网关 / 会话管理
│   ├── smartcs-agent-core/        # Agent 核心编排
│   ├── smartcs-skill-engine/      # 技能注册与声明式编排
│   ├── smartcs-workbench/         # 坐席工作台后端 / 人工审核
│   ├── smartcs-knowledge/         # 知识库 / RAG
│   ├── smartcs-notification/      # 通知服务
│   └── smartcs-common/            # 公共契约与工具
├── frontend/                      # 前端工程集合
│   ├── client-h5/                 # 用户端 H5 聊天组件 (Vue 3)
│   ├── app-h5/                    # APP 内嵌 H5 聊天组件
│   ├── workstation/               # 坐席工作台前端
│   └── package.json               # 前端 workspace 入口
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
- Node.js 18+
- Docker & Docker Compose
- MySQL 8.x（核心业务数据）
- Phase 5 混合检索需要 PostgreSQL 16 + pgvector、Elasticsearch 8.x 和 DashScope Embedding；Redis、RocketMQ 当前不强制启用

### 1. 克隆项目

```bash
git clone https://github.com/your-org/smartcs-agent.git
cd smartcs-agent
```

### 2. 启动基础设施

```bash
cd infra/docker
docker compose up -d          # 启动 MySQL
```

如果你已经有可用的本地或虚拟机 MySQL，也可以跳过这一步，后端默认连接：

```text
jdbc:mysql://localhost:3306/smartcs_agent
username=root
password=root
```

### 3. 初始化数据库

```bash
cd infra/sql
mysql -u root -p < 01-schema.sql
mysql -u root -p smartcs_agent < 02-skill-schema.sql
mysql -u root -p smartcs_agent < 03-enforce-enum-columns.sql
mysql -u root -p smartcs_agent < 04-risk-approval-schema.sql
mysql -u root -p smartcs_agent < 06-seed-risk-rules.sql
mysql -u root -p smartcs_agent < 07-seed-skill-registry.sql
mysql -u root -p smartcs_agent < 08-work-order-internal-note-action.sql
mysql -u root -p smartcs_agent < 09-knowledge-faq-management.sql
mysql -u root -p smartcs_agent < 10-notification-event-store.sql
mysql -u root -p smartcs_agent < 11-notification-delivery-status.sql
```

使用 `infra/docker/compose.yml` 首次启动空数据卷时，会自动执行上述 SQL；已有数据库只需要按缺失阶段补跑。

### 4. 构建后端骨架

```bash
cd backend
mvn clean install
```

### 5. 启动后端服务

```bash
cd backend
mvn -pl smartcs-gateway spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl smartcs-agent-core spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl smartcs-workbench spring-boot:run -Dspring-boot.run.profiles=dev
```

### 6. 安装前端依赖

```bash
cd frontend
npm install
```

### 7. 启动用户端 H5

```bash
npm run dev:client-h5
```

### 8. 启动坐席工作台前端骨架

```bash
npm run dev:workstation
```

页面实现后，默认规划为 `http://localhost:3000` 打开客服对话界面，`http://localhost:3001` 打开坐席工作台。

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
scope: gateway | agent-core | backend | frontend | workstation | infra
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
| 项目路线图 | [./docs/project-roadmap.md](./docs/project-roadmap.md) | 后续开发阶段、执行流程、验收要求和中间件准入规则 |
| 本地启动 Runbook | [./docs/local-development-runbook.md](./docs/local-development-runbook.md) | 本地 MySQL、后端、前端、健康检查和烟测排障步骤 |
| 验收记录模板 | [./docs/e2e-validation-template.md](./docs/e2e-validation-template.md) | 每轮端到端联调和页面验收的标准记录模板 |
| 日志字段规范 | [./docs/observability-log-fields.md](./docs/observability-log-fields.md) | Phase 9 统一日志字段和后续观测迁移规则 |
| API 文档 | 启动后访问 Swagger UI | 网关 `/swagger-ui.html`、Agent Core `/docs` |

---

## License

[MIT](./LICENSE)
