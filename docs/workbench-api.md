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
| `1004` | 工单不存在 |
| `4002` | 当前状态不允许执行该操作 |

## 2. 状态枚举

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

## 3. 数据模型

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

## 4. 接口列表

### 4.1 健康检查

```http
GET /api/health
```

返回 `ServiceHealth`，用于确认服务启动。

### 4.2 工单列表

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

### 4.3 工单详情

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

### 4.4 工单操作日志

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

### 4.5 添加内部备注

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

### 4.6 领取工单

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

### 4.7 审批通过

```http
POST /api/workbench/tickets/{ticketId}/approval/approve
```

请求：

```ts
interface ApprovalDecisionRequest {
  operatorId: string;
  comment?: string;
  result?: Record<string, unknown>;
}
```

示例：

```json
{
  "operatorId": "agent_001",
  "comment": "符合退款条件，同意",
  "result": {
    "manualReason": "用户申请合理"
  }
}
```

效果：

- `approval_task.status` 变为 `APPROVED`
- `work_order.status` 变为 `APPROVED`
- 写入审批动作、工单动作和审计日志
- `cs_message` 新增一条 `SYSTEM` 用户可见消息，用户端通过会话消息列表可看到审核通过结果

### 4.8 审批驳回

```http
POST /api/workbench/tickets/{ticketId}/approval/reject
```

请求同审批通过。

效果：

- `approval_task.status` 变为 `REJECTED`
- `work_order.status` 变为 `REJECTED`
- 写入审批动作、工单动作和审计日志
- `cs_message` 新增一条 `SYSTEM` 用户可见消息，用户端通过会话消息列表可看到审核驳回结果

### 4.9 开始人工接管

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

### 4.10 发送人工接管消息

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
- 消息写入 `cs_message`，角色为 `HUMAN_AGENT`。
- 用户端 H5 / APP H5 继续通过 `GET /api/chat/sessions/{sessionId}/messages` 轮询看到该消息。
- 同步写入 `work_order_action` 和 `audit_log`，用于坐席操作追踪。

响应：

```ts
ApiResponse<ActionResult>
```

### 4.11 结束人工接管

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

## 5. 前端实现建议

当前可以实现坐席工作台最小页面：

- 工单列表页
- 工单详情抽屉或详情页
- 会话消息区域
- 审批信息区
- 操作日志时间线
- 领取、审批通过、审批驳回、开始接管、结束接管按钮

不要实现真实退款、换货、订单接口调用。

前端接口 Base URL 使用环境变量：

```text
VITE_WORKSTATION_API_BASE_URL=http://localhost:8083
```

如果后续通过 Gateway 聚合，再统一切换为 Gateway 地址。
