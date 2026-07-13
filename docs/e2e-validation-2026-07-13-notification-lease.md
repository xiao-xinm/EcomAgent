# Notification Worker Lease Validation

验证日期：2026-07-13

## 验证目标

验证多个 Notification 进程共享 MySQL 时，不会同时领取同一待投递事件，并且进程异常后可通过租约过期恢复处理。

## 数据库验证

- `infra/sql/13-notification-delivery-lease.sql` 连续执行两次均成功。
- `notification_event` 已新增 `delivery_owner` 和 `delivery_lease_until`。
- 已新增 `idx_notification_event_claim(status, next_retry_at, delivery_lease_until, accepted_at)`。
- 两个并发事务竞争同一事件时，第二个事务通过 `FOR UPDATE SKIP LOCKED` 跳过被锁记录。
- 测试事件设置 2 秒租约后，在租约有效期内不可领取，过期后可重新领取。

## 双进程验证

同时启动两个 Notification 实例：

- 实例 A：`8085`，worker ID 为 `lease-worker-a`。
- 实例 B：`18085`，worker ID 为 `lease-worker-b`。
- 两个实例共用本地 `smartcs_agent` MySQL。
- worker 和 `USER_SESSION` 通道均开启，调度间隔 500 毫秒，租约 10 秒。

插入一条 `userMessageDeliveryMode=NOTIFICATION` 测试事件后：

- 事件最终进入 `DELIVERED`。
- 用户会话中只生成一条 `SYSTEM` 消息。
- 两个实例日志中只有实例 B 记录该事件的 `Notification delivered`。
- 测试事件、消息和会话均已删除。

## 结论

Notification 重试 worker 已具备基础多实例安全领取和过期恢复能力。Workbench outbox worker 仍是单实例，下一增量使用相同原则完成租约改造。
