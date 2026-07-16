# 通知异步切换就绪状态验收记录

验收日期：2026-07-16

## 验收范围

- Workbench Outbox 摘要附加 Notification 开关和用户消息交付模式。
- Notification Delivery 摘要附加 `USER_SESSION` 通道开关。
- 坐席工作台根据两端配置与积压计算切换状态。
- 默认同步交付模式保持不变。

## 自动化结果

- `mvn -pl smartcs-workbench,smartcs-notification -am test`：通过，Common 6、Workbench 44、Notification 34 个测试。
- `npm run test --workspace @smartcs/workstation`：通过，10 个测试。
- `npm run typecheck --workspace @smartcs/workstation`：通过。
- `npm run lint --workspace @smartcs/workstation`：通过。
- `npm run build --workspace @smartcs/workstation`：通过。
- `npm run test:e2e:workstation`：通过，6 个 Playwright 场景。

## 真实服务验收

使用现有 MySQL，以默认配置启动 Workbench `8083`、Notification `8085` 和 Workstation `3001`：

- Workbench 摘要返回 `enabled=false`、`notificationEnabled=true`、`userMessageDeliveryMode=DIRECT` 和零计数。
- Notification 摘要返回 `enabled=false`、`userSessionChannelEnabled=false` 和零计数。
- 坐席工作台显示“同步直写中”，并明确展示 Notification 已开启、消息直写、`USER_SESSION` 已关闭。
- 1440 x 900 视口中状态条、双摘要、筛选区和事件表无重叠或溢出。
- 临时启动的 3001、8083、8085 进程和截图日志已清理。

## 状态判定

- 异步配置完整且队列清空、仍为直写：`可切换异步`。
- 任一侧存在重试耗尽：`存在重试耗尽`，阻止切换判断。
- 异步配置完整但仍有待处理、重试或租约任务：显示灰度或异步积压。
- 直写已关闭且异步配置完整、无积压：`异步交付中`。
- 直写已关闭但异步链路未完整开启：`异步配置异常`。

## 中间件

本轮仅使用现有 MySQL，不需要 Redis、RocketMQ、Elasticsearch 或 pgvector。
