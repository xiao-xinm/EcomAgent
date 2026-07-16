# smartcs-notification

通知分发模块，负责用户通知、坐席提醒和流程事件通知。

当前已具备：

- Workbench 审批、人工接管和人工消息事件接收与 MySQL 落库。
- 通知事件分页查询和投递结果回写。
- 失败原因、重试次数、下次重试时间和投递完成时间记录。
- 默认关闭、支持 MySQL 租约多实例领取的定时投递 worker。
- 投递运行摘要，展示接收、失败、耗尽、完成、到期、租约和最老积压时间。
- 通过 `NotificationDeliveryChannel` 扩展真实投递渠道。
- 默认关闭的幂等 `USER_SESSION` 通道，可使用 `eventId` 派生固定消息 ID 写入 `cs_message`。

默认仍保持重试 worker 和 `USER_SESSION` 通道关闭。需要验证用户会话异步交付时，必须同时设置：

```text
SMARTCS_NOTIFICATION_RETRY_ENABLED=true
SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED=true
```

启用 worker 前必须先执行 `infra/sql/13-notification-delivery-lease.sql`。默认租约为 120 秒，可通过
`SMARTCS_NOTIFICATION_RETRY_LEASE_DURATION_MS` 调整；该值应长于单次投递通道调用的最大耗时。
多实例可选配置不同的 `SMARTCS_NOTIFICATION_RETRY_WORKER_ID`，未配置时每个进程自动生成唯一 ID。

启用 worker 后可通过 `GET /api/notifications/events/delivery-summary` 查看投递积压；worker 关闭时接口返回
`enabled=false` 和零计数，不读取租约字段。

只开启通道而未开启 worker 时应用会拒绝启动。历史事件及 `DIRECT` 事件只会被确认交付，不会重复写入用户消息。

详细契约见 `docs/notification-api.md` 和 `docs/notification-mq-evaluation.md`。

边界：

- 不做业务决策
- 不修改订单、退款、换货状态
- 只消费事件并完成通知分发
- 仅在事件明确声明 `userMessageDeliveryMode=NOTIFICATION` 时写用户会话消息
