# Workstation Ticket SSE Validation - 2026-07-16

## Scope

本次验证覆盖 Phase 7 坐席端工单实时提醒：

- Workbench SSE 开关默认关闭，显式开启后注册事件接口和 MySQL 增量扫描。
- `work_order` 变化可提示新工单、状态和分配变化。
- `audit_log` 变化可提示内部备注、人工消息等不修改工单状态的坐席动作。
- 坐席列表刷新工单表格和统计，详情只刷新当前工单。
- 不引入 Redis、RocketMQ 或 WebSocket。

## Automated Verification

后端：

```text
mvn -pl smartcs-workbench -am test
```

结果：`smartcs-common` 6 个测试、`smartcs-workbench` 50 个测试全部通过。

前端：

```text
npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run test --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
npm run test:e2e:workstation
```

结果：

- Lint、TypeScript 和生产构建通过。
- Workstation 13 个单元测试通过。
- Workstation 9 个 Playwright E2E 通过，其中包含列表 SSE 刷新和详情 `ticketId` 过滤。
- Vite 仍提示既有大 chunk 警告，不影响本次构建结果。

## Real MySQL Validation

执行并确认索引：

```text
work_order.idx_updated_ticket = updated_at,ticket_id
audit_log.idx_occurred_event = occurred_at,event_id
```

临时在 `18083` 端口启用 Workbench SSE 后：

1. `GET /api/workbench/tickets/events` 首先收到 `stream.ready`。
2. 在现有测试工单下写入一条专用 `WORK_ORDER_INTERNAL_NOTE` 审计事件。
3. SSE 收到同一事件 ID 的 `ticket.changed`，包含工单 ID、当前 `PENDING` 状态、空坐席和变化时间。
4. 删除专用审计事件，停止临时 Workbench，确认 `18083` 不再监听。

## Boundaries

- SSE 是增量提醒，不替代工单查询接口，也不提供消息队列级可靠投递。
- 前后端开关默认关闭，现有手动刷新和操作后刷新保持不变。
- 严格 Bearer 鉴权环境暂不开启坐席 SSE；需要账号中心提供 Cookie/BFF 或长连接票据契约。
- 本轮不实现 WebSocket、在线状态、输入中、已读回执或双向命令协议。
