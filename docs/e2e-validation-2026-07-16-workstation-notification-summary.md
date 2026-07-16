# 坐席通知运维摘要验收记录

验收日期：2026-07-16

## 验收范围

- Workbench Outbox 运维摘要。
- Notification Delivery 运维摘要。
- Notification 服务对坐席工作台的跨域预检。
- 通知事件列表原有查询能力回归。

## 自动化结果

- `mvn -pl smartcs-notification -am test`：通过，Common 6 个测试、Notification 34 个测试。
- `npm run test --workspace @smartcs/workstation`：通过，7 个测试。
- `npm run typecheck --workspace @smartcs/workstation`：通过。
- `npm run lint --workspace @smartcs/workstation`：通过。
- `npm run build --workspace @smartcs/workstation`：通过。
- `npm run test:e2e:workstation`：通过，6 个 Playwright 场景。

## 真实服务验收

启动 Workbench `8083`、Notification `8085` 和 Workstation `3001` 后验证：

- `OPTIONS /api/notifications/events/delivery-summary` 从 `http://127.0.0.1:3001` 发起时返回 `200`，并包含正确的 `Access-Control-Allow-Origin`。
- Workbench Outbox 与 Notification Delivery 摘要均成功加载。
- 两个 worker 默认关闭时均显示“未启用”和零计数，不触发无意义的数据库租约读取。
- 双摘要状态区在 1440 x 900 视口中无文本遮挡、组件重叠或首屏溢出。
- 筛选区和通知事件表仍可见，原有只读查询链路未受影响。

## 中间件

本轮仅使用现有 MySQL，不需要 Redis、RocketMQ、Elasticsearch 或 pgvector。
