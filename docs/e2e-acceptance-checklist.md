# SmartCS-Agent 端到端验收清单

本文档用于当前最小闭环的回归验收。目标是每次迭代后快速确认：

- 用户端 H5 可以发消息、查会话、查消息。
- Agent 可以完成自动回复、确认后执行、人工审核、人工接管四类路由。
- Skill Engine 可以写入技能执行记录。
- Workbench 可以处理工单，并把处理结果回写到用户会话消息。

## 1. 前置条件

### 1.1 数据库

MySQL 已启动，账号密码：

```text
username=root
password=root
database=smartcs_agent
```

按顺序执行数据库脚本：

```powershell
cd D:\NewProject\EcomAgent

mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/01-schema.sql
mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/02-skill-schema.sql
mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/03-enforce-enum-columns.sql
mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/04-risk-approval-schema.sql
mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/06-seed-risk-rules.sql
mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/07-seed-skill-registry.sql
mysql -uroot -proot --default-character-set=utf8mb4 < infra/sql/08-seed-e2e-test-data.sql
```

`08-seed-e2e-test-data.sql` 可重复执行。它只重置固定的 `e2e_*` 测试数据，不清理真实数据。

### 1.2 后端服务

用 IDEA 启动以下服务：

| 服务 | 端口 | 用途 |
| --- | --- | --- |
| `smartcs-gateway` | `8080` | 用户端 H5 API 入口 |
| `smartcs-agent-core` | `8081` | Agent 编排 |
| `smartcs-skill-engine` | `8082` | 技能执行 |
| `smartcs-workbench` | `8083` | 人工坐席工作台后端 |

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8081/api/health
Invoke-RestMethod http://localhost:8082/api/health
Invoke-RestMethod http://localhost:8083/api/health
```

预期：四个接口都返回 `code=0000` 或服务健康数据。

## 2. 固定测试数据

执行 `08-seed-e2e-test-data.sql` 后，会生成三组固定数据：

| 场景 | sessionId | ticketId | 预期用途 |
| --- | --- | --- | --- |
| 自动订单查询 | `e2e_s_order_query` | 无 | 验证 H5 查询会话消息和技能执行记录 |
| 待退款审核 | `e2e_s_refund_review` | `e2e_wo_refund_review` | 验证 Workbench 审批通过/驳回 |
| 待人工接管 | `e2e_s_takeover` | `e2e_wo_takeover` | 验证 Workbench 开始/结束人工接管 |

## 3. 用户端聊天链路

### 3.1 自动查询订单

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$body = '{"userId":"u1001","channel":"h5","content":"我的订单"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

$reply = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/messages `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes

$reply.data
```

预期：

- `routeDecision = AUTO_REPLY`
- `riskLevel = L0`
- `metadata.skillExecutionId` 有值
- `metadata.skillExecutionStatus = SUCCEEDED`

继续查询会话消息：

```powershell
$sessionId = $reply.data.sessionId
Invoke-RestMethod "http://localhost:8080/api/chat/sessions/$sessionId/messages?limit=100"
```

预期：能看到一条 `USER` 消息和一条 `AGENT` 消息。

### 3.2 修改地址确认后执行

```powershell
$body = '{"userId":"u1001","channel":"h5","content":"我的订单已经发货了，想修改地址"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

$reply = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/messages `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes

$reply.data
```

预期：

- `routeDecision = CONFIRM_BEFORE_EXECUTE`
- `riskLevel = L2`
- `quickActions` 包含 `CONFIRM` 和 `CANCEL`

确认继续：

```powershell
$sessionId = $reply.data.sessionId
$body = "{`"sessionId`":`"$sessionId`",`"userId`":`"u1001`",`"channel`":`"h5`",`"actionId`":`"confirm`",`"actionType`":`"CONFIRM`",`"content`":`"确认继续`"}"
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

$actionReply = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/actions `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes

