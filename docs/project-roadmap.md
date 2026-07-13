# SmartCS Project Roadmap

本文档是 SmartCS-Agent 后续开发的统一执行路线图。后续新增功能、接口、页面、测试和文档更新，都应先对齐本文档中的阶段、流程和验收要求。

## 1. 执行原则

- 先稳定最小闭环，再扩展业务能力。
- 所有任务按小步 PR 推进，每个 PR 只解决一个清晰目标。
- 涉及接口、状态枚举、数据库字段、事件协议时，必须先更新或确认文档契约，再实现代码。
- 不根据猜测补业务字段，不为了前端临时需要倒推后端接口。
- 新增 Redis、RocketMQ、WebSocket 等中间件前，必须先说明用途、替代方案和本地启动要求，并获得用户确认；Phase 5 已确认使用 PostgreSQL + pgvector 和 Elasticsearch。
- 每轮任务完成后，应同步更新相关 API 文档、验收清单或本文档状态。

## 2. 标准开发流程

每个开发任务都按以下流程执行：

1. 确认任务属于哪个阶段和模块。
2. 阅读 `README.md`、本文档、相关 API 文档、相关模块代码。
3. 明确本轮任务的输入、输出、接口契约、验收标准和不做事项。
4. 若需要新增接口或数据结构，先更新契约文档，再实现。
5. 小范围实现，只修改当前任务必需文件。
6. 运行对应验证命令。
7. 更新验收清单、联调记录或本文档。
8. 提交一个聚焦 PR，PR 说明包含变更、原因、验证和后续事项。

## 3. 当前基线

截至 2026-07-03，项目已完成核心最小闭环 MVP：

| 模块 | 当前状态 |
|------|----------|
| `smartcs-common` | 已具备公共契约、枚举、DTO 基础 |
| `smartcs-gateway` | 已具备用户聊天入口、会话查询、CORS 和日志基础 |
| `smartcs-agent-core` | 已具备意图识别、风险路由、确认链路、人工工单创建 |
| `smartcs-skill-engine` | 已具备订单查询、物流查询、修改地址、取消订单等最小技能 |
| `smartcs-workbench` | 已具备工单列表、详情、领取、审批、人工接管、消息回写 |
| `smartcs-knowledge` | 已具备 FAQ 关键词匹配最小链路 |
| `smartcs-notification` | 已具备通知事件最小接收和记录链路 |
| `frontend/client-h5` | 已具备聊天、短轮询、快捷动作、地址确认表单、状态展示和 Token Provider 骨架 |
| `frontend/app-h5` | 已具备复用用户端聊天能力的 APP 内嵌入口和 Token Provider 骨架 |
| `frontend/workstation` | 已具备坐席工作台最小闭环和 Token Provider 骨架 |
| `docs/e2e-acceptance-checklist.md` | 已沉淀最小闭环验收清单 |

当前基础链路仍保持轻量本地架构：

- 使用 MySQL。
- 用户端消息同步使用短轮询。
- 非知识检索链路暂不强制启用 Redis、RocketMQ、WebSocket。
- Phase 5 混合检索环境已确认：PostgreSQL 16 + pgvector 0.8.2、Elasticsearch 8.15.0 + `analysis-smartcn`、DashScope `text-embedding-v4`。
- 退款、换货等敏感业务不自动执行，只走人工审核或人工接管。

## 4. 总体阶段计划

### Phase 0. 工程骨架与契约

状态：已完成。

目标：

- 完成前后端分离目录。
- 完成 Java 多模块后端骨架。
- 完成前端 workspace 骨架。
- 明确 Agent 自动处理和人工兜底的模块边界。

关键文档：

- `requirements/AI客服Agent需求文档.md`
- `docs/AI-Agent客服框架说明.md`
- `docs/架构设计文档.md`

### Phase 1. 核心最小闭环 MVP

状态：已完成。

目标：

- 用户可通过 H5 发起自然语言请求。
- Agent 可自动处理低风险请求。
- 修改地址、取消订单等中风险操作可进入用户确认。
- 退款、人工客服等敏感请求可进入人工工单。
- 坐席可审批、接管、结束处理，并把结果回写到用户端消息流。

验收链路：

