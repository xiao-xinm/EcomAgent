# SmartCS 最小闭环验收清单

本文档用于验证当前阶段的 `Agent 自动处理 + 人工兜底` 最小闭环。

当前验收不依赖 Redis、RocketMQ 或 WebSocket。用户端消息同步采用短轮询：

- 用户端 H5：`GET /api/chat/sessions/{sessionId}/messages?limit=100`
- 坐席工作台：操作成功后重新拉取工单详情、会话消息和操作日志
- APP H5：复用用户端 H5 的聊天能力和短轮询策略，默认渠道为 `app-h5`

## 1. 启动服务

### 后端

在 IDEA 中启动：

- `smartcs-gateway`：`8080`
- `smartcs-agent-core`：`8081`
- `smartcs-skill-engine`：`8082`
- `smartcs-workbench`：`8083`

MySQL 使用当前开发库，账号密码按本地配置。

### 前端

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run dev:client-h5
npm run dev:app-h5
npm run dev:workstation
```

默认页面：

- 用户端 H5：`http://localhost:3000`
- APP H5：`http://localhost:3002`
- 坐席工作台：`http://localhost:3001`

## 2. 自动查订单

在用户端 H5 发送：

```text
我的订单
```

预期结果：

- 用户端收到 Agent 回复
- 路由决策为 `AUTO_REPLY`
- 响应 metadata 中包含 `skillExecutionId`
- 不创建人工工单

APP H5 也应在 `http://localhost:3002` 具备同样能力，后端会话 `channel` 应为 `app-h5`。

## 3. 修改地址确认

在用户端 H5 发送：

```text
我要修改订单 E2E-ORDER-1002 的收货地址
```

预期结果：

- 用户端收到确认类回复
- 路由决策为 `CONFIRM_BEFORE_EXECUTE`
- 用户点击“确认继续”后，H5 打开地址确认表单
- 用户填写订单号、收货人、手机号、省市区、详细地址等字段后提交
- 前端调用 `POST /api/chat/actions`，并按 `orderNo + newAddress + changeReason` 结构传入 `payload`
- 成功后回复中包含技能执行结果和 `skillExecutionId`

如果页面提交失败，可用 Postman 按 `docs/chat-api.md` 中的确认动作示例排查完整 `payload`。

## 4. 退款人工审核

在用户端 H5 发送：

```text
我要退款
```

预期结果：

- 用户端收到已创建人工审核工单的回复
- 坐席工作台工单列表出现 `HUMAN_REVIEW` 工单
- 坐席进入详情页后可以领取、审批通过或审批驳回
- 审批完成后，用户端 H5 通过轮询看到 `SYSTEM` 角色消息
- 工单详情页操作日志出现对应审批动作

## 5. 人工接管

在用户端 H5 发送：

```text
人工客服
```

预期结果：

- 用户端收到已转人工的回复
- 坐席工作台工单列表出现 `HUMAN_TAKEOVER` 工单
- 坐席领取后，详情页显示“开始接管”
- 点击“开始接管”后，接管状态变为 `IN_PROGRESS`
- 用户端 H5 通过轮询看到 `HUMAN_AGENT` 角色消息：人工客服已接入
- 点击“结束接管”后，接管状态变为 `RESOLVED`
- 用户端 H5 通过轮询看到人工处理结束消息
- 工单详情页操作日志出现领取、开始接管、结束接管动作

## 6. 常见排查

- 用户端没有看到坐席消息：确认 Gateway `8080` 和 Agent Core `8081` 都在运行，并等待 3 秒左右的轮询间隔。
- 坐席端按钮状态不对：刷新工单详情页，确认 Workbench `8083` 返回的 `takeover.status` 是否为 `ASSIGNED` 或 `IN_PROGRESS`。
- 浏览器 CORS 报错：确认 Gateway 和 Workbench 都允许当前前端端口访问。
- 自动技能没有结果：确认 Skill Engine `8082` 已启动，且 MySQL 中存在测试订单和技能配置。
