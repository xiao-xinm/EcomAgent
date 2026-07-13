# smartcs-workbench

坐席工作台后端模块，负责人工审核、人工接管和工单流转。

当前已提供：

- L3 人工审核工单领取、审批、驳回、补材料和转人工接管。
- 人工接管开始、人工消息和结束处理。
- 工单操作日志、内部备注和审计轨迹。
- 用户可见会话消息回写。
- Notification 事件发布，以及默认关闭的 MySQL 事务 outbox 和有限重试 worker。
- 通知事件携带 `messageRole` 和 `userMessageDeliveryMode`，可灰度迁移用户消息写入职责。
- 受坐席身份保护的 outbox 运行摘要，展示待投递、失败、耗尽、租约和最老积压时间。

启用事务 outbox 前依次执行 `infra/sql/12-workbench-notification-outbox.sql` 和
`infra/sql/14-workbench-outbox-delivery-lease.sql`，然后设置：

```text
SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true
SMARTCS_NOTIFICATION_OUTBOX_LEASE_DURATION_MS=120000
```

worker 通过 MySQL 租约支持多实例安全领取。每个进程可设置不同的
`SMARTCS_NOTIFICATION_OUTBOX_WORKER_ID`；留空时自动生成。租约应长于一次 Notification HTTP 调用的最大耗时。

默认保持 `SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true`。只有完成 Notification
`USER_SESSION` 通道联调后，才可将它设为 `false`；此时必须同时设置
`SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true`，否则 Workbench 会拒绝启动。

边界：

- 工单审批结果要回写 Agent 上下文
- 审核、拒绝、转派、接管都必须保留审计轨迹
- 不在本模块内直接实现订单、退款、换货等电商域规则