- `我的订单`：自动回复订单信息，返回 `skillExecutionId`。
- `查物流`：自动回复物流信息，返回 `skillExecutionId`。
- `修改地址`：用户端弹出地址确认表单，确认后自动执行。
- `取消订单`：用户确认后自动执行最小取消链路。
- `我要退款`：创建人工审核工单，坐席处理后用户端可看到系统消息。
- `人工客服`：创建人工接管工单，坐席开始和结束接管后用户端可看到人工消息。

关键文档：

- `docs/chat-api.md`
- `docs/skill-engine-api.md`
- `docs/workbench-api.md`
- `docs/e2e-acceptance-checklist.md`
- `docs/e2e-validation-2026-06-25.md`

### Phase 2. 稳定化与测试自动化

状态：基础版已完成，后续按风险补强。

目标：

- 把当前手工验收链路脚本化和页面自动化。
- 降低后续迭代引入回归的风险。
- 固化本地启动、健康检查和 E2E 流程。

建议任务顺序：

1. 补齐前端页面级 E2E，优先覆盖 `client-h5` 和 `workstation` 的核心链路。（已完成基础覆盖）
2. 补齐后端集成测试，覆盖 Gateway、Agent Core、Skill Engine、Workbench 的主流程。（已完成控制器契约基础覆盖）
3. 完善本地一键健康检查脚本，覆盖 8080、8081、8082、8083 和前端端口。（已完成基础覆盖）
4. 将手工验收记录标准化为固定模板。（已完成）

验证要求：

- 前端：对应 workspace 的 `lint`、`typecheck`、`build`。
- 后端：相关 Maven module `compile` 或测试。
- E2E：至少覆盖一条自动链路和一条人工兜底链路。

中间件要求：

- 不需要新增中间件。

### Phase 3. 坐席工作台增强

状态：下一优先级。

目标：

- 让坐席端更接近真实运营工作台。
- 增强工单筛选、优先级、备注、处理历史和人工接管体验。

建议任务顺序：

1. 工单列表增加更完整筛选：状态、风险等级、意图、坐席、时间范围。（已完成基础覆盖）
2. 工单详情增加处理备注和内部协作信息。（已完成基础覆盖）
3. 人工接管增加坐席发送消息能力。（已完成基础覆盖）
4. 操作日志增加更清晰的审计展示。（已完成基础覆盖）
5. 增加基础统计视图：待处理、处理中、已完成、超时风险。（已完成基础覆盖）

中间件要求：

- 第一轮不需要新增中间件。
- 若要做实时坐席提醒，再评估 WebSocket 或 SSE。

### Phase 4. 敏感业务工单细化

状态：进行中，退款、换货字段和审批结论结构基础覆盖已完成。

目标：

- 让退款、换货等 L3 请求具备更完整的工单上下文。
- 保持人工审核模式，不做真实资金或库存操作。

建议任务顺序：

1. 细化退款申请字段：订单号、原因、金额、凭证占位、用户诉求。（已完成基础覆盖）
2. 细化换货申请字段：订单号、商品、原因、期望处理方式、凭证占位。（已完成基础覆盖）
3. 增加工单审批结论结构：通过、驳回、补充材料、转人工接管。（已完成基础覆盖）
4. 更新 `docs/workbench-api.md` 和前端展示。（退款、换货、审批结论场景已完成基础覆盖）
5. 增加对应 E2E 验收场景。（退款、换货、审批结论场景已完成基础覆盖）

中间件要求：

- 不需要新增中间件。
- 不接真实支付、仓储、退款系统。

### Phase 5. 知识库能力增强

状态：混合检索基础闭环已完成，运营增强待继续。

目标：

- 从关键词 FAQ 逐步升级到可维护知识库。
- 先完成可运营的 FAQ 管理，再考虑 RAG。

建议任务顺序：

1. FAQ 数据从硬编码升级为数据库表或配置文件。
2. 增加 FAQ 管理接口或后台占位。
3. Agent Core 对知识类问题返回来源和置信度。
4. 增加未命中策略：低置信度转人工或提示换问法。
5. 使用 Elasticsearch BM25 与 pgvector 语义检索完成双路召回。
6. 在 Knowledge 服务内使用 RRF 融合两路排名，保留低置信度人工兜底。
7. 建立 MySQL FAQ 到两类检索索引的可重建同步机制和验收集。

