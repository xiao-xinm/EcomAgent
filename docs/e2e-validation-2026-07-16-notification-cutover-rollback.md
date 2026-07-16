# Notification 灰度切换与回滚验收记录（2026-07-16）

## 验收范围

- Workbench 同步直写与 outbox 并行的灰度状态。
- 用户消息切换为 Notification 异步交付。
- `USER_SESSION` 投递、唯一消息和重复事件幂等。
- 恢复 Workbench 事务直写的回滚顺序。

## 灰度状态

Notification 开启 retry worker 和 `USER_SESSION` 通道；Workbench 开启 Notification 发布与 outbox，同时保持用户消息直写。

```powershell
.\scripts\check-runtime-readiness.ps1 `
  -ExpectedKnowledgeState HYBRID_READY `
  -ExpectedNotificationState CUTOVER_READY
```

结果：通过。异步链路完整且无积压，用户消息仍由 Workbench 事务内写入。

## 异步切换

将 `SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=false` 后重启 Workbench。

```powershell
.\scripts\check-runtime-readiness.ps1 `
  -ExpectedKnowledgeState HYBRID_READY `
  -ExpectedNotificationState ASYNC_ACTIVE
.\scripts\smoke-notification-user-session.ps1 -Mode Preflight
.\scripts\smoke-notification-user-session.ps1
```

结果：

- 运行时状态为 `ASYNC_ACTIVE`。
- Preflight 确认 outbox、`NOTIFICATION` 模式、retry worker、`USER_SESSION` 均已开启。
- 测试事件最终进入 `DELIVERED`。
- 用户会话只生成一条 `HUMAN_AGENT` 消息。
- 重复重放同一事件后消息数仍为一条。
- 测试工单和关联数据已自动清理。

## 回滚验证

先恢复 `SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true` 并重启 Workbench，保留异步链路开启用于观察。

结果：运行时状态重新变为 `CUTOVER_READY`，没有积压或重试耗尽，证明消息直写所有权已先恢复。

## 清理结果

- 临时 Workbench、Knowledge、Notification 进程已停止。
- `8083`、`8084`、`8085` 端口已释放。
- 临时日志已删除。
- 没有遗留烟测工单或后台 Spring Boot 进程。
