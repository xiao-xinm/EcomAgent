# Realtime Messaging Evaluation

本文档用于评估 SmartCS 用户端、APP H5 和坐席工作台是否需要从短轮询升级到实时消息。

当前结论：**Phase 7 暂不立即实现 WebSocket**。现有短轮询已经覆盖最小闭环；如需提升实时体验，优先选择 SSE 作为用户端单向推送试点，WebSocket 留给后续真正双向人工聊天或多坐席协作。

## 1. 当前消息同步方式

当前用户侧消息同步依赖 Gateway：

```http
GET /api/chat/sessions/{sessionId}/messages?limit=100
```

当前前端行为：

- `frontend/client-h5` 默认每 3 秒轮询一次。
- `frontend/app-h5` 复用 `client-h5` 聊天能力和轮询配置。
- 页面重新可见时会立即同步一次消息。
- 发送消息或执行快捷动作后会主动同步远端消息。

当前坐席端行为：

- 坐席操作成功后刷新工单详情。
- 工单详情中包含 `messages` 和 `actions`。
- 用户可见消息统一写入 `cs_message`，角色包括 `SYSTEM` 和 `HUMAN_AGENT`。

## 2. 当前短轮询是否足够

当前阶段短轮询仍然足够：

- 已覆盖查订单、改地址确认、退款人工审核、人工接管四条最小闭环。
- 坐席消息通过 `cs_message` 统一承载，用户端轮询可见。
- 不依赖 Redis、MQ、WebSocket 网关或长连接部署。
- 本地开发、Postman 联调和前端 E2E 都更稳定。

短轮询的问题：

- 用户最多等待一个轮询周期才能看到坐席消息。
- 多开页面会增加重复请求。
- 如果后续消息量增加，Gateway 查询压力会上升。
- 无法天然支持坐席输入中、在线状态、消息已读等实时交互。

## 3. 方案对比

| 方案 | 适合场景 | 优点 | 代价 | 当前建议 |
|------|----------|------|------|----------|
| 短轮询 | MVP、低频客服状态同步 | 简单、稳定、无中间件 | 有延迟、有重复请求 | 继续保留 |
| SSE | 用户端接收服务端消息 | HTTP 语义简单、浏览器原生支持、比 WebSocket 轻 | 单向推送，不适合复杂双向协作 | 下一步优先试点 |
| WebSocket | 双向人工聊天、坐席在线协作 | 实时性强、适合双向事件 | 连接管理、鉴权、心跳、扩容复杂 | 后续再做 |
| SockJS/STOMP | 需要兼容或消息协议封装 | 生态成熟 | 引入协议和前端复杂度 | 暂不需要 |

## 4. 推荐演进路径

### Step 1：保持短轮询

当前继续使用：

```text
H5 / APP H5 -> Gateway -> GET /api/chat/sessions/{sessionId}/messages
```

适用阶段：

- 最小闭环稳定化。
- 业务字段和状态仍在演进。
- 暂不做坐席在线状态和已读回执。

### Step 2：用户端 SSE 试点

候选接口：

```http
GET /api/chat/sessions/{sessionId}/events
Accept: text/event-stream
```

建议事件类型：

- `message.created`：有新的用户可见消息。
- `session.updated`：会话状态变化。
- `heartbeat`：保持连接活跃。

建议 payload：

```json
{
  "eventId": "evt_xxx",
  "traceId": "trace_xxx",
  "sessionId": "s_xxx",
  "messageId": "m_xxx",
  "role": "HUMAN_AGENT",
  "content": "人工客服已接入，请继续描述你的问题。",
  "createdAt": "2026-07-05T01:00:00Z"
}
```

SSE 试点原则：

- 不替代历史消息查询接口。
- H5 首屏仍先拉 `GET /messages`。
- SSE 只负责增量提示，断线后回退短轮询。
- 仍以 `cs_message` 为消息事实来源。

当前最小技术尖刺已实现：

- Gateway 新增 `GET /api/chat/sessions/{sessionId}/events`。
- 用户端 H5 可通过 `VITE_CHAT_SSE_ENABLED=true` 或 URL 参数 `?sse=true` 开启。
- H5 收到 `message.created` 后复用 `GET /messages` 完整同步。
- SSE 失败后自动回退短轮询。

### Step 3：WebSocket 双向聊天

进入 WebSocket 的条件：

- 人工接管中需要真实双向实时聊天。
- 需要坐席在线状态、输入中、已读回执、断线重连。
- 用户端和坐席端都需要实时事件。
- 已明确鉴权方案，能保护长连接身份。

候选连接：

```text
ws://localhost:8080/ws/chat?sessionId=s_xxx
ws://localhost:8083/ws/workbench?operatorId=agent001
```

WebSocket 事件建议后续单独出协议文档，不在当前阶段实现。

## 5. 是否需要中间件

当前不需要。

单机本地开发：

- 短轮询不需要新增中间件。
- SSE 不需要新增中间件。
- WebSocket 单实例也不一定需要新增中间件。

多实例或生产扩容时再评估：

- Redis Pub/Sub：用于跨实例广播会话事件。
- RocketMQ：用于可靠异步事件分发，不适合直接承担前端长连接广播。
- WebSocket 网关或 Nginx 配置：用于长连接转发和超时设置。

引入 Redis 或 MQ 前，必须先确认用途、事件格式、本地启动方式和失败策略。

## 6. 后端改造边界

若进入 SSE 试点，建议只新增 Gateway 端接口：

- 不改 `POST /api/chat/messages`。
- 不改 `GET /api/chat/sessions/{sessionId}/messages`。
- 不新增业务表。
- 不改变 `cs_message` 作为消息事实来源的定位。

Gateway 可先通过短间隔查询 `cs_message` 实现 SSE 推送原型，后续再优化为事件驱动。

## 7. 前端改造边界

用户端 H5：

- 保留现有轮询 Store。
- 新增可开关的 `EventSource` 通道。
- SSE 断开、浏览器不支持或接口失败时回退短轮询。
- 不改变地址确认表单和快捷动作逻辑。

APP H5：

- 复用 `client-h5` 的消息通道能力。
- 通过 runtime config 控制是否启用 SSE。

坐席工作台：

- 当前继续操作后刷新详情。
- WebSocket 阶段再做工单列表、详情、消息和操作日志实时刷新。

## 8. 验收建议

保持当前验收：

- 用户发送“我的订单”：自动回复仍可见。
- 用户发送“我要退款”：坐席审批后用户端能看到 `SYSTEM` 消息。
- 用户发送“人工客服”：坐席开始和结束接管后用户端能看到 `HUMAN_AGENT` 消息。

如果后续实现 SSE，新增验收：

- H5 打开 SSE 后不影响首屏历史消息加载。
- 坐席开始接管后，用户端无需等待下一个轮询周期即可看到消息。
- SSE 断开后，用户端自动回退短轮询。
- 刷新页面后仍能通过 `GET /messages` 恢复完整消息列表。

## 9. 当前执行建议

短期建议：

- 不引入 Redis、RocketMQ 或 WebSocket。
- 保持短轮询作为稳定基线。
- SSE 技术尖刺保持默认关闭，先用于本地和测试环境验证。

暂缓事项：

- 暂不实现 WebSocket 双向协议。
- 暂不实现坐席在线状态、输入中、已读回执。
- 暂不做跨实例广播。
