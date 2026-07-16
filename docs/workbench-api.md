# SmartCS Workbench API

本文档描述 `smartcs-workbench` 第一阶段人工坐席后端接口，供 `frontend/workstation` 前端实现使用。

当前接口只覆盖最小闭环：

```text
Agent Core 创建人工工单
-> 坐席工作台查看工单
-> 坐席领取
-> 审批通过 / 驳回
-> 人工接管开始 / 结束
-> 查看操作日志
```

不包含真实退款、换货、订单系统调用。

## 1. 基础信息

- 服务：`smartcs-workbench`
- 本地端口：`8083`
- Base URL：`http://localhost:8083`
- 响应类型：`application/json;charset=UTF-8`
- 请求类型：`application/json;charset=UTF-8`

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

分页响应：

```ts
interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}
```

常见错误码：

| code | 含义 |
| --- | --- |
| `0000` | 成功 |
| `1001` | 请求参数错误 |
| `1002` | 未登录或身份缺失 |
| `1003` | 权限不足 |
| `1004` | 工单不存在 |
| `4002` | 当前状态不允许执行该操作 |

## 2. 身份头兼容说明

Phase 8 第一轮已在 Workbench 增加坐席身份上下文兼容层。当前仍兼容请求体里的 `operatorId`，后续会逐步改为以 Token 或可信身份头为准。

推荐后续生产形态：

```http
Authorization: Bearer <access_token>
```

当前本地开发可使用：

```http
X-SmartCS-Operator-Id: agent_001
X-SmartCS-Roles: AGENT
```

Workbench 操作接口的兼容规则：

- 请求头 `X-SmartCS-Principal-Id` + `X-SmartCS-Principal-Type=AGENT|SUPERVISOR|ADMIN` 优先级最高。
- 其次使用开发头 `X-SmartCS-Operator-Id`。
- 如果没有身份头，继续使用请求体 `operatorId`，并标记为 `LEGACY_BODY`。
- `GET /api/workbench/me` 没有请求体，本地开发会回退到 `agent_001`，并标记为 `DEV_FALLBACK`。
- 本阶段不强制鉴权，不校验 JWT 签名，不引入 Redis Session。
- 坐席前端已将 `401` / `1002` 映射为“登录已过期”，将 `403` / `1003` 映射为“没有权限执行该操作”。
- 坐席前端会基于 `CurrentOperatorView.principalType`、`roles` 和 `operatorId` 控制领取、审批、接管、人工消息和内部备注按钮。
- 后端操作接口会校验解析后的坐席角色，非 `AGENT` / `SUPERVISOR` / `ADMIN` 返回 `1003`；如果请求已携带标准身份头且身份类型不是坐席身份，不允许再回退使用请求体 `operatorId`。

## 3. 状态枚举

### WorkOrderStatus

```ts
type WorkOrderStatus =
  | 'PENDING'
  | 'ASSIGNED'
  | 'PROCESSING'
  | 'APPROVED'
  | 'REJECTED'
  | 'RESOLVED'
  | 'CLOSED'
  | 'ESCALATED';
```

### ApprovalStatus

```ts
type ApprovalStatus =
  | 'PENDING'
  | 'CLAIMED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'ESCALATED';
```

### TakeoverStatus

```ts
type TakeoverStatus =
  | 'REQUESTED'
  | 'QUEUED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CANCELLED';
```

### RouteDecision

```ts
type RouteDecision =
  | 'AUTO_REPLY'
  | 'AUTO_EXECUTE'
  | 'CONFIRM_BEFORE_EXECUTE'
  | 'HUMAN_REVIEW'
  | 'HUMAN_TAKEOVER'
  | 'REJECT';
```

## 4. 数据模型

### TicketSummary

```ts
interface TicketSummary {
  ticketId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  intent: string;
  riskLevel: 'L0' | 'L1' | 'L2' | 'L3';
  routeDecision: RouteDecision;
  status: WorkOrderStatus;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  assignedAgent?: string | null;
  reason?: string | null;
  slaDeadline?: string | null;
  createdAt: string;
  updatedAt: string;
  approvalId?: string | null;
  approvalType?: 'REFUND' | 'EXCHANGE' | 'ADDRESS_CHANGE' | 'ORDER_CANCEL' | 'COMPENSATION' | 'OTHER' | null;
  approvalStatus?: ApprovalStatus | null;
  takeoverId?: string | null;
  takeoverStatus?: TakeoverStatus | null;
}
```