当前进展：

- 已新增 `knowledge_faq` 表脚本和初始 FAQ 数据：`infra/sql/09-knowledge-faq-management.sql`。
- 已新增 FAQ 列表、新增、更新、状态变更接口，供后续知识库后台或运营工具接入。
- FAQ 查询链路保持 `POST /api/knowledge/faq/query` 不变，优先读 MySQL，表未初始化或无可用数据时回退到内置 FAQ。
- Agent Core 已对 FAQ 未命中或低置信度结果返回兜底 metadata，并提供 `REQUEST_HUMAN` 快捷动作创建人工接管工单。
- 坐席工作台已新增 FAQ 管理页面，支持列表查询、新增、编辑、启用和停用，复用现有 Knowledge FAQ 管理接口。
- 已确认混合检索架构：MySQL 是 FAQ 权威数据源，Elasticsearch 负责中文关键词/BM25 召回，pgvector 负责语义召回，Knowledge 服务执行应用层 RRF 融合。
- 已确认本地中间件版本：PostgreSQL 16 + pgvector 0.8.2、Elasticsearch 8.15.0；Elasticsearch 已启用安全认证并安装 `analysis-smartcn`。
- Embedding 使用 DashScope `text-embedding-v4`、1024 维，密钥沿用环境变量 `DASHSCOPE_API_KEY`。
- 已实现 Elasticsearch 中文关键词检索、pgvector 语义检索和应用层 RRF 融合，默认 `keyword` 模式不连接外部检索服务。
- 已实现 MySQL FAQ 新增、编辑、启停后的 ES/pgvector 尽力同步，以及 `POST /api/knowledge/faq/index/rebuild` 全量重建接口。
- 已新增 `scripts/smoke-knowledge-hybrid.ps1`，用于真实中间件的重建、双路命中和语义召回验收。
- 已于 2026-07-13 完成真实中间件验收：7 条 FAQ 成功写入 Elasticsearch 与 pgvector，共 16 个索引操作零失败；精确问句和语义改写均由 `hybrid-rrf-v1` 命中，置信度均为 `0.9`。
- 已修复 FAQ 初始化脚本的客户端字符集声明，并在本地 Runbook 固化 Windows PowerShell 5.1 的 UTF-8 导入方式。
- 已新增 `GET /api/knowledge/faq/index/status`，只读检查 MySQL、Elasticsearch、pgvector 的可用性、ACTIVE 文档数量和一致性；真实环境验证三端均为 7 条且状态健康。
- 混合检索冒烟脚本已把索引健康与一致性作为强制验收门槛。

中间件要求：

- FAQ 管理和现有关键词兜底仍只需要 MySQL。
- 混合检索阶段需要 PostgreSQL + pgvector、Elasticsearch 和 DashScope Embedding。
- Redis、RocketMQ、Nacos、WebSocket 和独立 Rerank 模型不属于本阶段依赖。

### Phase 6. 通知服务增强

状态：进行中。

目标：

- 让 Workbench、Agent Core、Notification 之间形成更清晰的事件边界。
- 当前阶段先做可追踪事件，不急于消息队列化。

建议任务顺序：

1. 通知事件落库。
2. 增加事件查询接口。
3. 增加失败重试或错误记录。
4. Workbench 操作结果和用户消息回写解耦。
5. 再评估 MQ 异步化。

当前进展：

