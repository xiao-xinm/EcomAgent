# Notification Event API

本文档记录 `smartcs-notification` 当前阶段的通知事件能力。

当前目标是让 Workbench 在审批、人工接管、人工消息等动作完成后投递通知事件，并由 Notification 服务落库，便于后续接入站内信、短信、坐席提醒、失败重试或消息队列。当前阶段不引入 RocketMQ、Redis 或 WebSocket。

## 服务信息

- 服务名：`smartcs-notification`
- 默认端口：`8085`
- 健康检查：`GET http://localhost:8085/api/health`
- 本地依赖：MySQL
- 初始化脚本：
  - `infra/sql/10-notification-event-store.sql`
  - `infra/sql/11-notification-delivery-status.sql`

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
- 这只是状态记录，不会自动执行真实重试；真实重试调度后续再评估是否接入 RocketMQ 或定时任务。

## Workbench 投递点

Workbench 当前会在以下动作成功后投递通知事件：

- `APPROVAL_APPROVED`
- `APPROVAL_REJECTED`
- `APPROVAL_MATERIALS_REQUESTED`
- `APPROVAL_TRANSFERRED_TO_TAKEOVER`
- `TAKEOVER_STARTED`
- `TAKEOVER_MESSAGE_SENT`
- `TAKEOVER_FINISHED`

通知投递是辅助链路：

- Workbench 仍先写工单状态、操作日志、审计日志和用户可见消息。
- Notification 调用失败只写 warn 日志。
- Notification 调用失败不会回滚审批或人工接管操作。

## Workbench 配置

```yaml
smartcs:
  notification:
    enabled: ${SMARTCS_NOTIFICATION_ENABLED:true}
    base-url: ${SMARTCS_NOTIFICATION_BASE_URL:http://localhost:8085}
```

本地如果暂时不启动 `smartcs-notification`，Workbench 主流程仍可运行，只会在日志中看到通知投递失败的 warning。
