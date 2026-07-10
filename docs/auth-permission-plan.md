# SmartCS 登录鉴权与权限方案

本文档对应 `docs/project-roadmap.md` 的 Phase 8，用于约束后续登录鉴权、身份传递和权限校验的实现顺序。当前阶段只定义方案和迁移路径，不直接修改后端接口、不新增数据库表、不引入 Redis。

## 0. 当前落地进展

截至 Phase 8 第一轮代码：

- `smartcs-common` 已新增身份上下文模型：`AuthenticatedPrincipal`、`PrincipalType`、`AuthSource`、`AuthHeaders`、`AuthRoles`。
- `smartcs-gateway` 已支持从标准身份头、开发用户头或旧请求体 `userId` 解析用户身份，并向 Agent Core 透传标准身份头。
- `smartcs-workbench` 已支持从标准身份头、开发坐席头或旧请求体 `operatorId` 解析坐席身份，并用解析后的坐席 ID 写入现有操作请求。
- `smartcs-workbench` 已新增 `GET /api/workbench/me`，供坐席前端后续移除固定 `DEFAULT_OPERATOR_ID`。
- `frontend/client-h5` / `frontend/app-h5` 已通过运行时配置发送开发用户身份头，并预留可选 Bearer Token。
- `frontend/workstation` 已通过运行时配置发送开发坐席身份头，操作前调用 `GET /api/workbench/me` 获取当前坐席，不再由页面硬编码操作人。
- `frontend/client-h5` 和 `frontend/workstation` 已对 `401`、`403`、`1002`、`1003` 做统一错误文案归一化。
- `frontend/workstation` 已基于当前坐席的 `principalType`、`roles` 和 `operatorId` 控制领取、审批、接管、人工消息、内部备注按钮。
- `smartcs-workbench` 已在服务端对坐席操作入口做角色兜底校验，非坐席角色返回 `1003 FORBIDDEN`，且标准用户身份不能回退到请求体 `operatorId`。
- `smartcs-common` 已新增无状态 Bearer JWT 解析器，支持签名校验、issuer/audience 校验、角色解析和权限解析。
- `smartcs-gateway` 已支持可配置 Bearer JWT 用户身份解析，默认关闭，启用后 Token 优先于标准身份头、开发头和旧请求体 `userId`。
- `smartcs-workbench` 已支持可配置 Bearer JWT 坐席身份解析，默认关闭，启用后 Token 优先于标准身份头、开发头和旧请求体 `operatorId`，且用户 Token 不能回退成坐席身份。
- `smartcs-gateway` / `smartcs-workbench` 已新增 `smartcs.auth.strict-enabled`，默认关闭；开启后只接受 Bearer JWT 或标准身份头。
- 强制鉴权开启后，Gateway 不再接受旧请求体 `userId` 兜底，Workbench 不再接受旧请求体 `operatorId`、开发头和本地 `agent_001` 兜底。
- 强制鉴权开启后，请求体身份与可信身份冲突会返回 `1003 FORBIDDEN`。
- Workbench 工单列表、统计、详情和操作日志读取接口已统一经过坐席身份校验。
- 已新增 JWT 校验接入说明：`docs/auth-jwt-validation.md`。
- `frontend/client-h5` / `frontend/app-h5` 已新增 `window.__SMARTCS_AUTH__` Token Provider，支持 Token 获取、刷新一次重试和登录跳转兜底。
- `frontend/workstation` 已新增 `window.__SMARTCS_WORKSTATION_AUTH__` Token Provider，支持坐席端 Token 获取、刷新一次重试和登录跳转兜底。
- `smartcs-gateway` 已对用户聊天会话增加归属校验：发送消息、快捷动作、会话状态查询、消息轮询和 SSE 订阅在解析到可信用户身份后，都会拒绝访问其他用户的会话。
- 当前默认仍是兼容模式：不新增 Redis Session，真实账号中心和真实登录页面接入留到后续收紧阶段。

## 1. 当前问题

当前最小闭环为了方便本地联调，仍使用开发态身份：

- 用户端 H5 通过 `VITE_USER_ID`、URL 参数或默认值 `u1001` 传递 `userId`。
- 坐席工作台通过前端常量 `agent_001` 传递 `operatorId`。
- Gateway 信任聊天请求体中的 `userId`。
- Workbench 信任操作请求体中的 `operatorId`。
- 前端未统一处理 `401 Unauthorized`、`403 Forbidden` 和登录过期状态。

这些设计可以支撑 MVP 联调，但不适合真实环境。Phase 8 的目标是把“请求体传身份”逐步替换为“服务端解析可信身份”，并保留一段开发兼容期，避免一次性破坏现有链路。

## 2. 方案结论

第一轮采用无状态 Token 方案，优先兼容 JWT 或上游电商账号体系签发的访问令牌。

