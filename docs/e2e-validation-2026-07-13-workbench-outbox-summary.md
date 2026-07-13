# Workbench Outbox Summary Validation

验证日期：2026-07-13

## 验证目标

验证通知 outbox 运行摘要的聚合口径和坐席读取权限，不改变工单控制器与既有身份解析执行流。

## 自动化测试

- `mvn -pl smartcs-workbench -am test` 通过。
- Common 6 项测试通过。
- Workbench 44 项测试通过。
- 新增覆盖 SQL 映射、关闭模式不访问数据库、坐席放行、用户身份拒绝和控制器响应封装。

## 真实 MySQL 接口验证

临时启动 Workbench `18083`，开启 outbox 并写入 5 条专用测试事件：

- 2 条 `PENDING`，其中 1 条到期、1 条处于有效租约中。
- 1 条可重试 `FAILED`。
- 1 条已耗尽 `FAILED`。
- 1 条 `SENT`。

`GET /api/workbench/notifications/outbox/summary` 返回：

- `enabled=true`
- `pending=2`
- `retryableFailed=1`
- `exhaustedFailed=1`
- `sent=1`
- `due=1`
- `leased=1`
- `oldestDueAt` 为到期 `PENDING` 事件的创建时间

标准 `CUSTOMER` 身份请求返回业务错误码 `1003`。Workbench 统一异常响应仍使用 HTTP 200，本次验证按响应业务码判断权限结果。

## 清理结果

- 专用测试事件已删除，残留数量为 0。
- 临时 Workbench 已停止，`18083` 未继续监听。

## 结论

异步通知模式已经具备基础积压观测入口，当前仍只依赖 MySQL，不需要 Redis、RocketMQ 或额外监控中间件。
