# Notification USER_SESSION E2E Validation

验证日期：2026-07-13

## 验证目标

验证 Workbench 关闭用户消息直写后，用户可见消息可通过 MySQL outbox 和 Notification `USER_SESSION` 通道可靠交付，并在服务中断和重复消费时保持幂等。

## 环境

- MySQL 8.0.32：`smartcs_agent`
- Workbench：`8083`
- Notification：`8085`
- Workbench 配置：outbox 开启、用户消息直写关闭、outbox 调度间隔 1 秒
- Notification 配置：重试 worker 开启、`USER_SESSION` 通道开启、调度间隔 1 秒
- 未启用 Redis、RocketMQ 或 WebSocket

## 执行结果

### 正常投递

执行：

```powershell
.\scripts\smoke-notification-user-session.ps1 -Mode Full -TimeoutSeconds 30
```

结果：

- Workbench 操作成功后生成 `userMessageDeliveryMode=NOTIFICATION` 的 outbox 事件。
- Workbench 未直接写入测试用户消息。
- outbox 最终进入 `SENT`，Notification 事件进入 `DELIVERED`。
- 用户会话中只存在一条 `HUMAN_AGENT` 消息。
- 将同一通知事件重置为 `ACCEPTED` 后再次消费，消息数量仍为一条。

### 中断恢复

1. 仅启动 Workbench，停止 Notification。
2. 执行 `-Mode PrepareRecovery`。
3. 确认 outbox 保持 `PENDING` 或 `FAILED`，用户消息数量为 0。
4. 启动 Notification。
5. 执行 `-Mode VerifyRecovery`。

结果：

- Notification 不可用时，业务事务和 outbox 事件均成功持久化。
- Notification 恢复后，Workbench worker 将事件补投成功。
- Notification worker 写入用户消息并将事件标记为 `DELIVERED`。
- 重放同一事件没有产生重复消息。
- 两轮测试数据和状态文件均已自动清理。

## 结论

Phase 6 的用户会话消息解耦迁移模式已具备进程级可恢复性和幂等性。默认配置继续保留 Workbench 直写，后续只有在部署与观测方案明确后才评估切换默认值。
