# SmartCS 本地启动与排障 Runbook

本文档用于本地联调 SmartCS。默认开发方式是：MySQL 可由本机、虚拟机 Docker 或 `infra/docker/compose.yml` 提供；Phase 5 混合检索使用虚拟机中的 PostgreSQL + pgvector 和 Elasticsearch；后端服务用 IDEA 启动；前端用 npm workspace 启动。

## 1. 本地依赖

必需：

- JDK 17+
- Node.js 18+
- MySQL 8.x

可选：

- Docker Desktop 或 Docker CLI：用于启动本地或虚拟机中间件。

Phase 5 混合检索还需要：

- PostgreSQL 16 + pgvector 0.8.x
- Elasticsearch 8.x + `analysis-smartcn`
- DashScope `text-embedding-v4`

当前仍不需要 Redis、RocketMQ、Milvus、Prometheus、Grafana。

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
mysql -u root -p smartcs_agent < 12-workbench-notification-outbox.sql
mysql -u root -p smartcs_agent < 13-notification-delivery-lease.sql
mysql -u root -p smartcs_agent < 14-workbench-outbox-delivery-lease.sql
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

Notification 的内部重试 worker 和 `USER_SESSION` 通道默认关闭，普通本地开发无需配置。验证异步用户会话交付时，两项必须同时开启：

```text
SMARTCS_NOTIFICATION_RETRY_ENABLED=true
SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED=true
SMARTCS_NOTIFICATION_RETRY_LEASE_DURATION_MS=120000
```

启用 Notification worker 前必须执行 13 号迁移。多实例部署时每个进程可以设置不同的
`SMARTCS_NOTIFICATION_RETRY_WORKER_ID`；留空会自动生成。租约应长于单次通道调用的最大耗时。

Workbench 通知 outbox 也默认关闭，关闭时保持原有同步 HTTP 辅助链路。需要验证事务 outbox 时，依次执行 `12-workbench-notification-outbox.sql` 和 `14-workbench-outbox-delivery-lease.sql`，同时启动 Workbench 与 Notification，再设置：

```text
SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true
SMARTCS_NOTIFICATION_ENABLED=true
SMARTCS_NOTIFICATION_OUTBOX_LEASE_DURATION_MS=120000
```

多实例部署时每个 Workbench 进程可以设置不同的 `SMARTCS_NOTIFICATION_OUTBOX_WORKER_ID`；留空会自动生成。租约应长于一次 Notification HTTP 调用的最大耗时。

仅验证 outbox 时，Workbench 仍直接写用户可见 `cs_message`。验证完整解耦迁移时，在上述配置基础上再设置：

```text
SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=false
```

该配置会强制要求 outbox 已开启。建议先启动 Notification，再启动 Workbench；回滚时先将该配置恢复为 `true`，避免出现消息交付空窗。

完整配置启动后，可执行用户会话解耦烟测：

```powershell
.\scripts\smoke-notification-user-session.ps1
```

验证 Notification 中断恢复时，先停止 Notification 并执行 `-Mode PrepareRecovery`，再启动 Notification 并执行 `-Mode VerifyRecovery`。脚本会验证 outbox 恢复、最终投递和重复事件幂等，并在成功后清理测试数据。

Knowledge 混合检索后续使用以下环境变量。密码和 API Key 只能配置在 IDEA、系统环境变量或未提交的本地配置中：

```text
SMARTCS_VECTOR_DB_URL=jdbc:postgresql://<vm-ip>:5432/smartcs_knowledge
SMARTCS_VECTOR_DB_USERNAME=postgres
SMARTCS_VECTOR_DB_PASSWORD=<local-secret>
SMARTCS_ES_URL=http://<vm-ip>:9200
SMARTCS_ES_USERNAME=<local-username>
SMARTCS_ES_PASSWORD=<local-secret>
DASHSCOPE_API_KEY=<local-secret>
SMARTCS_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
SMARTCS_EMBEDDING_MODEL=text-embedding-v4
SMARTCS_EMBEDDING_DIMENSIONS=1024
```

虚拟机端口检查：

```powershell
Test-NetConnection <vm-ip> -Port 5432
Test-NetConnection <vm-ip> -Port 9200
```

首次启用混合检索前，在 PostgreSQL `smartcs_knowledge` 数据库执行：

```text
infra/postgres/01-knowledge-vector-schema.sql
```

确认表结构完成后，将 Knowledge 的 IDEA 环境变量切换为：

```text
SMARTCS_RETRIEVAL_MODE=hybrid
```

重启 Knowledge，先调用全量重建，再运行烟测：

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8084/api/knowledge/faq/index/rebuild
Invoke-RestMethod -Method Get -Uri http://localhost:8084/api/knowledge/faq/index/status
.\scripts\smoke-knowledge-hybrid.ps1
```

索引状态应满足 `healthy=true`、`consistent=true`，并且 MySQL、Elasticsearch、pgvector 的 ACTIVE 文档数一致。

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

Windows PowerShell 5.1 通过管道把 SQL 交给 `mysql.exe` 时，还要显式设置原生进程输出编码，否则脚本中的中文可能在进入 MySQL 前被转换成 `?`：

```powershell
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Get-Content -Raw -Encoding UTF8 .\infra\sql\09-knowledge-faq-management.sql |
    mysql --default-character-set=utf8mb4 -u root -p
```

导入后可通过 FAQ 列表接口确认 `faq_refund_arrival.question` 等字段仍是正常中文，再执行混合索引重建。

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
