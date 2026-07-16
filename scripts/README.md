# scripts

开发、检查、部署脚本目录。

## smoke-e2e.ps1

`smoke-e2e.ps1` 用于本地最小闭环烟测。默认假设你已经用 IDEA 启动：

- Gateway：`8080`
- Agent Core：`8081`
- Skill Engine：`8082`
- Workbench：`8083`
- Knowledge：`8084`
- Notification：`8085`

执行：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\smoke-e2e.ps1
```

覆盖入口级检查：

- FAQ 自动回复
- 查订单
- 用户会话归属校验：当前用户可拉取自己的消息，其他用户访问同一 `sessionId` 返回 `1003`
- 查物流
- 取消订单确认
- 退款进入人工审核
- 人工客服进入人工接管

脚本会临时插入一条取消订单测试数据，验证后删除。若当前环境没有 `mysql` 命令，可跳过取消订单的数据准备：

```powershell
.\scripts\smoke-e2e.ps1 -SkipOrderCancelDbSetup
```

如需指定跨用户归属校验中的“另一个用户”：

```powershell
.\scripts\smoke-e2e.ps1 -OtherUserId u2002
```

## check-local-stack.ps1

`check-local-stack.ps1` 用于联调前快速确认本地服务是否都已启动。它只检查服务健康和前端入口，不执行业务请求，也不修改数据库。

默认检查：

- Gateway：`http://localhost:8080/api/health`
- Agent Core：`http://localhost:8081/api/health`
- Skill Engine：`http://localhost:8082/api/health`
- Workbench：`http://localhost:8083/api/health`
- Knowledge：`http://localhost:8084/api/health`
- Notification：`http://localhost:8085/api/health`
- 用户端 H5：`http://localhost:3000`
- 坐席工作台：`http://localhost:3001`
- APP H5：`http://localhost:3002`

执行：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\check-local-stack.ps1
```

只检查后端：

```powershell
.\scripts\check-local-stack.ps1 -SkipFrontend
```

只检查前端：

```powershell
.\scripts\check-local-stack.ps1 -SkipBackend
```

未启动 APP H5 时跳过 `3002`：

```powershell
.\scripts\check-local-stack.ps1 -SkipAppH5
```

## check-deployment-config.ps1

`check-deployment-config.ps1` 在启动生产进程前离线校验后端环境变量文件。它不会连接数据库或外部服务，也不会输出密钥值。

先基于模板生成未提交的实际配置，再执行：

```powershell
Copy-Item .\infra\env\backend-production.env.example .\infra\env\backend-production.env
.\scripts\check-deployment-config.ps1 -Path .\infra\env\backend-production.env
```

校验覆盖数据库账号、CORS、严格 JWT、内部服务地址、Knowledge 混合检索依赖，以及 Workbench outbox 与 Notification 投递开关组合。模板中的密钥占位符必须替换后才能通过。

脚本自身回归测试：

```powershell
.\scripts\test-deployment-config.ps1
```

## check-runtime-readiness.ps1

`check-runtime-readiness.ps1` 在 Workbench、Knowledge 和 Notification 启动后只读检查运行进程实际加载的配置与依赖状态：

- Knowledge `keyword` 模式要求 MySQL 可用；`hybrid` 模式还要求 Elasticsearch、pgvector 健康且三端文档数一致。
- `NOTIFICATION` 用户消息模式要求 Workbench outbox、Notification 发布、retry worker 和 `USER_SESSION` 通道完整开启。
- 任一侧出现重试耗尽时返回失败；仍可自动处理的待投递或重试积压会显示 `ASYNC_BACKLOG`，但不会误判为进程不可用。

执行实时检查：

```powershell
.\scripts\check-runtime-readiness.ps1
```

脚本不会修改配置、索引、通知事件或数据库。它也支持 `-SnapshotPath` 读取脱敏 JSON 快照，便于离线复盘和脚本回归。

运行 6 种状态组合的离线测试：

```powershell
.\scripts\test-runtime-readiness.ps1
```

## smoke-knowledge-hybrid.ps1

`smoke-knowledge-hybrid.ps1` 用于 Phase 5 混合检索验收。执行前必须：

- 在 `smartcs_knowledge` PostgreSQL 数据库执行 `infra/postgres/01-knowledge-vector-schema.sql`。
- Knowledge IDEA 配置已设置 ES、pgvector、`DASHSCOPE_API_KEY`。
- `SMARTCS_RETRIEVAL_MODE=hybrid`。
- Knowledge 服务运行在 `8084`。

执行：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\smoke-knowledge-hybrid.ps1
```

脚本会执行全量索引重建，并验证精确问题双路命中和语义改写问题的向量召回来源。

## smoke-notification-user-session.ps1

`smoke-notification-user-session.ps1` 用于验证 Workbench outbox 到 Notification `USER_SESSION` 通道的用户消息解耦。执行前需要：

- MySQL 已启动并已执行 `10`、`11`、`12` 号通知 SQL。
- Workbench 开启 outbox，并关闭用户消息直写。
- Notification 同时开启重试 worker 和 `USER_SESSION` 通道。

两个服务均已启动时执行完整烟测：

```powershell
.\scripts\smoke-notification-user-session.ps1 -Mode Preflight
.\scripts\smoke-notification-user-session.ps1
```

`Preflight` 只读检查 Workbench outbox、`NOTIFICATION` 消息模式、Notification retry worker 和 `USER_SESSION` 通道，不创建测试数据。完整烟测和恢复验证也会先执行同样的配置检查。

预检判断的纯脚本回归测试：

```powershell
.\scripts\test-notification-smoke-preflight.ps1
```

验证服务中断与恢复时，先停止 Notification，保持 Workbench 运行：

```powershell
.\scripts\smoke-notification-user-session.ps1 -Mode PrepareRecovery
```

再启动 Notification，并执行：

```powershell
.\scripts\smoke-notification-user-session.ps1 -Mode VerifyRecovery
```

脚本覆盖 outbox 持久化、最终投递、消息角色和重复事件幂等。成功后自动清理测试数据；异常中断后可执行 `-Mode Cleanup` 清理状态文件对应的数据。
