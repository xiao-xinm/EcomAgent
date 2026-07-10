# SmartCS 日志字段规范

本文档对应 `docs/project-roadmap.md` 的 Phase 9，用于统一后端服务日志中的核心排障字段。

## 1. 目标

- 同一条用户请求能通过 `traceId` 串起 Gateway、Agent Core、Skill Engine、Workbench、Knowledge 和 Notification。
- 同一客服会话能通过 `sessionId` 定位用户端、Agent 和坐席端日志。
- 人工审核、人工接管和通知链路能通过 `ticketId` 关联工单动作。
- 用户和坐席身份分别使用 `userId`、`operatorId`，避免日志里混用 `principalId` 后难以搜索。

## 2. 标准字段

| 字段 | 说明 |
|------|------|
| `traceId` | 单次请求或链路追踪 ID |
| `sessionId` | 客服会话 ID |
| `userId` | 用户身份 ID |
| `ticketId` | 人工工单 ID |
| `operatorId` | 坐席身份 ID |
| `streamId` | SSE / 后续实时连接的流 ID |

公共常量位于：

```text
backend/smartcs-common/src/main/java/com/smartcs/agent/common/observability/LogFields.java
```

## 3. 日志格式

推荐使用稳定 key-value 片段：

```text
traceId=xxx sessionId=s_xxx userId=u1001
traceId=xxx ticketId=wo_xxx userId=u1001 operatorId=agent_001
streamId=xxx sessionId=s_xxx userId=u1001
```

缺失值统一输出 `-`，不要在不同服务中混用 `NONE`、空字符串或 `null`。

## 4. 当前落地

- `smartcs-common` 已新增 `LogFields` 公共字段工具。
- `smartcs-gateway` 聊天入口已使用统一字段片段覆盖：发送消息、快捷动作、会话状态查询、消息轮询、SSE 打开/关闭、跨用户会话拒绝和 Agent Core 转发结果。

## 5. 后续迁移顺序

1. Agent Core：意图识别、风险路由、技能调用、工单创建日志统一 `traceId/sessionId/userId/ticketId`。
2. Skill Engine：技能执行日志统一 `traceId/sessionId/userId`，并补充 `skillId/executionId`。
3. Workbench：工单操作日志统一 `traceId/ticketId/userId/operatorId`。
4. Notification：事件接收、投递结果和失败重试统一 `traceId/ticketId/userId`。
5. 如后续接入 OpenTelemetry，再复用这些字段作为 span attributes。

## 6. 中间件说明

当前阶段不需要新增 Prometheus、Grafana、SkyWalking 或 OpenTelemetry。先保证本地日志字段稳定，后续再评估监控和链路追踪组件。
