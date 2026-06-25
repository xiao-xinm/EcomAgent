# infra/sql

数据库脚本目录。

当前按阶段拆分：

- `01-schema.sql`：第一阶段核心主链路表结构
- `02-skill-schema.sql`：第二阶段技能注册和编排表结构
- `03-enforce-enum-columns.sql`：MySQL 8.0.12 兼容的枚举字段强约束修正
- `04-risk-approval-schema.sql`：风控规则和人工审核流转表结构
- `05-knowledge-schema.sql`：知识库和 RAG 元数据表结构
- `06-seed-risk-rules.sql`：风控规则初始化
- `07-seed-skill-registry.sql`：技能注册初始化

第一阶段已覆盖：

- 会话：`cs_session`
- 消息：`cs_message`
- 意图识别记录：`intent_record`
- 风控决策记录：`risk_decision`
- 人工审核工单：`work_order`
- 工单操作记录：`work_order_action`
- 审计日志：`audit_log`

设计说明见 [数据库设计-第一阶段.md](./数据库设计-第一阶段.md)。

第二阶段已覆盖：

- 技能注册：`skill_registry`
- 技能槽位：`skill_slot`
- API 编排步骤：`skill_api_step`
- 技能执行日志：`skill_execution_log`

设计说明见 [数据库设计-第二阶段.md](./数据库设计-第二阶段.md)。

当前最小技能种子见 `07-seed-skill-registry.sql`，包含：

- `order.query`
- `logistics.query`
- `order.modify_address`
- `order.cancel`
- `refund.apply`
- `exchange.apply`

其中退款和换货技能默认是 `L3 + REVIEW_ONLY`，只进入人工审核，不由 Agent 直接执行业务动作。取消订单是 `L2 + SYNC`，需要用户确认后才会更新本地订单影子状态，且不处理真实退款。

第三阶段已覆盖：

- 风控规则：`risk_rule`
- 审批任务：`approval_task`
- 审批操作：`approval_action`
- 人工接管：`human_takeover`

设计说明见 [数据库设计-第三阶段.md](./数据库设计-第三阶段.md)。

当前最小风控规则种子见 `06-seed-risk-rules.sql`，包含：

- 退款申请强制人工审核
- 换货申请强制人工审核
- 低置信度转人工接管
- 异常情绪转人工接管
- 高金额操作人工审核
- 高频操作转人工接管
- 地址修改按订单状态动态升级
- 取消订单执行前用户确认
