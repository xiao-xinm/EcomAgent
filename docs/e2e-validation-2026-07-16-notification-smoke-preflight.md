# 通知烟测配置预检验收记录

验收日期：2026-07-16

## 验收范围

- 通知用户会话烟测新增只读 `Preflight` 模式。
- `Full`、`PrepareRecovery` 和 `VerifyRecovery` 在写测试数据前执行对应配置检查。
- `Cleanup` 保持可独立执行，不依赖服务在线或配置正确。

## 脚本测试

- 三个 PowerShell 文件均通过 AST 语法解析。
- `scripts/test-notification-smoke-preflight.ps1` 通过 4 个场景：完整异步配置、outbox 关闭、仍为直写模式、`USER_SESSION` 通道关闭。

## 真实进程验收

使用现有 MySQL 临时启动：

- Workbench：outbox 开启、Notification 发布开启、用户消息直写关闭。
- Notification：retry worker 与 `USER_SESSION` 通道开启。

执行结果：

- `-Mode Preflight` 成功确认四个运行时条件。
- `-Mode Full` 成功创建临时人工接管消息事件。
- outbox 事件最终进入 `SENT`，Notification 事件最终进入 `DELIVERED`。
- 用户会话只写入一条 `HUMAN_AGENT` 消息，来源为 `notification`。
- 重放同一事件后消息数量仍为 1。
- 测试工单、事件、消息和状态文件已清理。
- 临时 8083、8085 进程与日志目录已关闭并删除。

## 中间件

本轮仅使用现有 MySQL，不需要 Redis 或 RocketMQ。