- 已新增 `notification_event` 表脚本：`infra/sql/10-notification-event-store.sql`。
- `smartcs-notification` 已将 `POST /api/notifications/events` 接收的事件写入 MySQL。
- 已新增 `GET /api/notifications/events` 分页查询接口，支持按事件类型、工单、用户和状态筛选。
- 坐席工作台已新增通知事件只读页，支持按事件类型、工单、用户和状态查询，并展示投递状态、重试次数和失败原因。
- 已新增 `infra/sql/11-notification-delivery-status.sql`，记录投递状态、失败原因、重试次数、下次重试时间和成功投递时间。
- 已新增 `POST /api/notifications/events/{eventId}/delivery-result`，用于后续投递 worker 或 MQ 消费器回写 `DELIVERED` / `FAILED`。
- Workbench 已将审批结论和接管状态类用户侧事件收口到统一私有边界，业务动作不再重复散落“写用户消息 + 投递通知”的相邻组合。
- 已完成 MQ 异步化评估文档：`docs/notification-mq-evaluation.md`。当前结论是暂不引入 RocketMQ，继续使用 MySQL 可追踪事件，后续自动重试优先评估 Notification 内部定时 worker。
- 已实现默认关闭的 Notification 定时投递框架：支持 `ACCEPTED` 首次投递、到期 `FAILED` 重试、1/5/15/30 分钟退避、最大尝试次数和可插拔 `NotificationDeliveryChannel`。
- 已新增 `infra/sql/13-notification-delivery-lease.sql`，Notification worker 使用 `FOR UPDATE SKIP LOCKED`、实例 owner 和过期租约支持多实例安全领取。
- 2026-07-13 已使用两个 Notification 进程共享 MySQL 验证同一事件只由一个实例完成投递，租约过期事件可恢复领取。
- 已新增默认关闭的幂等 `USER_SESSION` 通道；短信、邮件和 APP Push 仍未接入。
- worker 显式开启时必须至少注册一个投递通道，否则应用启动失败，不会误消费待处理事件。
- 已固化用户会话消息解耦迁移顺序：先做 Workbench 事务 outbox 和 `eventId` 幂等，再接 `USER_SESSION` 通道，最后灰度关闭 Workbench 直接写消息。
- 已新增 `workbench_notification_outbox` 表、兼容发布器和默认关闭的投递 worker；开启后事件随工单操作事务入队，再异步调用 Notification，失败按有限退避重试。
- 已新增 `infra/sql/14-workbench-outbox-delivery-lease.sql`，Workbench outbox worker 使用 `FOR UPDATE SKIP LOCKED`、实例 owner 和过期租约支持多实例安全领取。
- 2026-07-13 已使用两个 Workbench 进程共享 MySQL 验证同一 outbox 事件只由一个实例发送，Notification 仅落一条事件，租约过期后可恢复领取。
- Workbench 事件已补齐 `messageRole` 和 `userMessageDeliveryMode`，Notification 使用 `eventId` 派生稳定 `messageId`；历史与 `DIRECT` 事件不会重复写消息。
- 已新增 `scripts/smoke-notification-user-session.ps1`，进程级验证正常投递、Notification 中断恢复、固定消息 ID 幂等重放和测试数据清理。
- 2026-07-13 已完成真实 MySQL 进程联调：Notification 停止期间 Workbench 不直写消息，服务恢复后事件进入 `DELIVERED`，重复重放后用户消息仍只有一条。
- 默认仍走同步 HTTP 辅助链路并由 Workbench 事务写 `cs_message`。迁移模式已经具备并通过验收，但在生产部署和持续观测方案完成前不切换默认值。

中间件要求：

- 初期只需要 MySQL。
- 如果引入 RocketMQ，必须先说明 Topic、消息格式、消费失败策略和本地启动方式。

### Phase 7. 实时消息升级

状态：已完成 SSE 最小技术尖刺，后续按需增强。

目标：

- 将用户端和坐席端从短轮询升级为更实时的消息体验。

候选方案：

- SSE：适合单向服务端推送，复杂度较低。
- WebSocket：适合双向实时会话，适合后续人工坐席实时聊天。
- SockJS：可作为兼容方案。

进入条件：

- 短轮询链路稳定。
- 工单消息和会话消息语义稳定。
- 用户确认需要真实实时体验。

中间件要求：

- 不一定需要额外中间件。
- 如果需要跨实例广播，再评估 Redis Pub/Sub 或 MQ。

当前进展：

- 已新增实时消息方案评估文档：`docs/realtime-messaging-evaluation.md`。
- 当前结论是继续保留短轮询，后续如要提升实时体验，优先做用户端 SSE 试点，不立即实现 WebSocket。
- 已完成 SSE 最小技术尖刺：Gateway 提供 `GET /api/chat/sessions/{sessionId}/events`，用户端 H5 可配置开启 SSE，失败后回退短轮询。

### Phase 8. 登录鉴权与权限

状态：进行中，JWT 校验、前端 Token Provider 骨架和用户会话归属校验已完成，真实账号中心接入待做。

目标：

- 替换当前开发态 `userId` 和 `operatorId`。
- 区分用户端身份、坐席身份、管理员身份。

