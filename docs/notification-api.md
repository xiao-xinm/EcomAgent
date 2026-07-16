# Notification Event API

本文档记录 `smartcs-notification` 当前阶段的通知事件能力。

当前目标是让 Workbench 在审批、人工接管、人工消息等动作完成后投递通知事件，并由 Notification 服务落库，便于后续接入站内信、短信、坐席提醒、失败重试或消息队列。当前阶段不引入 RocketMQ、Redis 或 WebSocket。

MQ 是否引入、何时引入以及 RocketMQ 候选设计见 [notification-mq-evaluation.md](./notification-mq-evaluation.md)。

## 服务信息

- 服务名：`smartcs-notification`
- 默认端口：`8085`
- 健康检查：`GET http://localhost:8085/api/health`
- 本地依赖：MySQL
- 初始化脚本：
  - `infra/sql/10-notification-event-store.sql`
  - `infra/sql/11-notification-delivery-status.sql`
  - 可选 Workbench outbox：`infra/sql/12-workbench-notification-outbox.sql`
  - Notification worker 租约：`infra/sql/13-notification-delivery-lease.sql`
  - Workbench outbox worker 租约：`infra/sql/14-workbench-outbox-delivery-lease.sql`

## 接收通知事件

```http
POST /api/notifications/events
Content-Type: application/json; charset=utf-8
```

请求体：

```json
{
  "eventId": "ntf_xxx",
  "traceId": "trace_xxx",
  "sourceService": "smartcs-workbench",
  "eventType": "APPROVAL_APPROVED",
  "recipientUserId": "u1001",
  "sessionId": "s_xxx",
  "ticketId": "wo_xxx",
  "operatorId": "agent001",
  "channel": "USER_SESSION",
  "title": "审批通过通知",
  "content": "你的售后申请已通过人工审核。",
  "payload": {
    "messageRole": "SYSTEM",
    "userMessageDeliveryMode": "DIRECT",
    "approvalStatus": "APPROVED",
    "workOrderStatus": "APPROVED"
  },
  "occurredAt": "2026-07-05T01:00:00Z"
}
```

响应体：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "eventId": "ntf_xxx",
    "status": "ACCEPTED",
    "channel": "USER_SESSION",
    "acceptedAt": "2026-07-05T01:00:01Z"
  },
  "traceId": "trace_xxx"
}
```

说明：

- `eventId` 不传时由 Notification 服务生成。
- `channel` 不传时默认为 `USER_SESSION`。
- `occurredAt` 不传时使用服务接收时间。
- 当前仅代表事件已被 Notification 接收并落库，真实站内信、短信或推送还未接入。

## 查询通知事件

```http
GET /api/notifications/events?pageNo=1&pageSize=20&eventType=APPROVAL_APPROVED&ticketId=wo_xxx&recipientUserId=u1001&status=ACCEPTED
```

查询参数：

- `eventType`：可选，事件类型。
- `ticketId`：可选，工单 ID。
- `recipientUserId`：可选，用户 ID。
- `status`：可选，事件状态。
- `pageNo`：可选，默认 `1`。
- `pageSize`：可选，默认 `20`，最大 `100`。

响应体：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "records": [
      {
        "eventId": "ntf_xxx",
        "traceId": "trace_xxx",
        "sourceService": "smartcs-workbench",
        "eventType": "APPROVAL_APPROVED",
        "recipientUserId": "u1001",
        "sessionId": "s_xxx",
        "ticketId": "wo_xxx",
        "operatorId": "agent001",
        "channel": "USER_SESSION",
        "title": "审批通过通知",
        "content": "你的售后申请已通过人工审核。",
        "payload": {
          "approvalStatus": "APPROVED",
          "workOrderStatus": "APPROVED"
        },
        "status": "ACCEPTED",
        "retryCount": 0,
        "lastError": null,
        "nextRetryAt": null,
        "deliveredAt": null,
        "occurredAt": "2026-07-05T01:00:00Z",
        "acceptedAt": "2026-07-05T01:00:01Z",
        "createdAt": "2026-07-05T01:00:01Z",
        "updatedAt": "2026-07-05T01:00:01Z"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 20
  },
  "traceId": "..."
}
```

## 查询投递运行摘要