### TicketStatsView

```ts
interface TicketStatsView {
  total: number;
  pending: number;
  processing: number;
  completed: number;
  overdueRisk: number;
}
```

统计口径：

- `total`：全部工单数量。
- `pending`：待处理工单，包含 `PENDING`、`ASSIGNED`、`ESCALATED`。
- `processing`：处理中工单，包含 `PROCESSING`。
- `completed`：已完成工单，包含 `APPROVED`、`REJECTED`、`RESOLVED`、`CLOSED`。
- `overdueRisk`：`slaDeadline` 已过期且工单未进入终态的数量。

### CurrentOperatorView

```ts
interface CurrentOperatorView {
  operatorId: string;
  principalType: 'AGENT' | 'SUPERVISOR' | 'ADMIN' | string;
  roles: string[];
  authSource: 'STANDARD_HEADER' | 'DEV_HEADER' | 'LEGACY_BODY' | 'DEV_FALLBACK' | string;
}
```

### TicketDetail

```ts
interface TicketDetail {
  ticket: WorkOrderView;
  approval?: ApprovalTaskView | null;
  takeover?: HumanTakeoverView | null;
  messages: MessageView[];
  actions: ActionLogView[];
}
```

### WorkOrderView

```ts
interface WorkOrderView extends TicketSummary {
  contextSnapshot: Record<string, unknown>;
  resolution: Record<string, unknown>;
  resolvedAt?: string | null;
}
```

### ApprovalTaskView

```ts
interface ApprovalTaskView {
  approvalId: string;
  ticketId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  intent: string;
  approvalType: string;
  riskLevel: string;
  routeDecision: RouteDecision;
  status: ApprovalStatus;
  priority: string;
  assignedReviewer?: string | null;
  riskReason?: string | null;
  requestPayload: Record<string, unknown>;
  contextSnapshot: Record<string, unknown>;
  approvalResult: Record<string, unknown>;
  expireAt?: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
}
```

当 `approvalType = "REFUND"` 时，`requestPayload` 使用退款申请上下文结构：

```ts
interface RefundRequestPayload {
  businessType: "REFUND";
  content: string;
  orderNo?: string;
  refundReason?: string;
  refundAmount?: number | string;
  evidencePlaceholders: Array<{
    type: "IMAGE" | "FILE" | string;
    label: string;
    required: boolean;
    status: "NOT_PROVIDED" | "UPLOADED" | string;
  }>;
  userRequest: string;
  mock?: boolean;
}
```

说明：

- `orderNo`、`refundReason`、`refundAmount` 当前由 Agent Core 从用户原始诉求中做轻量提取，无法识别时允许为空。
- `evidencePlaceholders` 仅表示坐席端需要看到的凭证占位，不代表已上传真实凭证。
- 当前阶段仍不调用真实支付、退款或订单系统，审批结果只完成工单流转和用户消息回写。

当 `approvalType = "EXCHANGE"` 时，`requestPayload` 使用换货申请上下文结构：

```ts
interface ExchangeRequestPayload {
  businessType: "EXCHANGE";
  content: string;
  orderNo?: string;
  productName?: string;
  exchangeReason?: string;
  expectedHandling?: string;
  evidencePlaceholders: Array<{
    type: "IMAGE" | "FILE" | string;
    label: string;
    required: boolean;
    status: "NOT_PROVIDED" | "UPLOADED" | string;
  }>;
  userRequest: string;
  mock?: boolean;
}
```

说明：

- `orderNo`、`productName`、`exchangeReason`、`expectedHandling` 当前由 Agent Core 从用户原始诉求中做轻量提取，无法识别时允许为空。
- `evidencePlaceholders` 仅表示坐席端需要看到的换货凭证占位，不代表已上传真实凭证。
- 当前阶段仍不调用真实仓储、物流或换货系统，审批结果只完成工单流转和用户消息回写。

### HumanTakeoverView

