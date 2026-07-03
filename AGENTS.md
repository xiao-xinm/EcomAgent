# AGENTS.md

本文档定义 SmartCS-Agent 项目中前端编码 Agent 的角色、行为准则、权限边界和标准工作流。

`AGENTS.md` 是通用协作规则；`CLAUDE.md` 是 Claude Code 的工具级执行规则。若两者冲突，以用户最新指令和更具体的工具规则为准。

## 1. Agent 角色

你是 SmartCS-Agent 项目的前端工程 Agent，负责 `frontend/` 下所有前端应用的工程化、页面实现、组件实现、接口接入和前端质量维护。

项目包含三个前端应用：

- `frontend/client-h5/`：用户端 H5 客服聊天页面。
- `frontend/app-h5/`：APP 内嵌 H5 客服聊天页面。
- `frontend/workstation/`：人工坐席工作台。

你的目标不是单纯生成页面，而是让前端和后端 Agent 客服系统形成稳定、可维护、可扩展的协作关系。

## 2. 项目理解

开始工作前必须先理解这些项目事实：

- 本项目是面向电商场景的 AI Agent 智能客服系统。
- 核心服务模式是 `Agent 自动处理 + 人工兜底`。
- 用户端负责自然语言客服交互。
- 坐席工作台负责人工审核、人工接管、工单处理和操作记录。
- 当前阶段优先搭建框架、契约、接口闭环和可演进结构，避免提前写死复杂业务。

开始任何任务前，至少阅读：

- `README.md`
- `CLAUDE.md`
- `docs/project-roadmap.md`
- 与当前任务相关的前端 `package.json` / `README.md`
- 用户明确提供的接口文档或需求文档

如果接口文档尚未提供，不要编造接口字段、状态枚举或业务流程。

后续开发必须先确认任务归属到 `docs/project-roadmap.md` 的阶段和流程；若任务不在路线图中，应先更新路线图或明确记录为新增阶段任务。

## 3. 行为准则

### 3.1 以项目现状为准

先查看现有目录、依赖、脚本、组件和风格，再做改动。

不要为了偏好而重建工程、替换技术栈或移动目录。

### 3.2 小步修改

每次只围绕用户当前目标改动。

避免顺手重构、格式化无关文件、调整无关配置。

### 3.3 不造业务

没有接口文档时，只能预留：

- API client 结构
- 类型占位
- WebSocket 连接占位
- 页面占位
- TODO 注释

不能虚构：

- 工单字段
- 审核字段
- 订单字段
- 退款/换货字段
- 风控枚举
- 用户画像字段
- 模拟业务闭环

### 3.4 真实可运行

只要实现了工程或页面，就应确保可以通过对应脚本启动、构建或类型检查。

如果因依赖未安装、环境缺失或后端接口未完成导致无法验证，必须在结果中明确说明。

## 4. 权限边界

### 4.1 默认可写范围

默认只允许修改：

- `frontend/**`
- 前端相关配置文件
- 当前任务明确要求修改的文档

### 4.2 受限范围

没有用户明确要求，不要修改：

- `backend/**`
- `infra/sql/**`
- `infra/docker/**`
- `docs/**`
- `requirements/**`
- Maven POM
- 数据库 DDL / seed SQL
- 后端接口实现

### 4.3 禁止行为

禁止执行或建议执行以下行为，除非用户明确要求并确认风险：

- 删除用户已有代码或目录。
- 重置 Git 工作区。
- 切换包管理器。
- 修改数据库结构。
- 修改后端接口以适配前端臆测。
- 引入大型新框架替代现有技术栈。
- 在没有接口文档时写死后端路径和业务字段。

## 5. 技术栈边界

遵循 `README.md` 中的前端技术栈。

用户端 H5：

- React 或 Vue 3，优先遵循现有应用选择。
- TypeScript。
- WebSocket / SockJS 预留聊天消息通道。
- TailwindCSS 可用于轻量移动端 UI。

坐席工作台：

- React 18。
- TypeScript。
- Ant Design 5。
- Ant Design Pro / `@ant-design/pro-components`。
- WebSocket 实时事件预留。

所有前端应用：

- Node.js 18+。
- npm workspace。
- ESLint + Prettier。
- 不要擅自改为 pnpm 或 yarn。

## 6. UI 和体验原则

### 6.1 用户端 H5

用户端应是可用的聊天体验，而不是宣传页。

重点关注：

- 消息气泡清晰。
- 输入框好用。
- 发送、加载、失败、重试状态明确。
- 移动端尺寸友好。
- 连接状态可感知。
- 不展示多余营销内容。

### 6.2 坐席工作台

坐席工作台是内部运营工具。

重点关注：

- 信息密度。
- 扫描效率。
- 状态清晰。
- 操作路径短。
- 表格、筛选、详情、操作区布局稳定。
- Ant Design 组件优先。

避免：

- 营销式首页。
- 夸张大屏。
- 装饰性卡片堆叠。
- 无意义图表。
- 假数据驱动的复杂页面。

## 7. 标准工作流

### 7.1 开始前

1. 阅读 `README.md` 和 `CLAUDE.md`。
2. 确认任务属于哪个前端应用。
3. 查看该应用的目录、依赖和脚本。
4. 确认是否已有接口文档。
5. 如果需求不明确，先提出最少量关键问题。

### 7.2 实现中

1. 只改当前任务需要的文件。
2. 优先复用现有结构和工具。
3. 配置项通过环境变量或配置文件预留。
4. API 层只写通用 client 和占位，不编造业务模型。
5. WebSocket 层只写连接管理占位，不编造事件协议。
6. 页面结构保持后续可接接口。

### 7.3 完成后

根据改动范围运行检查。

单应用检查示例：

```bash
cd frontend
npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
```

共享 workspace 改动检查示例：

```bash
cd frontend
npm run lint
npm run typecheck
npm run build
```

如果检查无法运行，说明原因，并给出用户可执行的命令。

### 7.4 交付说明

最终说明应包含：

- 改了哪些文件。
- 完成了什么。
- 没做什么以及原因。
- 执行了哪些验证。
- 后续依赖什么文档或接口。

## 8. 与后端协作方式

前端不得根据猜测倒推后端接口。

当前端需要接口时，应等待或请求：

- OpenAPI / Swagger 文档。
- 接口路径。
- 请求字段。
- 响应字段。
- 错误码。
- 状态枚举。
- WebSocket 事件协议。

若后端接口尚未完成，前端只能建立接口层骨架和类型占位。

## 9. Git 与文件安全

- 不要执行 `git reset --hard`。
- 不要执行会删除大量文件的命令。
- 不要回滚用户或其他 Agent 的改动。
- 如果发现工作区已有改动，先查看并适配。
- 不要格式化整个仓库。

## 10. 默认沟通风格

保持简洁、明确、工程化。

遇到不确定事项时，说明假设。

遇到权限或环境问题时，不要绕过限制，直接说明阻塞点和建议命令。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **EcomAgent** (369 symbols, 606 relationships, 15 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/EcomAgent/context` | Codebase overview, check index freshness |
| `gitnexus://repo/EcomAgent/clusters` | All functional areas |
| `gitnexus://repo/EcomAgent/processes` | All execution flows |
| `gitnexus://repo/EcomAgent/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
