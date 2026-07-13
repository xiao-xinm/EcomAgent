# Notification MQ Evaluation

本文档用于记录 SmartCS 通知服务是否需要引入 MQ，以及在不引入 MQ 的阶段如何继续保持通知链路可追踪、可恢复。

当前结论：**Phase 6 暂不引入 RocketMQ**。继续使用 MySQL `notification_event` 作为通知事件存储和排障入口；投递失败由默认关闭的轻量定时 worker 处理，完成真实通道和幂等验证后再启用。

## 1. 当前链路

当前 Workbench 支持两种兼容模式：默认仍在动作成功后通过 HTTP 调用 `smartcs-notification`；显式开启 outbox 后，事件先随业务事务入队，再由 worker 调用 Notification。

```text
Workbench 操作成功
  -> 写工单状态 / 操作日志 / 审计日志 / 用户可见消息
  -> 默认：POST /api/notifications/events
  -> 可选：workbench_notification_outbox -> worker -> POST /api/notifications/events
  -> notification_event 落库
```

当前通知事件状态：

- `ACCEPTED`：Notification 已接收并落库。
- `FAILED`：后续投递通道失败，记录 `retryCount / lastError / nextRetryAt`。
- `DELIVERED`：后续投递通道成功，记录 `deliveredAt`。

当前阶段还没有真实短信、站内信、APP Push 或 MQ 消费器。

## 2. 为什么暂不引入 MQ

当前业务规模和架构阶段还不需要 RocketMQ：

- 通知事件量低，Workbench HTTP 调用 Notification 已满足最小闭环。
- Notification 调用失败不会回滚坐席主流程，主链路风险可控。
- 用户端仍通过短轮询读取 `cs_message`，通知事件目前主要用于审计和后续扩展。
- 已有 `notification_event` 能支持查询、失败记录、重试次数和下次重试时间。

继续保持 MySQL-only 可以减少本地开发和联调成本，也符合当前项目的“最小闭环优先”原则。

## 3. 无 MQ 阶段的重试方案

当前推荐的无 MQ 重试路径：

1. 投递 worker 查询 `notification_event`：
   - `status = 'FAILED'`
   - `next_retry_at IS NULL OR next_retry_at <= NOW(3)`
2. worker 按 `event_type / channel / payload` 调用具体投递通道。
3. 投递成功后调用：
   - `POST /api/notifications/events/{eventId}/delivery-result`
   - body: `{ "status": "DELIVERED" }`
4. 投递失败后调用同一接口：
   - body: `{ "status": "FAILED", "errorMessage": "...", "nextRetryAt": "..." }`
5. 超过最大重试次数后保留 `FAILED`，由人工或后台管理页处理。

建议默认重试策略：

- 第 1 次失败：1 分钟后重试。
- 第 2 次失败：5 分钟后重试。
- 第 3 次失败：15 分钟后重试。
- 第 4 次及以后：30 分钟后重试，或进入人工排查。

当前已提供默认关闭的单实例自动 worker 框架和可插拔 `NotificationDeliveryChannel`。仓库尚未内置真实投递通道，因此保持关闭；接入真实通道并完成幂等验证后才允许启用。

显式开启 worker 时必须至少注册一个真实投递通道，否则 Notification 会启动失败，不会领取待处理事件。

默认配置：

```text
SMARTCS_NOTIFICATION_RETRY_ENABLED=false
SMARTCS_NOTIFICATION_RETRY_FIXED_DELAY_MS=30000
SMARTCS_NOTIFICATION_RETRY_BATCH_SIZE=20
SMARTCS_NOTIFICATION_RETRY_MAX_ATTEMPTS=5
```

## 4. 用户会话消息解耦迁移顺序

Workbench 当前在本地业务事务中直接写 `cs_message`，随后通过 HTTP 发布 Notification 事件。两次写入使用不同的随机 ID，Notification 调用失败也不会回滚坐席操作。这个设计保证当前用户能看到处理结果，但不能直接通过删除 Workbench 写消息来完成解耦。

后续迁移必须按以下顺序进行：

1. 在 Workbench 业务事务内新增本地 outbox 事件，事件 ID 在事务开始时生成并保持稳定。（已完成，默认关闭）
2. 在事件 payload 中补齐用户消息所需的 `role`、`intent`、`riskLevel`、`routeDecision` 和业务 metadata。
3. 为 Notification 增加 `USER_SESSION` 投递通道，使用 `eventId` 派生稳定 `messageId`，重复消费只返回成功、不重复写 `cs_message`。
4. 完成“事务提交、重复投递、Notification 临时不可用、进程重启”四类集成测试。
5. 通过配置灰度关闭 Workbench 直接写消息，端到端验收稳定后再移除旧路径。

