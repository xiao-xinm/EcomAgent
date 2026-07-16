# Notification Delivery Summary Validation

验证日期：2026-07-16

## 验证目标

验证 Notification 投递运行摘要的聚合口径，并确保不改变现有事件接收、分页查询和重试执行流。

## 自动化测试

- `mvn -pl smartcs-notification -am test` 通过。
- Common 6 项测试通过。
- Notification 32 项测试通过。
- 新增覆盖 SQL 映射、关闭模式不访问数据库、启用模式仓储委托和控制器响应封装。

## 真实 MySQL 接口验证

临时启动 Notification `18085`，开启 retry worker、设置 1 小时调度间隔，并写入 5 条专用测试事件：

- 2 条 `ACCEPTED`，其中 1 条到期、1 条处于有效租约中。
- 1 条可重试 `FAILED`。
- 1 条已耗尽 `FAILED`。
- 1 条 `DELIVERED`。

`GET /api/notifications/events/delivery-summary` 返回：

- `enabled=true`
- `accepted=2`
- `retryableFailed=1`
- `exhaustedFailed=1`
- `delivered=1`
- `due=1`
- `leased=1`
- `oldestDueAt` 为到期 `ACCEPTED` 事件的接收时间

## 清理结果

- 专用测试事件已删除，残留数量为 0。
- 临时 Notification 已停止，`18085` 未继续监听。

## 结论

异步通知下游已具备基础积压观测入口，当前仍只依赖 MySQL，不需要 Redis、RocketMQ 或额外监控中间件。
