# SmartCS 生产部署配置基线

本文档定义当前阶段的生产配置准入，不负责安装中间件或启动应用进程。实际密钥应由部署平台、系统环境变量或密钥管理服务注入，不得提交到 Git。

## 1. 生成配置

项目提供不含真实密钥的模板：

```text
infra/env/backend-production.env.example
```

本地准备时可复制为已被 `.gitignore` 忽略的文件：

```powershell
Copy-Item .\infra\env\backend-production.env.example .\infra\env\backend-production.env
```

替换所有 `<replace-with-...>` 占位符，并按实际网络修改数据库、内部服务、浏览器来源、pgvector 和 Elasticsearch 地址。不同进程可以由部署平台只注入自己使用的变量；校验文件保留完整集合，用于检查跨服务开关是否一致。

## 2. 部署前校验

```powershell
.\scripts\check-deployment-config.ps1 -Path .\infra\env\backend-production.env
```

返回码：

- `0`：配置通过，可进入应用启动和健康检查阶段。
- `1`：配置被拒绝，必须先处理所有 `[fail]` 项。

校验器不会连接外部服务，也不会打印密钥内容。它会拒绝：

- MySQL `root` 账号、常见开发密码和 localhost 数据库地址。
- 通配 CORS、HTTP 浏览器来源和本地开发来源。
- 未同时开启严格鉴权与 JWT，或长度不足 32 字符的 JWT 密钥。
- 指向 localhost 的后端服务拓扑。
- `hybrid` 模式缺少 pgvector、Elasticsearch 或 DashScope 配置。
- Notification 重试与 `USER_SESSION` 通道只开启一侧。
- 关闭 Workbench 用户消息直写，但 outbox 或 Notification 投递链路没有完整开启。

脚本回归测试不需要 Pester：

```powershell
.\scripts\test-deployment-config.ps1
```

应用进程全部启动后，再检查运行时实际状态：

```powershell
.\scripts\check-runtime-readiness.ps1 `
  -WorkbenchBaseUrl https://workbench-api.example.com `
  -KnowledgeBaseUrl https://knowledge-api.example.com `
  -NotificationBaseUrl https://notification-api.example.com
```

配置文件校验只能发现静态组合错误；运行时检查还会验证 Knowledge 依赖可用性与索引一致性，以及通知链路是否存在配置断点或重试耗尽。脚本只读取摘要接口，不打印密钥，不创建业务数据。

## 3. 首次上线模式

首次部署保持当前稳定的同步用户消息模式：

```text
SMARTCS_NOTIFICATION_ENABLED=true
SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true
SMARTCS_NOTIFICATION_OUTBOX_ENABLED=false
SMARTCS_NOTIFICATION_RETRY_ENABLED=false
SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED=false
```

该模式不依赖 RocketMQ。Workbench 在事务内写用户消息，并同步向 Notification 记录辅助事件。

## 4. 异步灰度

只有执行完通知相关 MySQL 迁移并完成进程级烟测后，才进入灰度：

1. 保持 `SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true`。
2. 开启 Workbench outbox、Notification retry 和 `USER_SESSION` 通道。
3. 观察 Workbench Outbox 与 Notification Delivery 摘要，确认 `due`、`exhaustedFailed` 和 `oldestDueAt` 无持续增长。
4. 执行 `scripts/check-runtime-readiness.ps1 -ExpectedNotificationState CUTOVER_READY`，确认灰度链路完整且无积压。
5. 将 `SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=false` 并重启 Workbench。
6. 执行 `scripts/check-runtime-readiness.ps1 -ExpectedNotificationState ASYNC_ACTIVE`，确认用户消息所有权已切换到 Notification。
7. 执行 `scripts/smoke-notification-user-session.ps1 -Mode Preflight`，确认运行进程已加载完整异步配置。
8. 执行 `scripts/smoke-notification-user-session.ps1` 验证真实投递和幂等重放。

直写与 outbox 同时开启属于迁移观察状态，校验器会给出警告但不会阻止部署。

## 5. 回滚顺序

异步链路异常时按以下顺序回滚：

1. 先恢复 `SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true` 并重启 Workbench。
2. 执行 `scripts/check-runtime-readiness.ps1 -ExpectedNotificationState CUTOVER_READY`，确认直写已恢复且异步链路仍可观察。
3. 验证新工单消息已重新直接写入 `cs_message`。
4. 再关闭 Workbench outbox、Notification retry 和 `USER_SESSION` 通道。
5. 保留 outbox 与 notification_event 数据用于排障，不直接删除积压记录。

先恢复直写可以避免用户消息交付空窗。回滚后继续通过两个摘要接口确认旧积压状态。

## 6. 中间件边界

当前生产配置基线使用：

- MySQL 8.x。
- Knowledge `hybrid` 模式所需的 PostgreSQL + pgvector、Elasticsearch 和 DashScope。

本阶段不新增 Redis、RocketMQ、Nacos、Prometheus、Grafana 或 OpenTelemetry。后续要启用其中任何一项，必须先补充用途、版本、端口、认证、数据持久化和回滚方案。
