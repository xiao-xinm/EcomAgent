# smartcs-notification

通知分发模块，负责用户通知、坐席提醒和流程事件通知。

当前已具备：

- Workbench 审批、人工接管和人工消息事件接收与 MySQL 落库。
- 通知事件分页查询和投递结果回写。
- 失败原因、重试次数、下次重试时间和投递完成时间记录。
- 默认关闭的单实例定时投递 worker。
- 通过 `NotificationDeliveryChannel` 扩展真实投递渠道。

当前没有内置短信、邮件、APP Push 或站内信通道，`SMARTCS_NOTIFICATION_RETRY_ENABLED` 必须保持 `false`。接入真实通道并完成 `eventId` 幂等验证后再启用；如果误开但没有注册通道，应用会直接启动失败，不会消费待处理事件。

详细契约见 `docs/notification-api.md` 和 `docs/notification-mq-evaluation.md`。

边界：

- 不做业务决策
- 不修改订单、退款、换货状态
- 只消费事件并完成通知分发
- 不替代 Workbench 当前事务内的用户可见 `cs_message` 写入
