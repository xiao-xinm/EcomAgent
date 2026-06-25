# SmartCS 端到端联调记录 - 2026-06-25

## 1. 环境

- 日期：2026-06-25
- 后端服务：
  - Gateway `8080`：UP
  - Agent Core `8081`：UP
  - Skill Engine `8082`：UP
  - Workbench `8083`：UP
- 前端服务：
  - 用户端 H5 `3000`：UP
  - APP H5 `3002`：UP
  - 坐席工作台 `3001`：UP
- 中间件：本轮验收未使用 Redis、RocketMQ、WebSocket。

## 2. 静态检查

用户端 H5：

```powershell
npm run lint --workspace @smartcs/client-h5
npm run typecheck --workspace @smartcs/client-h5
npm run build --workspace @smartcs/client-h5
```

结果：通过。

坐席工作台：

```powershell
npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
```

结果：通过。

备注：坐席工作台仍有 Ant Design / ProComponents 首包体积 warning，不影响本轮验收。

## 3. API 验收结果

本轮 runId：`20260625102119`

| 链路 | 会话 / 工单 | 关键结果 | 结论 |
| --- | --- | --- | --- |
| 查订单 | `s_e2e_order_20260625102119` | `AUTO_REPLY`，`skillExecutionId=se_48b96c5f-6a17-4c18-8b80-8c6e4dd2042b` | 通过 |
| 修改地址确认 | `s_e2e_addr_20260625102119` | 初始 `CONFIRM_BEFORE_EXECUTE`，确认后 `AUTO_EXECUTE`，`skillExecutionId=se_0e4f84e9-9c2b-4f89-9ff2-ef9b957205cb` | 通过 |
| 退款人工审核 | `s_e2e_refund_20260625102119` / `wo_838f63a1-8c23-4b93-80b5-4383e4a7ad9c` | 工单审批通过，用户端消息列表出现 `SYSTEM` 回写消息 | 通过 |
| 人工接管 | `s_e2e_takeover_20260625102119` / `wo_dccc3d51-95ac-4b01-87a4-9bc80282da18` | 开始接管 `IN_PROGRESS`，结束接管 `RESOLVED`，用户端消息列表出现开始和结束的 `HUMAN_AGENT` 消息 | 通过 |
| APP H5 查订单 | `s_apph5_order_20260625104000` | `channel=app-h5`，`AUTO_REPLY`，`skillExecutionId=se_1e7cc9f6-5f06-4969-a3e1-910834e64837` | 通过 |
| APP H5 人工接管 | `s_apph5_takeover_20260625104000` / `wo_9639369c-4592-45ca-ae1a-80f83785faee` | `channel=app-h5`，`HUMAN_TAKEOVER`，成功创建人工接管工单 | 通过 |

## 4. 页面验收结果

- 用户端 H5 可以打开 `http://localhost:3000`。
- APP H5 可以打开 `http://localhost:3002`，并复用用户端 H5 的聊天界面、短轮询和状态条。
- 在 H5 中发送“我要修改订单 E2E-ORDER-1002 的收货地址”后，点击“确认继续”会打开地址确认表单。
- 地址确认表单会自动带入订单号 `E2E-ORDER-1002`。
- 在页面填写地址并提交后，表单关闭，聊天区出现“已为订单 E2E-ORDER-1002 修改收货地址。”。
- 坐席工作台可以打开 `http://localhost:3001`，工单列表可以加载本轮生成的人工审核和人工接管工单。

## 5. 结论

当前最小闭环通过：

- Agent 自动查询订单
- 修改地址确认后自动执行
- 退款进入人工审核并回写用户消息
- 人工接管开始 / 结束并回写用户消息
- APP H5 入口可运行，并能以 `app-h5` 渠道走 Gateway 链路

下一步可以进入 FAQ 知识库最小链路，先做关键词匹配，不接 Milvus / ES / RAG。
