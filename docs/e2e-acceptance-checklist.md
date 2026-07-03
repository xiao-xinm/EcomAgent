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
- `smartcs-knowledge`：`8084`
- `smartcs-notification`：`8085`

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

联调前可以先执行本地栈健康检查，确认后端健康接口和前端入口都已启动：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\check-local-stack.ps1
```

如果暂时没有启动 APP H5：

```powershell
.\scripts\check-local-stack.ps1 -SkipAppH5
```

服务启动后，可以先执行入口级烟测脚本：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\smoke-e2e.ps1
```

脚本会自动检查 FAQ、查订单、查物流、取消订单确认、退款人工审核入口和人工接管入口。坐席审批、开始接管、结束接管等页面操作仍按下方清单手工验收。

也可以执行前端页面级 E2E。该脚本会启动用户端 H5 和坐席工作台，并 mock 后端接口验证页面契约，不要求启动 MySQL 或后端服务：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run test:e2e
```

首次运行如提示缺少 Playwright 浏览器，先执行：

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run install:e2e-browsers
```

后端控制器契约测试不依赖 MySQL 或其他中间件，可用于快速确认 Gateway、Agent Core、Skill Engine、Workbench、Knowledge、Notification 的基础 API 包装契约：

```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-gateway,smartcs-agent-core,smartcs-skill-engine,smartcs-workbench,smartcs-knowledge,smartcs-notification -am test
```

每次完成一轮页面或端到端联调后，应复制 [e2e-validation-template.md](./e2e-validation-template.md) 新建验收记录：

```text
docs/e2e-validation-YYYY-MM-DD.md
```

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

## 2.1 自动查物流

在用户端 H5 发送：

```text
我的物流到哪了
```

预期结果：

- 用户端收到 Agent 物流回复
- 路由决策为 `AUTO_REPLY`
- 响应 metadata 中包含 `skillExecutionId`
- 响应 metadata 中 `intent = logistics.query`
- 回复内容包含承运商、运单号、物流状态或最新物流节点
- 不创建人工工单

## 2.5 FAQ 知识库自动问答

在用户端 H5 发送：

```text
退款多久到账
```

预期结果：

- 用户端收到 Agent FAQ 回答
- 路由决策为 `AUTO_REPLY`
- 风险等级为 `L0`
- 响应 metadata 中 `intent = faq.query`
- 响应 metadata 中包含 `knowledgeAnswerId`
- 不创建人工工单
- `我要退款` 仍必须进入 `HUMAN_REVIEW`，不能被 FAQ 分支拦截

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

## 3.5 取消订单确认

在用户端 H5 发送：

```text
我要取消订单 E2E-ORDER-1002
```

预期结果：

- 用户端收到确认类回复
- 路由决策为 `CONFIRM_BEFORE_EXECUTE`
- 用户点击“确认继续”后，前端调用 `POST /api/chat/actions`
- 成功后回复中包含 `skillExecutionId`
- 响应 metadata 中 `intent = order.cancel`
- 回复内容说明订单已取消，同时说明不处理真实退款
- `ecom_order.order_status` 更新为 `CANCELLED`
- 不创建人工工单

如果未提供订单号，预期确认后不会自动取消最近订单，而是提示需要补充订单号。

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
- 审批完成后，Workbench 日志显示通知事件投递结果；Notification 日志出现 `APPROVAL_APPROVED` 或 `APPROVAL_REJECTED`
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
- 开始/结束接管后，Workbench 日志显示通知事件投递结果；Notification 日志出现 `TAKEOVER_STARTED` 或 `TAKEOVER_FINISHED`
- 工单详情页操作日志出现领取、开始接管、结束接管动作

## 6. 常见排查

- 用户端没有看到坐席消息：确认 Gateway `8080` 和 Agent Core `8081` 都在运行，并等待 3 秒左右的轮询间隔。
- 坐席端按钮状态不对：刷新工单详情页，确认 Workbench `8083` 返回的 `takeover.status` 是否为 `ASSIGNED` 或 `IN_PROGRESS`。
- 浏览器 CORS 报错：确认 Gateway 和 Workbench 都允许当前前端端口访问。
- 自动技能没有结果：确认 Skill Engine `8082` 已启动，且 MySQL 中存在测试订单和技能配置。