建议任务顺序：

1. 明确认证方式：Session、JWT 或对接现有电商账号体系。（已完成方案文档，第一轮采用 JWT / 上游 Token 兼容的无状态方案）
2. Gateway 增加用户身份解析。
3. Workbench 增加坐席身份和权限校验。
4. 前端移除开发态固定用户配置。
5. 增加鉴权失败、权限不足、登录过期处理。

当前进展：

- 已新增登录鉴权与权限方案文档：`docs/auth-permission-plan.md`。
- 第一轮结论是不引入 Redis Session，优先做无状态 Token / JWT 兼容方案。
- 已新增 `smartcs-common` 身份上下文模型和标准身份头常量。
- Gateway 已支持标准身份头、开发用户头和旧请求体 `userId` 的兼容解析，并向 Agent Core 透传标准身份头。
- Workbench 已支持标准身份头、开发坐席头和旧请求体 `operatorId` 的兼容解析，并新增 `GET /api/workbench/me`。
- 用户端 H5 / APP H5 已发送开发用户身份头和可选 Bearer Token。
- 坐席工作台已发送开发坐席身份头，操作前通过 `GET /api/workbench/me` 获取当前坐席，页面不再硬编码 `operatorId`。
- 用户端 H5 和坐席工作台已完成 401 / 403 / 登录过期错误文案归一化。
- 坐席工作台已基于 `principalType`、`roles` 和 `operatorId` 对领取、审批、接管、人工消息、内部备注做按钮级显隐和禁用控制。
- Workbench 后端已对当前坐席身份做服务端角色兜底校验：非 `AGENT` / `SUPERVISOR` / `ADMIN` 角色不能执行领取、审批、接管、人工消息和内部备注操作。
- 已新增生产 Token / JWT 校验接入说明：`docs/auth-jwt-validation.md`。
- `smartcs-common` 已新增无状态 Bearer JWT 解析器，支持 `sub`、`principal_type`、`roles`、`permissions`、`iss`、`aud`。
- Gateway 已支持可配置 Bearer JWT 用户身份解析，默认关闭，启用后 Token 优先于标准身份头、开发头和旧请求体 `userId`。
- Workbench 已支持可配置 Bearer JWT 坐席身份解析，默认关闭，启用后 Token 优先于标准身份头、开发头和旧请求体 `operatorId`，且用户 Token 不能回退成坐席身份。
- Gateway / Workbench 已新增 `smartcs.auth.strict-enabled` 强制鉴权开关，默认关闭；开启后不再使用开发头、旧请求体身份和 Workbench 本地坐席兜底。
- Workbench 工单列表、统计、详情和操作日志读取接口已统一经过坐席身份校验。
- 用户端 H5 / APP H5 已新增 Token Provider 骨架，支持宿主注入 `getAccessToken` / `refreshAccessToken` / `redirectToLogin`。
- 坐席工作台已新增 Token Provider 骨架，支持后台门户或统一登录壳注入 Token 获取、刷新和登录跳转。
- Gateway 已新增用户会话归属校验：发送消息、快捷动作、会话状态查询、消息轮询和 SSE 订阅都会拒绝跨用户访问已有会话。
- 下一轮可继续对接真实账号中心 / 登录页，或进入 Phase 9 补齐启动、健康检查和部署说明。

中间件要求：

- 第一轮不需要新增中间件。
- 如后续切换 Redis Session，必须提前确认本地 Redis 可用。

### Phase 9. 观测、运维与部署

状态：进行中。

目标：

- 提升本地、测试、生产环境的可运行性和可排障性。

建议任务顺序：

1. 完善启动文档和端口清单。（已完成基础覆盖）
2. 统一日志字段：`traceId`、`sessionId`、`ticketId`、`userId`。（Gateway 聊天入口、Agent Core 入口和会话查询层、Skill Engine 技能执行层、Workbench 工单服务层、Notification 事件入口和服务层已完成基础覆盖）
3. 增加基础健康检查和依赖检查。（已完成基础覆盖）
4. 补齐 Docker Compose。（已完成 MySQL 最小依赖覆盖）
5. 再考虑 K8s、监控、告警和链路追踪。

当前进展：

