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
