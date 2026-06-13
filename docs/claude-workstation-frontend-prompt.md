# Claude Prompt: Build Workstation Frontend

请阅读项目根目录的 `AGENTS.md`、`CLAUDE.md`，以及 `docs/workbench-api.md`。

现在只实现 `frontend/workstation` 人工坐席工作台前端，不要修改：

- `backend/**`
- `infra/sql/**`
- `docs/**`
- `requirements/**`

## 目标

基于 `docs/workbench-api.md` 的接口，完成一个可运行的 React 18 + TypeScript + Ant Design 5 / ProComponents 坐席工作台最小闭环。

## 必须遵守

1. 使用现有 npm workspace。
2. 前端应用固定在 `frontend/workstation`。
3. 保留根 workspace 命令 `npm run dev:workstation`。
4. 默认开发端口使用 `3001`。
5. API Base URL 使用 `VITE_WORKSTATION_API_BASE_URL`，默认 `http://localhost:8083`。
6. 不要实现真实退款、换货、订单业务。
7. 不要编造 `docs/workbench-api.md` 之外的后端业务字段。
8. 不要修改后端或数据库。

## 页面能力

实现坐席工作台最小页面：

- 工单列表
- 工单详情
- 会话消息展示
- 审批信息展示
- 操作日志时间线
- 领取工单
- 审批通过
- 审批驳回
- 开始人工接管
- 结束人工接管

## 技术要求

- React 18
- TypeScript
- Vite
- Ant Design 5
- `@ant-design/pro-components`
- ESLint + Prettier

## UI 风格

这是内部坐席工作台，不是营销页。

请做成克制、清晰、高信息密度的后台管理界面：

- 表格适合快速扫描
- 详情区适合处理工单
- 操作按钮状态清晰
- 日志和消息可追溯
- 不要大屏风格
- 不要 hero 区域
- 不要假数据驱动复杂图表

## 验证

完成后请尽量运行：

```bash
cd frontend
npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
```

如果依赖未安装或检查无法运行，请明确说明原因和用户需要执行的命令。