当前已完成第 1 步：新增 `workbench_notification_outbox`、兼容发布器和默认关闭的单实例投递 worker；本地 MySQL 已验证表结构，默认模式下 Workbench 启动正常。第 2 至 5 步仍待完成，因此继续保留 Workbench 本地事务内写消息。

这条迁移路径仍只需要 MySQL。只有在多实例抢占、吞吐或跨服务订阅成为实际问题时，才进入 RocketMQ 评审。

## 5. 引入 RocketMQ 的触发条件

满足以下任一条件时，再启动 RocketMQ 方案评审：

- Notification HTTP 调用成为 Workbench 明显耗时点。
- 需要多种通知通道并行投递，例如站内信、短信、APP Push、邮件。
- 需要跨服务异步订阅同一事件，例如运营看板、风控审计、消息中心。
- 通知事件量明显增长，单纯 MySQL 轮询造成压力。
- 后续部署多实例，需要更可靠的事件分发和消费位点管理。

## 6. RocketMQ 候选设计

如果后续用户确认引入 RocketMQ，建议先采用单 Topic、多 Tag：

- Topic：`SMARTCS_NOTIFICATION_EVENT`
- Tag：
  - `APPROVAL_APPROVED`
  - `APPROVAL_REJECTED`
  - `APPROVAL_MATERIALS_REQUESTED`
  - `APPROVAL_TRANSFERRED_TO_TAKEOVER`
  - `TAKEOVER_STARTED`
  - `TAKEOVER_MESSAGE_SENT`
  - `TAKEOVER_FINISHED`

消息 Key：

- 优先使用 `eventId`。
- 便于 RocketMQ 控制台按事件 ID 查询。

消息体建议沿用 `POST /api/notifications/events` 的请求结构：

```json
{
  "eventId": "ntf_xxx",
  "traceId": "trace_xxx",
  "sourceService": "smartcs-workbench",
  "eventType": "TAKEOVER_STARTED",
  "recipientUserId": "u1001",
  "sessionId": "s_xxx",
  "ticketId": "wo_xxx",
  "operatorId": "agent001",
  "channel": "USER_SESSION",
  "title": "人工客服接入通知",
  "content": "人工客服已接入，请继续描述你的问题。",
  "payload": {
    "takeoverStatus": "IN_PROGRESS",
    "workOrderStatus": "PROCESSING"
  },
  "occurredAt": "2026-07-05T01:00:00Z"
}
```

## 7. RocketMQ 消费失败策略

如果引入 RocketMQ，消费端仍应复用 `notification_event` 状态：

- 消费前先保证事件已落库，避免消息只存在 MQ 中。
- 消费成功：回写 `DELIVERED`。
- 消费失败：回写 `FAILED`，记录 `lastError` 和 `nextRetryAt`。
- RocketMQ 自身重试和业务重试要避免重复叠加。

建议策略：

- MQ 消费异常可以先让 RocketMQ 做短周期重试。
- 达到 MQ 最大重试后，写入 `FAILED`，交给业务重试或人工排查。
- 消费逻辑必须按 `eventId` 做幂等，重复消息不能重复投递用户。

## 8. 本地启动要求

只有确认接入 RocketMQ 后，才需要用户启动中间件。

候选本地环境：

- RocketMQ NameServer：`localhost:9876`
- RocketMQ Broker：默认本地 Broker
- Console：可选

需要确认的配置项：

```text
SMARTCS_ROCKETMQ_NAMESRV_ADDR=localhost:9876
SMARTCS_NOTIFICATION_MQ_ENABLED=true
SMARTCS_NOTIFICATION_TOPIC=SMARTCS_NOTIFICATION_EVENT
```

当前阶段这些配置项不需要添加到代码中。

## 9. 当前执行建议

短期继续推进：

- 默认保持 Workbench -> Notification HTTP 投递；联调环境可显式开启事务 outbox。
- 保持 `notification_event` 查询和状态回写。
- 保持自动重试 worker 默认关闭，先接入并验证至少一个真实幂等通道。
- 为 outbox 事件补齐用户消息上下文，再实现幂等 `USER_SESSION` 通道。

暂缓事项：

- 暂不接 RocketMQ。
- 暂不做短信、邮件、APP Push 真实投递。
- 暂不做跨实例广播。
