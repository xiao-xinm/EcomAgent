# SmartCS Chat API

本文档描述用户端 H5 / APP H5 通过 Gateway 调用的聊天接口。

当前服务地址：

```text
http://localhost:8080
```

统一响应包：

```ts
interface ApiResponse<T> {
  code: string;
  message: string;
  data: T | null;
  traceId: string;
  metadata: Record<string, unknown>;
  timestamp: string;
}
```

## 1. 发送自然语言消息

```http
POST /api/chat/messages
Content-Type: application/json; charset=utf-8
```

请求：

```ts
interface ChatRequest {
  traceId?: string;
  sessionId?: string;
  userId: string;
  channel?: string;
  content: string;
  metadata?: Record<string, unknown>;
}
```

示例：

```json
{
  "userId": "u1001",
  "channel": "h5",
  "content": "我的订单"
}
```

说明：

- `traceId` 不传时由 Gateway 生成。
- `sessionId` 不传时由 Gateway 生成。
- 返回的 `data.sessionId` 需要前端保存，后续快捷动作必须继续传这个 `sessionId`。

## 2. 处理快捷动作

```http
POST /api/chat/actions
Content-Type: application/json; charset=utf-8
```

用于处理 Agent 回复中的 `quickActions`，例如“确认继续”和“取消”。

请求：

```ts
interface ChatActionRequest {
  traceId?: string;
  sessionId: string;
  userId: string;
  channel?: string;
  actionId?: string;
  actionType: 'CONFIRM' | 'CANCEL' | string;
  content?: string;
  payload?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}
```

确认继续示例：

```json
{
  "sessionId": "s_xxx",
  "userId": "u1001",
  "channel": "h5",
  "actionId": "confirm",
  "actionType": "CONFIRM",
  "content": "确认继续"
}
```

修改地址确认示例：

当 `currentIntent = order.modify_address` 时，前端需要在确认动作的 `payload` 中传入订单号和新地址。当前后端不会从第一次自然语言消息中保存完整地址字段，所以确认动作必须带完整 payload。

```json
{
  "sessionId": "s_xxx",
  "userId": "u1001",
  "channel": "h5",
  "actionId": "confirm",
  "actionType": "CONFIRM",
  "content": "确认修改地址",
  "payload": {
    "orderNo": "E2E-ORDER-1002",
    "newAddress": {
      "consigneeName": "测试用户A",
      "consigneePhone": "13800008888",
      "province": "上海市",
      "city": "上海市",
      "district": "浦东新区",
      "addressDetail": "联调路 8888 号",
      "postalCode": "200120"
    },
    "changeReason": "user confirmed address change"
  }
}
```

字段说明：

- `orderNo`：订单号。也兼容 `order_no`；如后续前端拿到内部订单 ID，也可以传 `orderId` / `order_id`。
- `newAddress.consigneeName`：收货人姓名，必填。
- `newAddress.consigneePhone`：收货人手机号，必填。
- `newAddress.province`：省份，必填。
- `newAddress.city`：城市，必填。
- `newAddress.district`：区县，必填。
- `newAddress.addressDetail`：详细地址，必填。
- `newAddress.postalCode`：邮编，选填。
- `changeReason`：地址变更原因，选填。

修改地址成功时，响应 `data.routeDecision = AUTO_EXECUTE`，`data.content` 会返回类似“已为订单 E2E-ORDER-1002 修改收货地址。”，`data.metadata` 中会包含 `skillExecutionId` 和 `skillExecutionStatus`。

如果地址字段不完整，Skill Engine 会返回 `missingFields`，Agent 回复会提示“请补充完整的新收货地址后再确认。”；如果订单不允许自动改地址，会返回 `requiresHuman = true`，当前最小闭环会提示转人工处理，后续再接入自动创建人工工单。

取消示例：

```json
{
  "sessionId": "s_xxx",
  "userId": "u1001",
  "channel": "h5",
  "actionId": "cancel",
  "actionType": "CANCEL",
  "content": "取消"
}
```

## 3. 当前动作语义

### CONFIRM

仅当当前会话处于 `WAITING_CONFIRM` 时生效。

后端会：

- 写入一条用户 `ACTION` 消息。
- 根据会话 `current_intent` 找到技能。
- 调用 Skill Engine 执行技能。
- 将会话状态更新为 `COMPLETED`。
- 返回一条 Agent 文本回复。

