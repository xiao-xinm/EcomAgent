# AI Agent 客服框架说明

本文档用于约束当前阶段的工程骨架：只搭建模块边界、交互契约和目录结构，不写具体业务实现。

## 目标模式

系统采用“Agent 自动处理 + 人工兜底”的混合服务模式：

- 常见低风险请求由 Agent 自动处理，如 FAQ、订单查询、物流查询。
- 可控中低风险请求由 Agent 编排处理，如未发货订单修改地址。
- 需要用户确认或存在业务风险的请求进入确认链路。
- 退款、换货、高金额、异常情绪、频繁操作等敏感场景进入人工审核或人工接管。

## 模块边界

| 模块 | 职责 | 当前状态 |
|------|------|----------|
| backend/smartcs-gateway | 用户消息入口、鉴权限流、会话管理、消息标准化 | 已建骨架 |
| backend/smartcs-agent-core | NLU、对话管理、槽位填充、风控路由、人工兜底决策 | 已建骨架 |
| backend/smartcs-skill-engine | 技能注册、技能路由、声明式 API 编排 | 已建骨架 |
| backend/smartcs-workbench | 人工审核、工单流转、坐席接管、审批审计 | 已建骨架 |
| backend/smartcs-knowledge | FAQ、政策知识库、RAG 检索增强 | 已建骨架 |
| backend/smartcs-notification | 用户通知、坐席提醒、事件通知 | 已建骨架 |
| backend/smartcs-common | 跨服务契约、DTO、枚举、错误码、工具 | 已建骨架 |
| frontend/package.json | 前端 workspace 聚合入口 | 已建骨架 |
| frontend/workstation | 坐席工作台前端 | 已建目录 |
| frontend/client-h5 | 用户端 H5 聊天组件 | 已建目录 |
| frontend/app-h5 | APP 内嵌 H5 聊天组件 | 已建目录 |
| infra | Docker、SQL、MQ、K8s 等基础设施配置 | 已建目录 |

## 主链路

1. 用户在 H5 / APP-H5 发起自然语言请求。
2. Gateway 完成鉴权、限流、消息标准化和会话上下文加载。
3. Agent Core 完成意图识别、实体抽取、槽位填充和风险评估。
4. Risk Router 输出路由决策：自动处理、用户确认、人工审核或人工接管。
5. Skill Engine 按声明式技能定义编排业务 API 调用链。
6. Workbench 处理需要人工审核或人工接管的请求。
7. Notification 根据事件向用户或坐席发送通知。
8. 全链路保留 trace_id、session_id、ticket_id 与审计日志。

## 风控分层

| 等级 | 处理方式 | 示例 |
|------|----------|------|
| L0 | Agent 自动回答或查询 | FAQ、查询订单、查询物流 |
| L1 | Agent 执行并通知 | 未发货订单修改地址、补充发票信息 |
| L2 | Agent 预执行，用户确认后执行 | 已出库前临界状态修改地址、取消订单 |
| L3 | 发起人工审核或人工接管 | 退款、换货、高金额补偿、异常情绪投诉 |

原则：

- 风险只升不降，宁严勿松。
- L3 不允许 Agent 直接执行。
- 动态风控要综合订单状态、金额、用户情绪、操作频率和历史风险。
- 人工接管时必须透传会话上下文、识别结果、槽位、风控原因和推荐处理动作。

## 后续实现顺序

1. 在 `backend/smartcs-common` 中定义跨模块契约：标准消息、意图结果、槽位、风险决策、工单事件。
2. 在 `backend/smartcs-gateway` 中接入最小 WebSocket / REST 入口和会话状态。
3. 在 `backend/smartcs-agent-core` 中实现 NLU 结果结构、对话状态机和风险路由接口。
4. 在 `backend/smartcs-skill-engine` 中实现技能注册表、技能路由和声明式执行框架。
5. 在 `backend/smartcs-workbench` 中实现 L3 工单创建、审批、接管和审计。
6. 在 `backend/smartcs-knowledge` 中接入 FAQ / RAG，只服务知识问答，不承载实时订单数据。
7. 补齐 infra 的本地 Docker Compose、SQL DDL、MQ Topic 和监控配置。
