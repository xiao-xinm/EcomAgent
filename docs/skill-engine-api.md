# SmartCS Skill Engine API

本文档描述 `smartcs-skill-engine` 当前阶段的最小接口。它负责读取技能注册表、展示技能定义，并承接 Agent Core 的自动技能执行请求。

当前仍然不调用真实电商订单、退款、换货系统；执行结果是最小 mock，用于打通链路和记录 `skill_execution_log`。

## 1. 基础信息

- 服务：`smartcs-skill-engine`
- 本地端口：`8082`
- Base URL：`http://localhost:8082`
- 响应类型：`application/json;charset=UTF-8`
- 数据库：`smartcs_agent`

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

## 2. 接口列表

### 2.1 健康检查

```http
GET /api/health
```

用于确认 Skill Engine 已启动。

### 2.2 技能列表

```http
GET /api/skills
```

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `intent` | 否 | 按意图过滤，例如 `order.query`、`logistics.query` |
| `status` | 否 | 按技能状态过滤，例如 `ACTIVE` |
| `enabled` | 否 | `true` / `false` |

响应：

```ts
ApiResponse<SkillDefinitionView[]>
```

### 2.3 技能详情

```http
GET /api/skills/{skillId}
```

响应包含技能基础信息、槽位定义和声明式 API 步骤。

### 2.4 执行技能

```http
POST /api/skills/execute
```

请求：

```ts
interface SkillExecuteRequest {
  traceId?: string;
  sessionId: string;
  messageId?: string;
  userId: string;
  intent?: string;
  skillId?: string;
  riskLevel?: 'L0' | 'L1' | 'L2' | 'L3';
  routeDecision?:
    | 'AUTO_REPLY'
    | 'AUTO_EXECUTE'
    | 'CONFIRM_BEFORE_EXECUTE'
    | 'HUMAN_REVIEW'
    | 'HUMAN_TAKEOVER'
    | 'REJECT';
  parameters?: Record<string, unknown>;
  context?: Record<string, unknown>;
}
```

说明：

- `skillId` 和 `intent` 至少提供一个。
- `sessionId` 必须对应已有 `cs_session`，因为执行日志有外键约束。
- `messageId` 可为空；如果提供，必须对应已有 `cs_message`。
- Agent Core 会在自动路径上调用该接口；直接测试时建议先通过聊天接口创建会话和消息。

响应：

```ts
interface SkillExecuteResult {
  executionId: string;
  traceId: string;
  sessionId: string;
  messageId?: string | null;
  skillId: string;
  skillName: string;
  intent: string;
  riskLevel: 'L0' | 'L1' | 'L2' | 'L3';
  routeDecision: string;
  status:
    | 'PENDING'
    | 'SUCCEEDED'
    | 'FAILED'
    | 'CANCELLED'
    | 'REVIEW_REQUIRED';
  response: Record<string, unknown>;
  stepResults: SkillStepResult[];
  message: string;
  startedAt: string;
  finishedAt?: string | null;
}
```

## 3. Agent Core 调用方式

`smartcs-agent-core` 新增配置：

```yaml
smartcs:
  skill-engine:
    base-url: ${SMARTCS_SKILL_ENGINE_BASE_URL:http://localhost:8082}
```

当前只在以下路由决策中调用 Skill Engine：

- `AUTO_REPLY`
- `AUTO_EXECUTE`

人工审核、人工接管、确认后执行路径暂时仍由 Agent Core 本地记录和兜底，避免 Skill Engine 未启动时影响敏感工单闭环。

## 4. 本地联调顺序

在 IDEA 中启动：

```text
smartcs-skill-engine  -> 8082
smartcs-agent-core    -> 8081
smartcs-gateway       -> 8080
smartcs-workbench     -> 8083
```

然后通过 Gateway 测试自动技能路径：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$body = '{"userId":"u1001","content":"查一下我的订单"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/messages `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

如果 Skill Engine 调用成功，返回 `data.metadata` 中会包含：

- `skillExecutionId`
- `skillExecutionStatus`

同时数据库 `skill_execution_log` 会新增一条由 Skill Engine 写入的执行记录。

物流查询同样走自动技能路径：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$body = '{"userId":"u1001","content":"我的物流到哪了"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/messages `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

预期返回 `AUTO_REPLY`，`data.metadata.intent = logistics.query`，并包含 `skillExecutionId`。
