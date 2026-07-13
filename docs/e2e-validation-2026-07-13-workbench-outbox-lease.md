# Workbench Outbox Worker Lease Validation

验证日期：2026-07-13

## 验证目标

验证多个 Workbench 进程共享 MySQL 时，不会同时领取同一通知 outbox 事件，并且进程异常后可通过租约过期恢复处理。

## 数据库验证

- `infra/sql/14-workbench-outbox-delivery-lease.sql` 连续执行两次均成功。
- `workbench_notification_outbox` 已新增 `delivery_owner` 和 `delivery_lease_until`。
- 已新增 `idx_workbench_notification_outbox_claim(status, next_attempt_at, delivery_lease_until, created_at)`。
- 两个并发事务竞争同一事件时，第二个事务通过 `FOR UPDATE SKIP LOCKED` 跳过被锁记录。
- 测试事件设置 2 秒租约后，在租约有效期内不可领取，过期后可重新领取。

## 双进程验证

同时启动两个 Workbench 实例和一个 Notification 实例：

- Workbench A：`8083`，worker ID 为 `workbench-lease-a`。
- Workbench B：`18083`，worker ID 为 `workbench-lease-b`。
- Notification：`8085`。
- 三个实例共用本地 `smartcs_agent` MySQL。
- outbox 调度间隔 500 毫秒，租约 10 秒。

插入一条完整通知 outbox 测试事件后：

- outbox 事件最终进入 `SENT`，`attempt_count` 保持 0。
- `delivery_owner` 和 `delivery_lease_until` 在成功后清空。
- Notification 中只生成一条同 `eventId` 事件。
- 两个 Workbench 实例中只有实例 A 记录 `Notification outbox delivered`。
- 测试事件和所有临时进程均已清理。

## 结论

Workbench outbox worker 已具备基础多实例安全领取和过期恢复能力。当前通知可靠性链路继续只依赖 MySQL，不需要引入 RocketMQ。