- 推荐请求头：`Authorization: Bearer <access_token>`。
- 不使用 Redis Session，因此本阶段不需要新增中间件。
- 如果后续选择服务端 Session，再单独评估 Redis，本地启动前需要用户确认。
- 后端先增加统一身份上下文，再逐步收紧接口，不直接一次性删除请求体里的 `userId` / `operatorId`。

## 3. 身份模型

### 3.1 用户端身份

用户端身份用于 Gateway、Agent Core 和会话消息链路。

| 字段 | 说明 |
|------|------|
| `principalId` | 可信用户 ID，对应当前 `userId` |
| `principalType` | 固定为 `CUSTOMER` |
| `channel` | `h5`、`app-h5` 等触点来源 |
| `roles` | 至少包含 `CUSTOMER` |

后续用户端请求体中的 `userId` 只作为兼容字段。鉴权启用后，应以 Token 解析出的 `principalId` 为准。

### 3.2 坐席端身份

坐席端身份用于 Workbench 工单列表、审批、接管、备注和消息发送。

| 字段 | 说明 |
|------|------|
| `principalId` | 可信坐席 ID，对应当前 `operatorId` |
| `principalType` | `AGENT`、`SUPERVISOR` 或 `ADMIN` |
| `roles` | 坐席角色集合 |
| `permissions` | 可选权限集合，后续用于细粒度按钮控制 |

后续操作请求体中的 `operatorId` 只作为兼容字段。鉴权启用后，应由服务端身份上下文写入操作日志、审批记录和通知事件。

## 4. 统一请求头

### 4.1 生产 / 测试环境

```http
Authorization: Bearer <access_token>
X-Request-Id: <traceId>
```

`X-Request-Id` 保持现有链路追踪语义，可选。

### 4.2 本地开发兼容头

仅允许在 `dev` profile 或显式开发配置中启用：

```http
X-SmartCS-User-Id: u1001
X-SmartCS-Operator-Id: agent_001
X-SmartCS-Roles: CUSTOMER,AGENT
```

开发兼容头用于平滑替换前端固定身份，不能作为生产鉴权方案。

## 5. 权限矩阵

### 5.1 用户端 Gateway

| 操作 | 最低角色 | 校验要求 |
|------|----------|----------|
| 发送聊天消息 | `CUSTOMER` | Token 用户必须与会话归属一致 |
| 确认 / 取消快捷动作 | `CUSTOMER` | Token 用户必须与会话归属一致 |
| 查询会话状态 | `CUSTOMER` | Token 用户必须与会话归属一致 |
| 查询会话消息 | `CUSTOMER` | Token 用户必须与会话归属一致 |
| 订阅 SSE 事件 | `CUSTOMER` | Token 用户必须与会话归属一致 |

### 5.2 坐席 Workbench

| 操作 | 最低角色 | 校验要求 |
|------|----------|----------|
| 查看工单列表 | `AGENT` | 只能查看允许范围内工单 |
| 查看工单详情 | `AGENT` | 只能查看允许范围内工单 |
| 领取工单 | `AGENT` | 工单必须未被其他坐席锁定 |
| 添加内部备注 | `AGENT` | 需要工单可见权限 |
| 审批通过 / 驳回 | `AGENT` | 需要审批权限，后续可升级为 `SUPERVISOR` |
| 要求补充材料 | `AGENT` | 需要审批权限 |
| 转人工接管 | `AGENT` | 需要审批或接管权限 |
| 开始人工接管 | `AGENT` | 需要接管权限，且工单处于可接管状态 |
| 发送人工消息 | `AGENT` | 需要接管权限，且自己正在接管或具备主管权限 |
| 结束人工接管 | `AGENT` | 需要接管权限，且自己正在接管或具备主管权限 |
| 查看统计 | `SUPERVISOR` | 第一轮可暂时允许 `AGENT`，后续收紧 |

## 6. 后端迁移步骤

### 6.1 公共身份上下文

在 `smartcs-common` 中增加轻量身份对象和解析结果，建议包含：

- `principalId`
- `principalType`
- `roles`
- `permissions`
- `authSource`

第一轮只做后端内部对象，不要求马上暴露到 API 响应。

### 6.2 Gateway

第一轮改造目标：

1. 从 `Authorization` 或开发兼容头解析用户身份。
2. 构造标准身份上下文。
3. 对请求体 `userId` 做兼容校验：
   - 未开启强校验时，用可信身份覆盖请求体 `userId`。
   - 开启强校验时，若请求体 `userId` 与可信身份不同，返回无权限。
4. 向 Agent Core 透传标准身份头，减少下游重复解析。
5. 会话状态、消息轮询和 SSE 查询增加会话归属校验。
6. 发送消息和快捷动作在转发 Agent Core 前，对已有会话做归属预检，避免用户借用其他人的 `sessionId` 写入消息或触发操作。

### 6.3 Workbench

第一轮改造目标：

