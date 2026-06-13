# Agent Handoff

本文档是 SmartCS-Agent 项目中 Codex 与 Claude Code 的共享交接板。

所有 Agent 开始工作前请先阅读：

- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- 本文件

完成任务后，请更新本文件的“最新交接记录”。

## 当前阶段目标

完成人工坐席工作台最小闭环：

```text
用户发起敏感请求
-> Agent Core 创建人工工单 / 审批任务 / 接管记录
-> smartcs-workbench 提供坐席后端接口
-> frontend/workstation 提供坐席前端页面
-> 坐席可领取、查看、审核、接管、结束处理
```

当前不接真实订单、退款、换货业务系统。

## 当前职责分工

### Codex

负责：

- 后端模块实现。
- 数据库表和种子数据。
- 接口文档。
- 后端编译验证。

### Claude Code

负责：

- `frontend/**` 前端实现。
- 本阶段重点是 `frontend/workstation` 人工坐席工作台。
- 按接口文档实现页面和 API 调用。
- 不修改后端和数据库。

## 已完成内容

### 后端最小用户链路

已完成：

- `smartcs-gateway`
  - `POST /api/chat/messages`
  - 转发用户消息到 Agent Core
  - UTF-8 请求/响应声明
- `smartcs-agent-core`
  - Mock 意图识别
  - 查询 `skill_registry`
  - 查询 `risk_rule`
  - 写入会话、消息、意图、风控决策
  - 对 L3 操作创建 `work_order`
  - 对退款/换货创建 `approval_task`
  - 对低置信度/转人工创建 `human_takeover`
- 已验证：
  - “我要退款”返回 `HUMAN_REVIEW`
  - 可生成 `work_order` 和 `approval_task`

### 数据库

已执行并验证：

- `infra/sql/01-schema.sql`
- `infra/sql/02-skill-schema.sql`
- `infra/sql/03-enforce-enum-columns.sql`
- `infra/sql/04-risk-approval-schema.sql`
- `infra/sql/06-seed-risk-rules.sql`
- `infra/sql/07-seed-skill-registry.sql`

当前 MySQL：

- database: `smartcs_agent`
- username: `root`
- password: `root`

### 人工坐席后端

已完成 `smartcs-workbench` 最小闭环接口。

主要文件：

- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/SmartCsWorkbenchApplication.java`
- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/controller/HealthController.java`
- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/controller/WorkbenchTicketController.java`
- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/controller/WorkbenchExceptionHandler.java`
- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/ticket/WorkbenchDtos.java`
- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/ticket/WorkbenchTicketService.java`
- `backend/smartcs-workbench/src/main/java/com/smartcs/agent/workbench/ticket/WorkbenchOperationException.java`
- `backend/smartcs-workbench/src/main/resources/application.yml`
- `backend/smartcs-workbench/pom.xml`

接口文档：

- `docs/workbench-api.md`

给 Claude 的前端任务提示词：

- `docs/claude-workstation-frontend-prompt.md`

## smartcs-workbench 接口摘要

服务默认地址：

```text
http://localhost:8083
```

接口：

- `GET /api/health`
- `GET /api/workbench/tickets`
- `GET /api/workbench/tickets/{ticketId}`
- `GET /api/workbench/tickets/{ticketId}/actions`
- `POST /api/workbench/tickets/{ticketId}/claim`
- `POST /api/workbench/tickets/{ticketId}/approval/approve`
- `POST /api/workbench/tickets/{ticketId}/approval/reject`
- `POST /api/workbench/tickets/{ticketId}/takeover/start`
- `POST /api/workbench/tickets/{ticketId}/takeover/finish`

完整字段、请求体、响应体以 `docs/workbench-api.md` 为准。

## 后端验证记录

Codex 已执行：

```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-workbench -am -DskipTests compile
```

结果：

```text
BUILD SUCCESS
```

Codex 已查询 MySQL，确认当前有测试工单：

- `HUMAN_REVIEW` 退款审核工单
- `HUMAN_TAKEOVER` 低置信度接管工单

## Claude Code 下一步任务

Claude Code 请执行：

```text
请阅读并执行 docs/claude-workstation-frontend-prompt.md
```

目标：

- 实现 `frontend/workstation` 人工坐席工作台前端。
- 基于 `docs/workbench-api.md` 调用真实后端接口。
- 实现工单列表、详情、消息、审批信息、操作日志、操作按钮。
- 保持后台管理系统风格，克制、清晰、高信息密度。

## Claude Code 权限边界

默认只允许修改：

- `frontend/**`

可以在必要时读取：

- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/workbench-api.md`
- `docs/claude-workstation-frontend-prompt.md`
- 本文件

不要修改：

- `backend/**`
- `infra/sql/**`
- `docs/workbench-api.md`
- `requirements/**`
- 数据库结构
- 后端接口实现

若发现接口文档不够，请在本文件记录问题，不要擅自改后端。

## Claude Code 前端验证建议

如果依赖未安装，先执行：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm install
```

完成后尽量执行：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
```

本地启动：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run dev:workstation
```

默认页面：

```text
http://localhost:3001
```

前端环境变量：

```text
VITE_WORKSTATION_API_BASE_URL=http://localhost:8083
```

## 联调前提

用户需要在 IDEA 中启动：

- `smartcs-agent-core`，端口 `8081`
- `smartcs-gateway`，端口 `8080`
- `smartcs-workbench`，端口 `8083`

然后可以先通过 Gateway 创建测试工单：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$body = '{"userId":"u1001","content":"我要退款"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat/messages `
  -ContentType 'application/json; charset=utf-8' `
  -Body $bytes
```

再通过 Workbench 查询：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8083/api/workbench/tickets?status=PENDING&pageNo=1&pageSize=20"
```

## 最新交接记录

### 2026-05-22 Codex

完成：

- 新增 `smartcs-workbench` 后端接口。
- 新增 `docs/workbench-api.md`。
- 新增 `docs/claude-workstation-frontend-prompt.md`。
- 编译验证 `smartcs-workbench` 成功。

未完成：

- Codex 尝试调用 Claude CLI 两次，但 Codex 桌面权限审批超时，未能直接让 Claude 自动执行前端任务。

下一步：

- 用户在 Claude Code 中输入：

```text
请阅读并执行 docs/claude-workstation-frontend-prompt.md
```

- Claude 完成后，请更新本文件，追加它改动了哪些前端文件、运行了哪些验证命令、还有哪些问题。

### 2026-05-22 Claude Code

完成：

基于 `docs/workbench-api.md` 接口文档，实现 `frontend/workstation` 人工坐席工作台前端最小闭环。

新增 / 修改的前端文件：

- `frontend/workstation/package.json` — 重写，添加真实依赖（React 18、Ant Design 5、@ant-design/pro-components、react-router-dom、axios、dayjs、Vite、TypeScript、ESLint、Prettier）
- `frontend/workstation/vite.config.ts` — Vite 配置，含 `/api` 代理到 `localhost:8083`
- `frontend/workstation/tsconfig.json` — TypeScript 配置
- `frontend/workstation/vite-env.d.ts` — Vite 类型声明
- `frontend/workstation/index.html` — 入口 HTML
- `frontend/workstation/.eslintrc.cjs` — ESLint 配置
- `frontend/workstation/.prettierrc` — Prettier 配置
- `frontend/workstation/.env` — 环境变量 `VITE_WORKSTATION_API_BASE_URL=http://localhost:8083`
- `frontend/workstation/src/types/workbench.ts` — 完整类型定义，全部来自 `docs/workbench-api.md`，无编造字段
- `frontend/workstation/src/services/api.ts` — API 客户端，覆盖所有 9 个接口
- `frontend/workstation/src/constants/workbench.ts` — 状态枚举中文映射和颜色配置
- `frontend/workstation/src/layouts/BasicLayout.tsx` — 侧边栏 + 顶栏布局
- `frontend/workstation/src/routes/index.tsx` — 路由配置（/tickets 列表、/tickets/:ticketId 详情）
- `frontend/workstation/src/pages/TicketList/index.tsx` — 工单列表页，ProTable + 筛选 + 领取按钮
- `frontend/workstation/src/pages/TicketDetail/index.tsx` — 工单详情页，含工单信息、会话消息、审批信息、接管信息、操作日志时间线、操作按钮（领取/审批通过/审批驳回/开始接管/结束接管）
- `frontend/workstation/src/App.tsx` — 根组件，ConfigProvider + BrowserRouter
- `frontend/workstation/src/main.tsx` — 入口文件

页面能力：

- 工单列表（筛选：状态、路由决策、关键词；分页；领取待处理工单）
- 工单详情（工单基本信息、会话消息气泡、审批信息卡片、接管信息卡片、操作日志时间线）
- 操作按钮：领取工单、审批通过、审批驳回、开始人工接管、结束人工接管
- 所有操作带备注弹窗

验证结果：

```bash
npm install                              # 436 packages installed
npm run lint --workspace @smartcs/workstation      # clean, no errors
npm run typecheck --workspace @smartcs/workstation  # clean, no errors
npm run build --workspace @smartcs/workstation     # success, 15s
```

build 产物：

- `dist/index.html` (0.33 kB)
- `dist/assets/index-BLpZue2g.js` (1,978 kB gzip 628 kB) — antd 体积正常

未完成 / 问题：

- WebSocket 实时推送尚未实现（当前阶段不需要，已预留 `src/websocket/` 目录）。
- `src/components/.gitkeep`、`src/pages/.gitkeep`、`src/websocket/.gitkeep` 保留未删除。

### 2026-05-22 Codex Review

复核 Claude 前端实现后，Codex 做了少量收口：

- `frontend/workstation/src/pages/TicketDetail/index.tsx`
  - 修复操作弹窗未等待异步接口结果的问题。
  - 统一捕获操作失败并展示错误提示。
  - 增加操作中的 loading 状态。
  - 避免已通过、已驳回、已解决、已关闭工单继续显示“开始接管”等不可执行操作。
- `frontend/workstation/vite.config.ts`
  - Vite 代理目标改为读取 `VITE_WORKSTATION_API_BASE_URL`，默认仍为 `http://localhost:8083`。
- `.gitignore`
  - 增加 `*.tsbuildinfo`，避免 TypeScript 增量缓存进入版本管理。

Codex 已重新执行：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
```

结果：

```text
lint clean
typecheck clean
build success
```

备注：

- build 仍有 Ant Design / ProComponents 首包体积 warning，不影响当前最小闭环运行；后续可通过路由懒加载或手动分包优化。
- Codex 尝试启动 `npm run dev:workstation` 的后台进程，但沙箱下 `Start-Process` 没有稳定返回；请用户在本机终端或 IDE 中启动前端。

### 2026-05-23 Codex

完成：
- 新增 `smartcs-skill-engine` 最小可运行后端骨架。
- 新增 Skill Engine 健康检查、技能列表、技能详情、技能执行接口。
- Skill Engine 从 `skill_registry`、`skill_slot`、`skill_api_step` 读取声明式技能配置。
- Skill Engine 当前返回最小 mock 执行结果，并写入 `skill_execution_log`。
- Agent Core 新增 `SkillEngineClient`，在 `AUTO_REPLY` / `AUTO_EXECUTE` 路径调用 Skill Engine。
- Agent Core 保留本地兜底：Skill Engine 未启动或调用失败时，仍返回原有 mock 回复并写本地执行日志。
- 人工审核、人工接管、确认后执行路径暂不依赖 Skill Engine，避免影响敏感工单闭环。
- 新增接口文档 `docs/skill-engine-api.md`。

验证：

```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-agent-core,smartcs-skill-engine -am -DskipTests compile
```

结果：

```text
BUILD SUCCESS
```

本地联调时请在 IDEA 中额外启动 `smartcs-skill-engine`，端口 `8082`。自动技能测试详见 `docs/skill-engine-api.md`。
### 2026-05-23 Codex

完成：
- 新增公共契约 `ChatActionRequest`。
- Gateway 新增 `POST /api/chat/actions`，转发用户快捷动作到 Agent Core。
- Agent Core 新增 `POST /api/agent/actions`。
- Agent Core 支持 `CONFIRM` / `CANCEL` 动作：
  - `CONFIRM`：读取会话当前意图，调用 Skill Engine 继续执行技能，写入用户 ACTION 消息和 Agent 回复。
  - `CANCEL`：写入用户 ACTION 消息，记录取消日志，会话收束为 `COMPLETED`。
  - 上下文缺失或找不到技能时，转人工接管。
- 新增接口文档 `docs/chat-api.md`。

验证：

```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-gateway,smartcs-agent-core,smartcs-skill-engine -am -DskipTests compile
```

结果：

```text
BUILD SUCCESS
```

联调时需要重启：
- `smartcs-gateway`，端口 `8080`
- `smartcs-agent-core`，端口 `8081`
- `smartcs-skill-engine`，端口 `8082`

前端 H5 后续应按 `docs/chat-api.md` 将 quickActions 点击事件接到 `POST /api/chat/actions`。
### 2026-05-23 Codex

完成：
- 新增公共返回模型 `ChatSessionView`、`ChatMessageView`。
- Agent Core 新增用户端会话查询服务 `ChatSessionQueryService`。
- Agent Core 新增内部查询接口：
  - `GET /api/agent/sessions/{sessionId}`
  - `GET /api/agent/sessions/{sessionId}/messages?limit=100`
- Gateway 新增用户端公开查询接口：
  - `GET /api/chat/sessions/{sessionId}`
  - `GET /api/chat/sessions/{sessionId}/messages?limit=100`
- 更新 `docs/chat-api.md`，补充会话状态和消息列表接口。

验证：

```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-gateway,smartcs-agent-core,smartcs-workbench -am -DskipTests compile
```

结果：

```text
BUILD SUCCESS
```

用途：
- H5 用户端可以在人工审核、人工接管或坐席处理后，通过查询接口刷新会话状态和消息列表。
- 当前阶段可做轮询；后续再升级 WebSocket/SSE 推送。

### 2026-06-13 Codex

完成：
- Workbench 审批通过、审批驳回、开始人工接管、结束人工接管时，会在原会话写入一条用户可见 `cs_message`。
- 审批结果使用 `SYSTEM` 角色消息，人工接管进度使用 `HUMAN_AGENT` 角色消息。
- 用户端 H5 不需要新增接口，通过 `GET /api/chat/sessions/{sessionId}/messages?limit=100` 刷新即可看到坐席处理结果。
- 消息 `metadata` 会带上 `source=workbench`、`ticketId`、`operatorId`、`actionType`，并按动作补充 `approvalId` / `takeoverId` 等上下文。
- 更新 `docs/workbench-api.md` 和 `docs/chat-api.md`，说明坐席动作对用户消息列表的影响。

验证：
```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-gateway,smartcs-agent-core,smartcs-skill-engine,smartcs-workbench -am -DskipTests compile
```

结果：
```text
BUILD SUCCESS
```

联调提示：
- 修改的是 `smartcs-workbench` 后端服务，IDEA 中需要重启 Workbench 服务端口 `8083`。
- 用户端查看结果仍走 Gateway 端口 `8080` 的会话消息查询接口。
