# SmartCS 本地启动与排障 Runbook

本文档用于本地联调 SmartCS 最小闭环。默认开发方式是：MySQL 可由本机、虚拟机 Docker 或 `infra/docker/compose.yml` 提供；后端服务用 IDEA 启动；前端用 npm workspace 启动。

## 1. 本地依赖

必需：

- JDK 17+
- Node.js 18+
- MySQL 8.x

可选：

- Docker Desktop 或 Docker CLI：仅用于启动本地 MySQL。

当前阶段不需要 Redis、RocketMQ、Milvus、Elasticsearch、Prometheus、Grafana。

## 2. 启动 MySQL

如果你已经在本机或虚拟机 Docker 中启动了 MySQL，并且能用 `root/root` 连接 `localhost:3306`，可以跳过本节。

使用项目内 Compose：

```powershell
cd D:\NewProject\EcomAgent\infra\docker
docker compose up -d
docker compose ps
```

默认配置：

```text
host=localhost
port=3306
database=smartcs_agent
username=root
password=root
```

首次启动空数据卷时，Compose 会挂载并执行 `infra/sql/*.sql`。如果已有数据库，只按缺失阶段补跑 SQL。

## 3. 初始化或补齐数据库

如果不使用 Compose 自动初始化，可以手动执行：

```powershell
cd D:\NewProject\EcomAgent\infra\sql
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

## 4. 启动后端服务

推荐用 IDEA 分别启动以下 Spring Boot 应用：

| 服务 | 模块 | 端口 |
|------|------|------|
| Gateway | `smartcs-gateway` | `8080` |
| Agent Core | `smartcs-agent-core` | `8081` |
| Skill Engine | `smartcs-skill-engine` | `8082` |
| Workbench | `smartcs-workbench` | `8083` |
| Knowledge | `smartcs-knowledge` | `8084` |
| Notification | `smartcs-notification` | `8085` |

默认数据库环境变量：

```text
SMARTCS_DB_URL=jdbc:mysql://localhost:3306/smartcs_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SMARTCS_DB_USERNAME=root
SMARTCS_DB_PASSWORD=root
```

如 MySQL 不在本机，修改 IDEA Run Configuration 的 `SMARTCS_DB_URL`。

## 5. 启动前端

```powershell
cd D:\NewProject\EcomAgent\frontend
npm install
npm run dev:client-h5
npm run dev:workstation
npm run dev:app-h5
```

默认入口：

- 用户端 H5：`http://localhost:3000`
- 坐席工作台：`http://localhost:3001`
- APP H5：`http://localhost:3002`

## 6. 健康检查

后端和前端都启动后：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\check-local-stack.ps1
```

如果 APP H5 未启动：

```powershell
.\scripts\check-local-stack.ps1 -SkipAppH5
```

只检查后端：

```powershell
.\scripts\check-local-stack.ps1 -SkipFrontend
```

## 7. 最小烟测

```powershell
cd D:\NewProject\EcomAgent
.\scripts\smoke-e2e.ps1
```

覆盖：

- FAQ 自动回复
- 查订单
- 查物流
- 取消订单确认链路
- 退款人工审核入口
- 人工客服接管入口
- 用户会话归属保护

如果当前环境没有 `mysql` 命令：

```powershell
.\scripts\smoke-e2e.ps1 -SkipOrderCancelDbSetup
```

## 8. 常见问题

### CORS

用户端默认允许：

- `http://localhost:3000`
- `http://127.0.0.1:3000`

坐席端默认允许：

- `http://localhost:3001`
- `http://127.0.0.1:3001`

如端口变更，设置 `SMARTCS_CORS_ALLOWED_ORIGINS`。

### 中文乱码

PowerShell 调接口前设置：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
```

发送 JSON 时使用：

```powershell
-ContentType 'application/json; charset=utf-8'
```

### MySQL 端口冲突

如果 `3306` 已被占用，修改 `infra/docker/.env`：

```text
SMARTCS_MYSQL_PORT=3307
```

然后同步修改后端 `SMARTCS_DB_URL`。

### Docker Compose 未安装

可以继续使用你自己的 MySQL。项目当前除 MySQL 外不依赖其他中间件。

### 日志排障

后端日志统一使用：

- `traceId`
- `sessionId`
- `ticketId`
- `userId`
- `operatorId`
- `eventId`

优先用 `traceId` 串 Gateway、Agent Core、Skill Engine、Workbench、Knowledge、Notification。