$actionReply.data
```

预期：

- `routeDecision = AUTO_EXECUTE`
- `metadata.skillExecutionId` 有值
- 会话状态变为 `dialogState=COMPLETED`

## 4. 人工审核链路

### 4.1 查询待审核工单

```powershell
Invoke-RestMethod "http://localhost:8083/api/workbench/tickets?status=PENDING&routeDecision=HUMAN_REVIEW&pageNo=1&pageSize=20"
```

预期：

- 返回列表包含 `ticketId=e2e_wo_refund_review`
- `approvalStatus=PENDING`

### 4.2 领取工单

```powershell
$body = '{"operatorId":"agent_001","comment":"领取退款审核"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8083/api/workbench/tickets/e2e_wo_refund_review/claim `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

预期：

- `workOrderStatus = PROCESSING`
- `approvalStatus = CLAIMED`

### 4.3 审批通过并回写用户消息

```powershell
$body = '{"operatorId":"agent_001","comment":"符合退款条件","result":{"manualReason":"E2E审核通过"}}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8083/api/workbench/tickets/e2e_wo_refund_review/approval/approve `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

预期：

- `workOrderStatus = APPROVED`
- `approvalStatus = APPROVED`
- 查询用户消息时，能看到一条 `SYSTEM` 消息。

```powershell
Invoke-RestMethod "http://localhost:8080/api/chat/sessions/e2e_s_refund_review/messages?limit=100"
```

预期消息内容包含：

```text
你的退款申请已通过人工审核
```

## 5. 人工接管链路

### 5.1 查询待接管工单

```powershell
Invoke-RestMethod "http://localhost:8083/api/workbench/tickets?status=PENDING&routeDecision=HUMAN_TAKEOVER&pageNo=1&pageSize=20"
```

预期：

- 返回列表包含 `ticketId=e2e_wo_takeover`
- `takeoverStatus=REQUESTED`

### 5.2 开始人工接管

```powershell
$body = '{"operatorId":"agent_001","comment":"开始人工处理"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8083/api/workbench/tickets/e2e_wo_takeover/takeover/start `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

预期：

- `workOrderStatus = PROCESSING`
- `takeoverStatus = IN_PROGRESS`
- 用户消息列表新增一条 `HUMAN_AGENT` 消息，内容包含 `人工客服已接入`

### 5.3 结束人工接管

```powershell
$body = '{"operatorId":"agent_001","comment":"问题已处理完成","resolutionStatus":"RESOLVED","result":{"manualResult":"E2E人工处理完成"}}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8083/api/workbench/tickets/e2e_wo_takeover/takeover/finish `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

预期：

- `workOrderStatus = RESOLVED`
- `takeoverStatus = RESOLVED`
- 用户消息列表再新增一条 `HUMAN_AGENT` 消息，内容包含 `人工客服已处理完成`

```powershell
Invoke-RestMethod "http://localhost:8080/api/chat/sessions/e2e_s_takeover/messages?limit=100"
```

## 6. 前端联调

### 6.1 H5 用户端

启动：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run dev:client-h5
```

预期：

- 页面地址：`http://localhost:3000`
- 输入 `我的订单`，能收到 Agent 回复。
- 输入 `我要退款`，能收到人工审核提示。
- Workbench 审批后，刷新或轮询消息列表能看到坐席处理结果。

### 6.2 Workbench 坐席台

启动：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run dev:workstation
```

预期：

- 页面地址：`http://localhost:3001`
- 能看到 `e2e_wo_refund_review` 和 `e2e_wo_takeover`
- 能领取、审批、开始接管、结束接管
- 操作后详情页消息区和操作日志更新

## 7. 回归通过标准

本轮最小闭环通过的标准：

- 四个后端健康检查可访问。
- `我的订单` 返回 `AUTO_REPLY` 且包含 `skillExecutionId`。
- `修改地址` 返回确认动作，确认后返回 `AUTO_EXECUTE`。
- `退款` 能进入 `HUMAN_REVIEW`，Workbench 可审批。
- `人工接管` 能进入 `HUMAN_TAKEOVER`，Workbench 可开始和结束。
- Workbench 的审批/接管结果能回写到 H5 会话消息。
- 后端日志能看出 traceId、sessionId、ticketId、skillExecutionId。

## 8. 当前刻意不验收的内容

以下内容还没有进入当前最小闭环，不作为本轮通过标准：

- 真实订单表查询。
- 真实地址修改。
- 真实退款或换货业务执行。
- WebSocket/SSE 实时推送。
- 真实大模型意图识别。
- RAG 知识库问答。
