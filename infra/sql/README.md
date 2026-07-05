# infra/sql

数据库脚本目录。

当前按阶段和增量拆分：

- `01-schema.sql`：第一阶段核心主链路表结构。
- `02-skill-schema.sql`：第二阶段技能注册和编排表结构。
- `03-enforce-enum-columns.sql`：MySQL 8.0.12 兼容的枚举字段约束修正。
- `04-risk-approval-schema.sql`：风控规则和人工审核流转表结构。
- `06-seed-risk-rules.sql`：风控规则初始化。
- `07-seed-skill-registry.sql`：技能注册初始化。
- `08-work-order-internal-note-action.sql`：工单内部备注动作类型增量脚本。
- `09-knowledge-faq-management.sql`：FAQ 知识库管理表结构和初始数据。
- `10-notification-event-store.sql`：通知事件落库表结构。

第一阶段已覆盖：

- 会话：`cs_session`
- 消息：`cs_message`
- 意图识别记录：`intent_record`
- 风控决策记录：`risk_decision`
- 人工审核工单：`work_order`
- 工单操作记录：`work_order_action`
- 审计日志：`audit_log`

第二阶段已覆盖：

- 技能注册：`skill_registry`
- 技能槽位：`skill_slot`
- API 编排步骤：`skill_api_step`
- 技能执行日志：`skill_execution_log`

当前最小技能种子见 `07-seed-skill-registry.sql`，包含：

- `order.query`
- `logistics.query`
- `order.modify_address`
- `order.cancel`
- `refund.apply`
- `exchange.apply`

第三阶段已覆盖：

- 风控规则：`risk_rule`
- 审批任务：`approval_task`
- 审批动作：`approval_action`
- 人工接管：`human_takeover`

当前最小风控规则种子见 `06-seed-risk-rules.sql`，退款和换货默认是 `L3 + REVIEW_ONLY`，只进入人工审核，不由 Agent 直接执行业务动作。

第五阶段已开始：

- FAQ 管理：`knowledge_faq`

`smartcs-knowledge` 查询链路优先读取 `knowledge_faq` 中的 `ACTIVE` 数据；表未初始化或没有可用数据时会回退到内置 FAQ。

第六阶段已开始：

- 通知事件：`notification_event`

`smartcs-notification` 接收 Workbench 投递的审批、人工接管、人工消息等通知事件，并写入 `notification_event` 供后续查询、重试和事件解耦使用。
