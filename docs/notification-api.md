# Notification Event API

本文档记录 `smartcs-notification` 当前阶段的最小通知事件能力。

当前实现目标是让 Workbench 在审批、人工接管动作完成后，额外投递一条通知事件，便于后续接入站内信、短信、坐席提醒或消息队列。当前阶段不引入 RocketMQ、Redis 或数据库表。

## 服务

- 服务名：`smartcs-notification`
- 默认端口：`8085`
- 本地健康检查：`GET http://localhost:8085/api/health`
- 本地依赖：无新增中间件

## 事件接收

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
  "content": "你的退款申请已通过人工审核...",
  "payload": {
    "approvalStatus": "APPROVED",
    "workOrderStatus": "APPROVED"
  },
  "occurredAt": "2026-06-25T03:00:00Z"
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
    "acceptedAt": "2026-06-25T03:00:00Z"
  },
  "traceId": "trace_xxx"
}
```

## Workbench 投递点

Workbench 当前会在以下动作成功后投递通知事件：

- `APPROVAL_APPROVED`
- `APPROVAL_REJECTED`
- `TAKEOVER_STARTED`
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