1. 从 `Authorization` 或开发兼容头解析坐席身份。
2. 坐席操作统一使用可信 `principalId` 作为 `operatorId`。
3. 请求体 `operatorId` 保留兼容，但不再作为最终可信来源。
4. 在 Controller 或统一拦截器层做角色校验。
5. 在 Service 层保留业务状态校验，如工单是否可领取、是否可接管。
6. 操作日志、审批记录、通知事件和用户消息中的操作人统一来自身份上下文。

## 7. 前端迁移步骤

### 7.1 用户端 H5 / APP H5

1. 增加 `accessToken` 运行时配置或 Token Provider。
2. API client 自动追加 `Authorization`。
3. 移除默认固定 `u1001` 对业务可信身份的依赖。
4. 会话本地缓存 key 从 `userId` 维度逐步改为可信用户维度。
5. 增加登录过期、无权限、会话不属于当前用户的提示。

开发期可以保留 `VITE_USER_ID`，但只能作为本地兼容头或 mock token 的输入。

注意：浏览器 `EventSource` 不能附加自定义 Header。当前 SSE 仍作为兼容期可选增量唤醒能力，强制鉴权前需要单独确认 Cookie 或 query token 策略。

### 7.2 坐席工作台

1. 增加当前坐席信息接口，例如 `GET /api/workbench/me`。
2. 前端从当前坐席信息中获取 `operatorId`、角色和权限。
3. API client 自动追加 `Authorization`。
4. 移除 `DEFAULT_OPERATOR_ID` 对真实操作的依赖。
5. 按权限控制按钮显隐和禁用态。
6. 对 `401`、`403` 和登录过期做统一提示。

当前已完成开发身份头、可选 Bearer Token、`GET /api/workbench/me` 接入、鉴权失败 / 权限不足错误文案归一化、坐席端按钮级权限控制、Workbench 后端角色兜底校验，以及前端 Token Provider / 登录跳转骨架；真实账号中心和真实登录页面留到后续收紧期。

## 8. API 兼容策略

为了不破坏现有联调，Phase 8 按三步兼容：

1. **兼容期 A：文档和身份上下文**
   - 请求体仍允许 `userId` / `operatorId`。
   - 后端开始解析 Token 或开发兼容头。
   - 未提供身份时，开发环境继续允许现有默认链路。
   - 当前代码已完成身份上下文、标准身份头透传、开发兼容头和 Workbench 当前坐席接口。

2. **兼容期 B：可信身份优先**
   - 后端以可信身份覆盖请求体身份。
   - 请求体身份不一致时记录安全日志。
   - 前端开始从 Token Provider / 当前用户接口获取身份。
   - Gateway 对已有聊天会话执行用户归属校验，跨用户访问返回 `1003 FORBIDDEN`。

3. **收紧期 C：强制鉴权**
   - 生产环境通过 `smartcs.auth.strict-enabled=true` 开启强制鉴权。
   - 强制鉴权开启后必须携带 Bearer Token 或可信上游标准身份头。
   - 请求体身份不一致直接返回无权限。
   - 文档把 `userId` / `operatorId` 标记为兼容字段或移除。

## 9. 错误语义

后续需要在公共错误码中补齐：

| 场景 | HTTP 语义 | 建议业务语义 |
|------|-----------|--------------|
| 未登录 / Token 缺失 | `401` | `UNAUTHORIZED` |
| Token 过期 | `401` | `TOKEN_EXPIRED` |
| 权限不足 | `403` | `FORBIDDEN` |
| 会话或工单不属于当前身份 | `403` | `RESOURCE_FORBIDDEN` |
| 身份字段冲突 | `403` | `IDENTITY_MISMATCH` |

如果继续沿用统一 `ApiResponse`，也应确保网关和前端能识别这些错误语义。

## 10. 验收清单

后续代码实现完成后，至少验收：

- 用户端携带有效 Token 发送“我的订单”，仍返回 `AUTO_REPLY` 和 `skillExecutionId`。
- 用户端 Token 与请求体 `userId` 不一致时，生产模式返回无权限。
- 用户端强制鉴权开启且缺少可信身份时返回未登录。
- 用户端会话消息轮询和 SSE 只能读取当前用户会话。
- 坐席端携带有效 Token 能查看工单列表和详情。
- 坐席端领取、审批、接管、发送人工消息时，操作日志中的操作人来自 Token。
- 坐席端强制鉴权开启且缺少可信身份时，列表、详情、统计、操作接口都返回未登录。
- 无坐席权限的身份调用 Workbench 操作接口时返回无权限。
- 前端能识别未登录、权限不足和登录过期，不出现静默失败。

## 11. 暂不做事项

- 不实现真实登录页面。
- 不接入真实电商账号中心。
- 不新增 Redis Session。
- 不新增用户、坐席、角色、权限数据库表。
- 不改变现有聊天、审批、接管业务字段。
- 不移除现有 `userId` / `operatorId` 兼容字段。

这些事项等身份上下文和接口兼容完成后，再作为独立小 PR 评估。