如果上下文不完整或找不到技能，后端会转人工接管。

### CANCEL

后端会：

- 写入一条用户 `ACTION` 消息。
- 记录技能取消日志。
- 将会话状态更新为 `COMPLETED`。
- 返回“已取消本次操作”。

## 4. 查询会话状态

```http
GET /api/chat/sessions/{sessionId}
```

用于用户端 H5 刷新或轮询当前会话状态。人工审核、人工接管、坐席处理完成后，前端可以通过该接口拿到最新状态。

响应：

```ts
interface ChatSessionView {
  sessionId: string;
  traceId: string;
  userId: string;
  channel: string;
  status: 'ACTIVE' | 'HUMAN_TAKEOVER' | 'TIMEOUT' | 'CLOSED' | string;
  dialogState:
    | 'INIT'
    | 'INTENT_RECOGNIZED'
    | 'SLOT_FILLING'
    | 'READY_TO_EXECUTE'
    | 'WAITING_CONFIRM'
    | 'EXECUTING'
    | 'EXECUTED'
    | 'COMPLETED'
    | 'HUMAN_REVIEW'
    | 'HUMAN_TAKEOVER'
    | 'TIMEOUT'
    | 'CLOSED'
    | string;
  currentIntent?: string | null;
  slots: Record<string, unknown>;
  contextSnapshot: Record<string, unknown>;
  lastMessageAt?: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt?: string | null;
}
```

示例：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/chat/sessions/s_xxx"
```

## 5. 查询会话消息

```http
GET /api/chat/sessions/{sessionId}/messages?limit=100
```

用于用户端 H5 拉取当前会话消息列表。`limit` 可选，默认 `100`，最大 `200`。

消息列表会包含同一会话下的所有用户可见消息，包括 `USER`、`AGENT`、`SYSTEM` 和 `HUMAN_AGENT`。当坐席在 Workbench 完成审批通过、审批驳回、人工接入或人工处理结束时，后端会追加一条 `SYSTEM` 或 `HUMAN_AGENT` 消息，H5 通过本接口刷新即可看到人工处理结果。

响应：

```ts
interface ChatMessageView {
  messageId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  role: 'USER' | 'AGENT' | 'HUMAN_AGENT' | 'SYSTEM' | string;
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'CARD' | 'ACTION' | 'SYSTEM' | string;
  content?: string | null;
  attachments: unknown[];
  quickActions: unknown[];
  intent?: string | null;
  riskLevel?: 'L0' | 'L1' | 'L2' | 'L3' | null;
  routeDecision?:
    | 'AUTO_REPLY'
    | 'AUTO_EXECUTE'
    | 'CONFIRM_BEFORE_EXECUTE'
    | 'HUMAN_REVIEW'
    | 'HUMAN_TAKEOVER'
    | 'REJECT'
    | null;
  metadata: Record<string, unknown>;
  createdAt: string;
}
```

示例：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/chat/sessions/s_xxx/messages?limit=100"
```

## 6. 测试流程

先发送一个会触发确认的消息，例如：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$body = '{"userId":"u1001","channel":"h5","content":"修改收货地址，订单号 E2E-ORDER-1002"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

$reply = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/messages `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes

$reply.data
```

预期：

```text
routeDecision = CONFIRM_BEFORE_EXECUTE
quickActions  = 确认继续 / 取消
```

再用返回的 `sessionId` 携带新地址确认继续：

```powershell
$sessionId = $reply.data.sessionId
$body = @"
{
  "sessionId": "$sessionId",
  "userId": "u1001",
  "channel": "h5",
  "actionId": "confirm",
  "actionType": "CONFIRM",
  "content": "确认修改地址",
  "payload": {
    "orderNo": "E2E-ORDER-1002",
    "newAddress": {
      "consigneeName": "测试用户A",
      "consigneePhone": "13800008888",
      "province": "上海市",
      "city": "上海市",
      "district": "浦东新区",
      "addressDetail": "联调路 8888 号",
      "postalCode": "200120"
    },
    "changeReason": "manual test"
  }
}
"@
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/actions `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

确认成功时，响应 `data.metadata` 中会包含：

```text
skillExecutionId
skillExecutionStatus
```

同时 `skill_execution_log` 会新增一条 `AUTO_EXECUTE` 的执行记录。

最后可以查询会话状态和消息：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/chat/sessions/$sessionId"

Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/chat/sessions/$sessionId/messages?limit=100"
```
