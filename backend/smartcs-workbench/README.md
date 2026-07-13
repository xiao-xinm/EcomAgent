# smartcs-workbench

坐席工作台后端模块，负责人工审核、人工接管和工单流转。

当前已提供：

- L3 人工审核工单领取、审批、驳回、补材料和转人工接管。
- 人工接管开始、人工消息和结束处理。
- 工单操作日志、内部备注和审计轨迹。
- 用户可见会话消息回写。
- Notification 事件发布，以及默认关闭的 MySQL 事务 outbox 和有限重试 worker。

启用事务 outbox 前先执行 `infra/sql/12-workbench-notification-outbox.sql`，然后设置：

```text
SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true
```

当前 worker 按单实例运行；多实例部署前需要增加数据库抢占或改用 MQ。

边界：

- 工单审批结果要回写 Agent 上下文
- 审核、拒绝、转派、接管都必须保留审计轨迹
- 不在本模块内直接实现订单、退款、换货等电商域规则