```http
GET /api/notifications/events/delivery-summary
```

响应：

```ts
interface NotificationDeliverySummary {
  enabled: boolean;
  userSessionChannelEnabled: boolean;
  accepted: number;
  retryableFailed: number;
  exhaustedFailed: number;
  delivered: number;
  due: number;
  leased: number;
  oldestDueAt?: string;
}
```

字段说明：

- `enabled`：Notification retry worker 是否开启。
- `userSessionChannelEnabled`：`USER_SESSION` 用户会话消息通道是否开启。
- `accepted`：全部 `ACCEPTED` 事件，包括当前被 worker 租用的事件。
- `retryableFailed`：重试次数尚未达到 `max-attempts` 的 `FAILED` 事件。
- `exhaustedFailed`：重试次数已经达到 `max-attempts` 的 `FAILED` 事件。
- `delivered`：已完成真实通道投递的事件。
- `due`：当前已到期、未被有效租约占用且可立即领取的事件。
- `leased`：当前被有效租约占用的开放事件。
- `oldestDueAt`：当前可领取事件中最早的到期时间；没有积压时为 `null`。

Notification retry worker 默认关闭。关闭时接口返回 `enabled=false` 和零计数，但仍返回 `USER_SESSION` 通道开关，不访问 13 号迁移新增的租约字段；开启前必须完成 11、13 号 SQL 迁移。

## 回写投递结果

当前阶段还没有真实短信、站内信或 MQ 消费器。该接口先作为投递状态骨架，用于后续投递 worker 或消息消费者回写结果。

```http
POST /api/notifications/events/{eventId}/delivery-result
Content-Type: application/json; charset=utf-8
```

失败回写示例：

```json
{
  "status": "FAILED",
  "errorMessage": "站内信通道暂不可用",
  "nextRetryAt": "2026-07-05T01:10:00Z"
}
```

成功回写示例：

```json
{
  "status": "DELIVERED"
}
```