- 已新增 `scripts/check-local-stack.ps1`，覆盖 Gateway、Agent Core、Skill Engine、Workbench、Knowledge、Notification 和三端前端入口健康检查。
- 已新增 `scripts/smoke-e2e.ps1`，覆盖 FAQ、查订单、查物流、取消订单确认、退款人工审核入口、人工接管入口和用户会话归属保护。
- 已新增日志字段规范文档：`docs/observability-log-fields.md`。
- `smartcs-common` 已新增 `LogFields` 公共字段工具，Gateway 聊天入口已使用统一 `traceId/sessionId/userId/streamId` 日志片段。
- Agent Core 入口和会话查询层已使用统一 `traceId/sessionId/userId` 日志片段。
- Skill Engine 技能执行层已使用统一 `traceId/sessionId/userId` 日志片段，并保留 `skillId/executionId/status` 排障字段。
- Workbench 工单服务层已使用统一 `traceId/ticketId/userId/operatorId` 日志片段，并保留 `approvalId/takeoverId/status` 排障字段。
- Notification 事件入口和服务层已使用统一 `traceId/ticketId/userId/sessionId/operatorId` 日志片段，并保留 `eventId/status/retryCount` 排障字段。
- 已新增 `infra/docker/compose.yml` 和 `.env.example`，提供 MySQL 8.x 本地最小依赖编排，并自动挂载 `infra/sql` 初始化脚本。
- 已新增 `docs/local-development-runbook.md`，固化本地 MySQL、IDEA 后端启动、前端入口、健康检查、烟测和常见排障流程。
- 坐席工作台已补齐 FAQ 表单单元测试和当前坐席身份 E2E mock；工单、审批、人工接管、通知事件等 6 条 Playwright 场景可重复运行。
- 坐席工作台已采用路由级懒加载，页面模块按访问路径加载，降低首次进入工作台的入口包体积。
- 已将 `react-router-dom` 升级至 `6.30.4`、将锁定的 `form-data` 升级至 `4.0.6`，消除对应生产依赖审计告警；当前最新稳定版 `@ant-design/pro-components` 仍通过上游 `path-to-regexp` 保留审计风险，待上游发布兼容修复或单独评估替代方案。

中间件要求：

- 项目内 Docker Compose 当前只启用 MySQL；Phase 5 使用用户虚拟机中已有的 pgvector 与 Elasticsearch，Redis、RocketMQ 暂不接入业务链路。
- 监控阶段再评估 Prometheus、Grafana、OpenTelemetry。

## 5. 后续任务优先队列

当前推荐优先级：

1. Phase 8：登录鉴权与权限，优先完成身份上下文和开发兼容模式。
2. Phase 9：观测、运维与部署，补齐启动、健康检查和本地部署说明。
3. Phase 5：实现 Elasticsearch + pgvector 双路召回、应用层 RRF 融合和索引重建能力。
4. Phase 6：通知服务增强，后续再评估自动重试 worker 和 MQ 异步化。
5. Phase 7：实时消息升级，后续按需从用户端 SSE 扩展到坐席端实时提醒。

## 6. 变更准入规则

后续任何新需求进入开发前，都要先判断：

- 是否属于上述阶段。
- 是否会改变接口契约。
- 是否会改变数据库结构。
- 是否需要新增中间件。
- 是否影响已有 E2E 链路。
- 是否需要 Claude Code 或其他 Agent 配合前端实现。

如果答案涉及接口、数据库或中间件，必须先更新对应文档并确认风险。

## 7. 完成定义

一个任务只有同时满足以下条件，才算完成：

- 代码或文档改动已落地。
- 相关验证命令已执行，或明确说明无法执行的原因。
- 没有混入无关文件。
- 文档和验收清单已同步更新。
- 已准备或提交聚焦 PR。
- 后续事项已记录到本文档、验收清单或 PR 说明中。

## 8. 文档维护规则

- 本文档只维护项目级路线，不记录每个接口的详细字段。
- API 字段写入对应 `docs/*-api.md`。
- E2E 步骤写入 `docs/e2e-acceptance-checklist.md`。
- 实际联调结果写入 `docs/e2e-validation-*.md`。
- Agent 交接信息写入 `docs/agent-handoff.md`。
- 当项目阶段完成、优先级变化或新增中间件时，必须同步更新本文档。