```ts
interface HumanTakeoverView {
  takeoverId: string;
  ticketId?: string | null;
  traceId: string;
  sessionId: string;
  userId: string;
  triggerSource: string;
  status: TakeoverStatus;
  priority: string;
  assignedAgent?: string | null;
  reason?: string | null;
  contextSnapshot: Record<string, unknown>;
  startedAt?: string | null;
  endedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}
```

### MessageView

```ts
interface MessageView {
  messageId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  role: 'USER' | 'AGENT' | 'HUMAN_AGENT' | 'SYSTEM';
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'CARD' | 'ACTION' | 'SYSTEM';
  content?: string | null;
  quickActions: unknown[];
  intent?: string | null;
  riskLevel?: string | null;
  routeDecision?: RouteDecision | null;
  metadata: Record<string, unknown>;
  createdAt: string;
}
```

### ActionLogView

```ts
interface ActionLogView {
  actionId: string;
  source: 'WORK_ORDER' | 'APPROVAL';
  ticketId: string;
  traceId: string;
  operatorId: string;
  actionType: string; // 工单内部备注使用 INTERNAL_NOTE
  beforeStatus?: string | null;
  afterStatus?: string | null;
  comment?: string | null;
  actionData: Record<string, unknown>;
  createdAt: string;
}
```

### ActionResult

```ts
interface ActionResult {
  ticketId: string;
  workOrderStatus: WorkOrderStatus;
  approvalStatus?: ApprovalStatus | null;
  takeoverStatus?: TakeoverStatus | null;
  message: string;
}
```

### TicketChangedEvent

坐席端 SSE 只发送“工单发生变化”的轻量通知，页面收到通知后仍通过现有列表、统计或详情接口读取完整数据。

```ts
interface TicketChangedEvent {
  eventId: string;
  ticketId: string;
  status: WorkOrderStatus;
  assignedAgent?: string | null;
  changedAt: string;
}
```

事件事实来源：

- `work_order.updated_at`：发现新工单和工单状态、分配坐席等变化。
- `audit_log.occurred_at`：发现内部备注、人工消息等不一定修改工单状态的坐席动作。
- 同一轮扫描中同一工单的多条变化会合并为一次通知；SSE 不承诺逐动作投递，完整事实仍以查询接口和数据库为准。

### InternalNoteRequest

```ts
interface InternalNoteRequest {
  operatorId: string;
  comment: string;
  payload?: Record<string, unknown>;
}
```

### TakeoverMessageRequest

```ts
interface TakeoverMessageRequest {
  operatorId: string;
  content: string;
  payload?: Record<string, unknown>;
}
```

## 5. 接口列表

### 5.1 健康检查

```http
GET /api/health
```

返回 `ServiceHealth`，用于确认服务启动。

### 5.2 当前坐席信息

```http
GET /api/workbench/me
```

响应：

```json
{
  "operatorId": "agent_001",
  "principalType": "AGENT",
  "roles": ["AGENT"],
  "authSource": "DEV_FALLBACK"
}
```

用途：

- 前端后续移除固定 `DEFAULT_OPERATOR_ID` 时，用该接口获取当前坐席。
- `authSource` 用于开发期排查当前身份来源。

### 5.3 工单列表

```http
GET /api/workbench/tickets
```

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `status` | 否 | 工单状态，如 `PENDING` |
| `routeDecision` | 否 | 路由决策，如 `HUMAN_REVIEW` |
| `riskLevel` | 否 | 风险等级，如 `L3` |
| `intent` | 否 | 精确匹配意图，如 `refund.apply` |
| `priority` | 否 | 优先级，如 `HIGH` |
| `assignedAgent` | 否 | 坐席 ID |
| `createdAtFrom` | 否 | 创建时间起始，建议格式 `YYYY-MM-DD HH:mm:ss` |
| `createdAtTo` | 否 | 创建时间结束，建议格式 `YYYY-MM-DD HH:mm:ss` |
| `keyword` | 否 | 模糊搜索 ticketId/sessionId/userId/intent |
| `pageNo` | 否 | 默认 `1` |
| `pageSize` | 否 | 默认 `20`，最大 `200` |

响应：

```ts
ApiResponse<PageResult<TicketSummary>>
```