响应体：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "eventId": "ntf_xxx",
    "status": "FAILED",
    "retryCount": 1,
    "lastError": "站内信通道暂不可用",
    "nextRetryAt": "2026-07-05T01:10:00Z",
    "deliveredAt": null,
    "updatedAt": "2026-07-05T01:00:01Z"
  },
  "traceId": "..."
}
```

说明：

- `status` 只支持 `DELIVERED` 和 `FAILED`。
- `FAILED` 会让 `retryCount + 1`，记录 `lastError` 和 `nextRetryAt`。
- `DELIVERED` 会清空 `lastError` 和 `nextRetryAt`，并记录 `deliveredAt`。
- 该接口只负责显式状态回写；启用内部重试 worker 后，worker 也会直接维护相同状态字段。

## Workbench 投递点

Workbench 当前会在以下动作成功后投递通知事件：

- `APPROVAL_APPROVED`
- `APPROVAL_REJECTED`
- `APPROVAL_MATERIALS_REQUESTED`
- `APPROVAL_TRANSFERRED_TO_TAKEOVER`
- `TAKEOVER_STARTED`
- `TAKEOVER_MESSAGE_SENT`
- `TAKEOVER_FINISHED`

默认直接 HTTP 模式下，通知投递是辅助链路：

- Workbench 仍先写工单状态、操作日志、审计日志和用户可见消息。
- Notification 调用失败只写 warn 日志。
- Notification 调用失败不会回滚审批或人工接管操作。

启用事务 outbox 后，Workbench 不在业务事务内调用 Notification，而是把稳定 `eventId` 和完整事件请求写入 `workbench_notification_outbox`。入队失败会回滚当前工单操作；事务提交后由支持 MySQL 租约的 worker 调用 Notification，失败按有限退避重试。

## Workbench 配置

```yaml
smartcs:
  notification:
    enabled: ${SMARTCS_NOTIFICATION_ENABLED:true}
    base-url: ${SMARTCS_NOTIFICATION_BASE_URL:http://localhost:8085}
    user-message-direct-write-enabled: ${SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED:true}
    outbox:
      enabled: ${SMARTCS_NOTIFICATION_OUTBOX_ENABLED:false}
      fixed-delay-ms: ${SMARTCS_NOTIFICATION_OUTBOX_FIXED_DELAY_MS:30000}
      batch-size: ${SMARTCS_NOTIFICATION_OUTBOX_BATCH_SIZE:20}
      max-attempts: ${SMARTCS_NOTIFICATION_OUTBOX_MAX_ATTEMPTS:5}
      lease-duration-ms: ${SMARTCS_NOTIFICATION_OUTBOX_LEASE_DURATION_MS:120000}
      worker-id: ${SMARTCS_NOTIFICATION_OUTBOX_WORKER_ID:}
```

本地如果暂时不启动 `smartcs-notification`，Workbench 主流程仍可运行，只会在日志中看到通知投递失败的 warning。

启用 outbox 前必须依次执行 `12-workbench-notification-outbox.sql` 和 `14-workbench-outbox-delivery-lease.sql`，并保持 `SMARTCS_NOTIFICATION_ENABLED=true`。outbox worker 处理 `PENDING` 和到期 `FAILED` 事件，按 1、5、15、30 分钟退避，默认最多尝试 5 次。多个实例通过事务内 `FOR UPDATE SKIP LOCKED`、`delivery_owner` 和过期租约安全领取；成功或失败回写必须匹配领取实例。

## 内部自动重试 worker

Notification 服务提供基于 MySQL 的轻量定时投递框架，以及默认关闭的幂等 `USER_SESSION` 用户会话通道。短信、邮件和 APP Push 仍未接入。

启用配置：

```text
SMARTCS_NOTIFICATION_RETRY_ENABLED=false
SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED=false
SMARTCS_NOTIFICATION_RETRY_FIXED_DELAY_MS=30000
SMARTCS_NOTIFICATION_RETRY_BATCH_SIZE=20
SMARTCS_NOTIFICATION_RETRY_MAX_ATTEMPTS=5
SMARTCS_NOTIFICATION_RETRY_LEASE_DURATION_MS=120000
SMARTCS_NOTIFICATION_RETRY_WORKER_ID=
```

执行规则：

- 处理 `ACCEPTED` 事件的首次投递，以及 `next_retry_at <= NOW(3)` 的 `FAILED` 事件。
- 仅选择 `retry_count < maxAttempts` 的事件，每轮最多处理 `batchSize` 条。
- 通过事务内 `FOR UPDATE SKIP LOCKED` 领取事件，并写入 `delivery_owner / delivery_lease_until`。
- 成功或失败回写必须匹配领取 worker；进程异常退出后，其他实例可在租约过期后重新领取。
- 真实投递通道通过 `NotificationDeliveryChannel` 扩展，并按 `channel` 精确匹配。
- 显式开启 worker 但没有注册任何投递通道时，应用会启动失败，不会消费待处理事件。
- 只开启 `USER_SESSION` 通道但未开启 worker 时，应用同样会启动失败。
- 通道必须使用 `eventId` 保证幂等，避免进程重启或重复调度造成重复通知。
- 投递成功后状态变为 `DELIVERED`。
- 投递失败后增加 `retryCount`，默认按 1、5、15、30 分钟退避。
- 达到最大尝试次数后保留 `FAILED`，清空 `nextRetryAt`，等待人工处理。
- Notification worker 和 Workbench outbox worker 均已支持共享 MySQL 的多实例领取与过期租约恢复。

`USER_SESSION` 通道按事件 payload 执行：

- 缺少 `userMessageDeliveryMode`：视为历史 `DIRECT` 事件，只确认交付，不写消息。
- `userMessageDeliveryMode=DIRECT`：消息已由 Workbench 写入，只确认交付。
- `userMessageDeliveryMode=NOTIFICATION`：使用 `eventId` 派生固定 `messageId`，通过主键幂等写入 `cs_message`。
- `messageRole` 只允许 `SYSTEM` 和 `HUMAN_AGENT`。

默认模式不变。完整迁移验证需同时配置 Workbench：

```text
SMARTCS_NOTIFICATION_ENABLED=true
SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true
SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=false
```

以及 Notification：

```text
SMARTCS_NOTIFICATION_RETRY_ENABLED=true
SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED=true
```

建议先启动 Notification，再切换并重启 Workbench。回滚时先恢复 Workbench 直写，再关闭 Notification 通道和 worker。
