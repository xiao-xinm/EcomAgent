# 电商业务域数据库设计

本文档描述 SmartCS-Agent 第四阶段的最小电商业务域表结构，对应脚本：

```text
infra/sql/09-ecommerce-domain-schema.sql
```

## 1. 阶段目标

前三个阶段已经打通客服主链路：

```text
用户消息 -> Agent 路由 -> 技能执行日志 / 人工工单 -> Workbench 处理 -> 用户消息回写
```

第四阶段开始补真实业务数据面，让后续技能可以逐步从 mock 过渡到可查询、可校验、可审计的业务表。

本阶段只覆盖最小闭环需要的业务事实：

- 用户有哪些订单。
- 订单当前是什么状态。
- 订单是否允许自动修改地址。
- 当前和历史收货地址是什么。
- 退款/换货申请是否已进入售后流程。

## 2. 表清单

| 表 | 作用 | 服务的客服能力 |
| --- | --- | --- |
| `ecom_order` | 订单主表，保存订单、支付、物流和当前地址快照 | 查询订单、修改地址风控判断 |
| `ecom_order_item` | 订单明细表，保存 SKU、数量、金额和明细售后状态 | 查询订单、退款/换货申请 |
| `ecom_order_address` | 订单地址历史表，保存初始地址和后续变更记录 | 修改地址、人工地址处理 |
| `ecom_after_sale_request` | 售后申请表，保存退款/换货申请及审核关联 | 退款审核、换货审核 |

## 3. 与现有客服链路的关系

### 3.1 查询订单

后续 `order.query` 技能优先读取：

- `ecom_order`
- `ecom_order_item`

返回给用户的订单状态、物流状态和商品摘要来自这些表。

### 3.2 修改地址

后续 `order.modify_address` 技能先读取 `ecom_order`：

- `order_status`
- `logistics_status`
- `can_modify_address`

如果允许自动修改，则新增一条 `ecom_order_address`，并更新 `ecom_order` 的当前地址快照。

如果不允许自动修改，仍走现有风控和人工兜底链路：

- `work_order`
- `human_takeover`
- 必要时写入 `ecom_order_address.source_ticket_id`

### 3.3 退款和换货

退款/换货仍然是 L3 敏感操作。Agent 不直接退款或换货。

后续 `refund.apply` / `exchange.apply` 在人工审核通过后，可以写入或更新：

- `ecom_after_sale_request`
- `work_order`
- `approval_task`

`ecom_after_sale_request.source_ticket_id` 和 `source_approval_id` 用来把业务申请和坐席审核链路关联起来。

## 4. 设计原则

- 业务表只承载当前闭环需要的最小事实，不提前设计完整电商系统。
- 订单、地址、售后数据独立于客服会话，客服系统通过 ID 关联。
- 地址表保留历史记录，便于追踪 Agent 或人工坐席何时改过地址。
- 售后申请表只记录申请和审核结果，不表示真实退款到账或真实换货发货。
- 涉及手机号、姓名、详细地址的字段在生产写入前必须脱敏或加密。
- 当前不引入用户表，继续沿用现有 `user_id` 字符串。

## 5. 当前不包含的能力

本阶段不实现：

- 支付流水。
- 仓储出库。
- 物流轨迹明细。
- 真实退款打款。
- 真实换货发货。
- 商品主数据。
- 用户画像。
- 优惠券、发票、补偿金。

这些能力可以等订单查询和售后申请闭环稳定后，再按具体技能逐步补表。

## 6. 后续开发顺序

建议后续小 PR 顺序：

1. 为 `09-ecommerce-domain-schema.sql` 增加最小测试订单 seed。
2. 在 Skill Engine 中增加订单查询 repository/service，只读 `ecom_order` 和 `ecom_order_item`。
3. 让 `order.query` 从 mock 响应切换为真实 MySQL 查询。
4. 增加修改地址的状态判断和地址历史写入。
5. 审批通过后，把退款/换货人工结果写入 `ecom_after_sale_request`。

每一步都应保持一个 PR 一个闭环，先验证后合并。