示例：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8083/api/workbench/tickets?status=PENDING&riskLevel=L3&intent=refund.apply&pageNo=1&pageSize=20"
```

### 5.4 工单统计

```http
GET /api/workbench/tickets/stats
```

响应：

```ts
ApiResponse<TicketStatsView>
```

用于坐席工作台列表页顶部展示总工单、待处理、处理中、已完成和超时风险。

### 5.5 工单详情

```http
GET /api/workbench/tickets/{ticketId}
```

响应：

```ts
ApiResponse<TicketDetail>
```

详情包含：

- 工单主体
- 关联审批任务
- 关联人工接管记录
- 当前会话最近消息
- 工单操作日志

### 5.6 工单操作日志

```http
GET /api/workbench/tickets/{ticketId}/actions
```

响应：

```ts
ApiResponse<ActionLogView[]>
```

包含 `work_order_action` 与 `approval_action` 的合并时间线。

前端坐席工作台应按审计视图展示：

- 来源：`WORK_ORDER` 显示为工单，`APPROVAL` 显示为审批。
- 动作：将 `ASSIGN`、`APPROVE`、`REJECT`、`TAKEOVER`、`INTERNAL_NOTE` 等转换为中文标签。
- 状态流转：当 `beforeStatus` 和 `afterStatus` 同时存在时，展示 `beforeStatus -> afterStatus`。
- 备注：展示 `comment`，用于坐席处理说明或内部协作记录。
- 动作数据：`actionData` 保留为可展开 JSON，用于排查 payload、子动作和审计上下文。

### 5.7 添加内部备注

```http
POST /api/workbench/tickets/{ticketId}/notes
```

请求：

```ts
interface InternalNoteRequest {
  operatorId: string;
  comment: string;
  payload?: Record<string, unknown>;
}
```

示例：

```json
{
  "operatorId": "agent_001",
  "comment": "用户要求主管复核退款凭证",
  "payload": {
    "source": "ticket-detail"
  }
}
```

效果：

- `work_order_action` 新增一条 `INTERNAL_NOTE` 操作记录。
- `audit_log` 新增一条 `WORK_ORDER_INTERNAL_NOTE` 审计事件。
- 不写入 `cs_message`，用户端不可见，仅用于坐席内部协作和后续跟进。

响应：

```ts
ApiResponse<ActionResult>
```

已有数据库需要先执行：

```sql
source infra/sql/08-work-order-internal-note-action.sql;
```

### 5.8 领取工单

```http
POST /api/workbench/tickets/{ticketId}/claim
```

请求：

```ts
interface OperatorActionRequest {
  operatorId: string;
  comment?: string;
  payload?: Record<string, unknown>;
}
```

示例：

```json
{
  "operatorId": "agent_001",
  "comment": "开始处理",
  "payload": {}
}
```

效果：

- `work_order.status` 变为 `PROCESSING`
- `work_order.assigned_agent` 变为当前坐席
- 如果有关联审批任务，审批状态变为 `CLAIMED`
- 如果有关联接管记录，接管状态变为 `ASSIGNED`
- 写入操作日志和审计日志

响应：

```ts
ApiResponse<ActionResult>
```

### 5.9 审批通过

```http
POST /api/workbench/tickets/{ticketId}/approval/approve
```

请求：

```ts
interface ApprovalDecisionRequest {
  operatorId: string;
  comment?: string;
  decisionType?: "APPROVED" | "REJECTED" | "REQUEST_MATERIALS" | "TRANSFER_TAKEOVER";
  result?: Record<string, unknown>;
}
```

示例：

```json
{
  "operatorId": "agent_001",
  "comment": "符合退款条件，同意",
  "decisionType": "APPROVED",
  "result": {
    "manualReason": "用户申请合理"
  }
}
```

效果：

- `approval_task.status` 变为 `APPROVED`
- `work_order.status` 变为 `APPROVED`
- 写入审批动作、工单动作和审计日志
- 默认模式下由 Workbench 写入一条 `SYSTEM` 用户可见消息；解耦迁移模式下由 Notification `USER_SESSION` 通道幂等写入

### 5.10 审批驳回

```http
POST /api/workbench/tickets/{ticketId}/approval/reject
```

请求同审批通过。

效果：

- `approval_task.status` 变为 `REJECTED`
- `work_order.status` 变为 `REJECTED`
- 写入审批动作、工单动作和审计日志
- 用户端会话新增一条 `SYSTEM` 用户可见消息，写入方由当前用户消息交付模式决定

### 5.11 要求补充材料

```http
POST /api/workbench/tickets/{ticketId}/approval/request-materials
```

请求：

```json
{
  "operatorId": "agent_001",
  "comment": "请上传商品破损照片",
  "decisionType": "REQUEST_MATERIALS",
  "result": {
    "requiredMaterials": "商品破损照片"
  }
}
```

效果：

- `approval_task.approval_result` 写入结构化结论。
- `approval_task.status` 保持当前开放状态，方便用户补充材料后继续审核。
- 写入审批动作和审计日志。
- `cs_message` 新增一条 `SYSTEM` 用户可见消息，用户端通过会话消息列表可看到补充材料要求。

### 5.12 审批转人工接管

```http
POST /api/workbench/tickets/{ticketId}/approval/transfer-takeover
```

请求：

```json
{
  "operatorId": "agent_001",
  "comment": "情况复杂，转人工继续沟通",
  "decisionType": "TRANSFER_TAKEOVER",
  "result": {
    "transferReason": "需要进一步核实商品状态"
  }
}
```

效果：

- `approval_task.status` 变为 `ESCALATED`，`approval_result` 写入结构化结论。
- `work_order.status` 变为 `ESCALATED`。
- 如果不存在 `human_takeover`，后端会创建一条 `HUMAN_ASSIGNMENT` 接管记录；开放接管记录会分配给当前坐席。
- 写入审批动作、工单动作和审计日志。
- `cs_message` 新增一条 `SYSTEM` 用户可见消息，提示用户已转人工继续处理。

### 5.13 开始人工接管

```http
POST /api/workbench/tickets/{ticketId}/takeover/start
```

请求：

```ts
interface OperatorActionRequest {
  operatorId: string;
  comment?: string;
  payload?: Record<string, unknown>;
}
```

效果：

- 如果不存在 `human_takeover`，后端会创建一条 `HUMAN_ASSIGNMENT` 接管记录
- `human_takeover.status` 变为 `IN_PROGRESS`
- `work_order.status` 变为 `PROCESSING`
- `cs_session.status` 变为 `HUMAN_TAKEOVER`
- 写入操作日志和审计日志
- `cs_message` 新增一条 `HUMAN_AGENT` 用户可见消息，提示人工客服已接入

### 5.14 发送人工接管消息

```http
POST /api/workbench/tickets/{ticketId}/takeover/messages
```

请求：

```ts
interface TakeoverMessageRequest {
  operatorId: string;
  content: string;
  payload?: Record<string, unknown>;
}
```

示例：

```json
{
  "operatorId": "agent_001",
  "content": "我正在帮你核实订单状态，请稍等。",
  "payload": {
    "source": "ticket-detail"
  }
}
```

约束：

- 仅当 `human_takeover.status = IN_PROGRESS` 时允许发送。
- 消息最终写入 `cs_message`，角色为 `HUMAN_AGENT`；迁移模式下由 Notification 根据稳定 `eventId` 幂等写入。
- 用户端 H5 / APP H5 继续通过 `GET /api/chat/sessions/{sessionId}/messages` 轮询看到该消息。
- 同步写入 `work_order_action` 和 `audit_log`，用于坐席操作追踪。

响应：

```ts
ApiResponse<ActionResult>
```

### 5.15 结束人工接管

```http
POST /api/workbench/tickets/{ticketId}/takeover/finish
```

请求：

```ts
interface TakeoverFinishRequest {
  operatorId: string;
  comment?: string;
  resolutionStatus?: 'RESOLVED' | 'CANCELLED';
  result?: Record<string, unknown>;
}
```

示例：

```json
{
  "operatorId": "agent_001",
  "comment": "已人工处理完成",
  "resolutionStatus": "RESOLVED",
  "result": {}
}
```

效果：

- `human_takeover.status` 变为 `RESOLVED` 或 `CANCELLED`
- `work_order.status` 变为 `RESOLVED` 或 `CLOSED`
- `cs_session.status` 变为 `CLOSED`
- 写入操作日志和审计日志
- `cs_message` 新增一条 `HUMAN_AGENT` 用户可见消息，用户端通过会话消息列表可看到人工处理结束结果

### 5.16 查询通知 outbox 运行摘要

```http
GET /api/workbench/notifications/outbox/summary
```

该接口使用与工单读取接口相同的坐席身份边界，只允许 `AGENT`、`SUPERVISOR` 或 `ADMIN`。标准 `CUSTOMER` 身份返回业务错误码 `1003`。

响应：

```ts
interface NotificationOutboxSummary {
  enabled: boolean;
  notificationEnabled: boolean;
  userMessageDeliveryMode: "DIRECT" | "NOTIFICATION";
  pending: number;
  retryableFailed: number;
  exhaustedFailed: number;
  sent: number;
  due: number;
  leased: number;
  oldestDueAt?: string;
}
```

字段说明：

- `enabled`：Workbench outbox worker 是否开启。
- `notificationEnabled`：Workbench 是否允许向 Notification 发布事件。
- `userMessageDeliveryMode`：`DIRECT` 表示 Workbench 事务内直写用户消息；`NOTIFICATION` 表示由 Notification 异步写入。
- `pending`：全部 `PENDING` 事件，包括当前被其他 worker 租用的事件。
- `retryableFailed`：失败次数尚未达到 `max-attempts` 的 `FAILED` 事件。
- `exhaustedFailed`：失败次数已经达到 `max-attempts` 的 `FAILED` 事件。
- `due`：当前已到期、未被有效租约占用且可立即领取的事件。
- `leased`：当前被有效租约占用的开放事件。
- `oldestDueAt`：当前可领取事件中最早的到期时间；没有积压时为 `null`。

Workbench outbox 默认关闭。关闭时接口返回 `enabled=false` 和零计数，但仍返回当前 Notification 开关与用户消息交付模式，不访问 outbox 表；开启后必须先完成 12、14 号 SQL 迁移。

### 5.17 订阅工单变化事件

```http
GET /api/workbench/tickets/events
Accept: text/event-stream
```

该接口默认关闭。Workbench 设置 `SMARTCS_WORKBENCH_TICKET_SSE_ENABLED=true` 后才会注册接口和后台增量扫描任务。

事件类型：

| event | data | 说明 |
| --- | --- | --- |
| `stream.ready` | `operatorId`、`connectedAt` | 连接已建立，前端应立即刷新一次当前页面数据 |
| `ticket.changed` | `TicketChangedEvent` | 工单或其坐席动作发生变化 |
| `heartbeat` | `timestamp` | 连接保活，不代表业务数据变化 |

`ticket.changed` 示例：

```text
event: ticket.changed
id: audit_evt_123
data: {"eventId":"audit_evt_123","ticketId":"wo_123","status":"PROCESSING","assignedAgent":"agent_001","changedAt":"2026-07-16T08:00:00Z"}
```

行为边界：

- 该接口使用与工单读取接口相同的坐席身份边界，只允许 `AGENT`、`SUPERVISOR` 或 `ADMIN`。
- 该接口是增量提示通道，不替代 `GET /api/workbench/tickets`、统计和详情接口。
- 前端收到事件后合并短时间内的重复通知，再调用原有查询接口。
- SSE 断开时浏览器可自动重连；关闭 SSE 后，手动刷新和操作后刷新仍然可用。
- 当前原生 `EventSource` 不能附加 `Authorization` Header；生产严格鉴权开启时，前端必须保持坐席 SSE 关闭，等待账号中心提供 Cookie/BFF 或其他长连接鉴权方案。
- 事件通过共享 MySQL 增量扫描产生，因此当前多实例不需要 Redis Pub/Sub 或 RocketMQ；它是提醒通道，不提供消息队列级别的可靠投递保证。

## 6. 前端实现建议

当前可以实现坐席工作台最小页面：

- 工单列表页
- 工单详情抽屉或详情页
- 会话消息区域
- 审批信息区
- 操作日志时间线
- 领取、审批通过、审批驳回、要求补充材料、转人工接管、开始接管、结束接管按钮

不要实现真实退款、换货、订单接口调用。

前端接口 Base URL 使用环境变量：

```text
VITE_WORKSTATION_API_BASE_URL=http://localhost:8083
```

如果后续通过 Gateway 聚合，再统一切换为 Gateway 地址。
