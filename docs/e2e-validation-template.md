# SmartCS 端到端联调记录 - YYYY-MM-DD

本文档是每次本地或测试环境端到端验收的记录模板。新建联调记录时，复制本文档为：

```text
docs/e2e-validation-YYYY-MM-DD.md
```

若同一天执行多轮验收，可追加短后缀：

```text
docs/e2e-validation-YYYY-MM-DD-round2.md
```

## 1. 验收范围

- 验收日期：YYYY-MM-DD
- 验收人：
- 代码分支 / Commit：
- 本轮目标：
- 是否涉及新增中间件：否
- 中间件说明：
  - MySQL：
  - Redis：
  - RocketMQ：
  - WebSocket / SSE：
  - Milvus / ES：

本轮不验收的内容：

- 待填写

## 2. 环境状态

### 后端服务

| 服务 | 端口 | 状态 | 备注 |
|------|------|------|------|
| Gateway | `8080` | 未验证 |  |
| Agent Core | `8081` | 未验证 |  |
| Skill Engine | `8082` | 未验证 |  |
| Workbench | `8083` | 未验证 |  |
| Knowledge | `8084` | 未验证 |  |
| Notification | `8085` | 未验证 |  |

### 前端服务

| 应用 | 端口 | 状态 | 备注 |
|------|------|------|------|
| 用户端 H5 | `3000` | 未验证 |  |
| 坐席工作台 | `3001` | 未验证 |  |
| APP H5 | `3002` | 未验证 |  |

### 本地栈健康检查

```powershell
cd D:\NewProject\EcomAgent
.\scripts\check-local-stack.ps1
```

结果：

```text
未执行
```

若未启动 APP H5，可使用：

```powershell
.\scripts\check-local-stack.ps1 -SkipAppH5
```

## 3. 静态检查和自动化测试

### 前端检查

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run lint
npm run typecheck
npm run build
npm run test
npm run test:e2e
```

结果：

```text
未执行
```

备注：

- 待填写

### 后端控制器契约测试

```powershell
cd D:\NewProject\EcomAgent\backend
mvn -pl smartcs-gateway,smartcs-agent-core,smartcs-skill-engine,smartcs-workbench,smartcs-knowledge,smartcs-notification -am test
```

结果：

```text
未执行
```

备注：

- 待填写

### 入口级 smoke 脚本

```powershell
cd D:\NewProject\EcomAgent
.\scripts\smoke-e2e.ps1
```

结果：

```text
未执行
```

若当前机器没有 `mysql` 命令，可临时跳过取消订单数据准备：

```powershell
.\scripts\smoke-e2e.ps1 -SkipOrderCancelDbSetup
```

## 4. 核心链路验收

### 4.1 自动查订单

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：`我的订单`
- sessionId：
- routeDecision：
- riskLevel：
- skillExecutionId：
- 是否创建人工工单：
- 用户端展示结果：

结论：未验证

### 4.2 自动查物流

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：`我的物流到哪了`
- sessionId：
- routeDecision：
- riskLevel：
- skillExecutionId：
- intent：
- 用户端展示结果：

结论：未验证

### 4.3 FAQ 知识库自动问答

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：
- sessionId：
- routeDecision：
- knowledgeAnswerId：
- knowledgeMatched：
- knowledgeConfidence：
- 用户端展示结果：

结论：未验证

### 4.4 修改地址确认

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：`我要修改订单 E2E-ORDER-1002 的收货地址`
- sessionId：
- 初始 routeDecision：
- 是否出现确认动作：
- H5 是否弹出地址确认表单：
- 表单订单号是否自动带入：
- 提交后 routeDecision：
- skillExecutionId：
- 用户端展示结果：

结论：未验证

### 4.5 取消订单确认

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：
- sessionId：
- 初始 routeDecision：
- 是否出现确认动作：
- 确认后 routeDecision：
- skillExecutionId：
- 数据库订单状态：
- 用户端展示结果：

结论：未验证

### 4.6 退款人工审核

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：`我要退款`
- sessionId：
- ticketId：
- routeDecision：
- riskLevel：
- 坐席端是否可见工单：
- 坐席处理动作：审批通过 / 审批驳回 / 需要补充材料
- 用户端是否看到 `SYSTEM` 回写消息：
- 用户端展示结果：

结论：未验证

### 4.7 人工接管

- 用户入口：用户端 H5 / APP H5 / Postman
- 输入内容：`人工客服`
- sessionId：
- ticketId：
- routeDecision：
- riskLevel：
- 坐席端是否可见工单：
- 坐席是否可领取：
- 开始接管后 takeoverStatus：
- 用户端是否看到 `HUMAN_AGENT` 接入消息：
- 结束接管后 takeoverStatus：
- 用户端是否看到 `HUMAN_AGENT` 结束消息：

结论：未验证

## 5. 页面验收

### 用户端 H5

- 页面地址：`http://localhost:3000`
- 是否可打开：
- 是否可发送消息：
- 是否可展示 Agent 回复：
- 是否可展示系统消息：
- 是否可展示人工坐席消息：
- 是否可弹出地址确认表单：
- 轮询是否能刷新远端消息：

结论：未验证

### APP H5

- 页面地址：`http://localhost:3002`
- 是否可打开：
- channel 是否为 `app-h5`：
- 是否复用聊天、短轮询、快捷动作和地址表单：

结论：未验证

### 坐席工作台

- 页面地址：`http://localhost:3001`
- 工单列表是否可加载：
- 工单详情是否可打开：
- 领取工单是否可执行：
- 审批通过 / 驳回是否可执行：
- 开始 / 结束人工接管是否可执行：
- 操作后详情、消息、日志是否刷新：

结论：未验证

## 6. 问题记录

| 编号 | 严重级别 | 模块 | 问题描述 | 复现步骤 | 处理状态 | 备注 |
|------|----------|------|----------|----------|----------|------|
|  | P0 / P1 / P2 |  |  |  | 未处理 |  |

严重级别说明：

- P0：接口失败、CORS、按钮缺失、状态不同步、payload 不符合契约、链路不可闭环。
- P1：提示不清晰、轮询延迟体验差、错误信息不友好。
- P2：样式细节、文档措辞、测试补充。

## 7. 结论

本轮验收结论：未通过 / 部分通过 / 通过

通过的内容：

- 待填写

未通过或遗留内容：

- 待填写

下一步建议：

- 待填写
