# smartcs-common

公共基础模块，只作为 JAR 被其他服务依赖，不作为独立服务启动。

当前已提供第一版公共契约骨架：

| 包 | 内容 |
|----|------|
| `constant` | 共享字段名、默认配置常量 |
| `dto` | `ApiResponse`、`PageQuery`、`PageResult` |
| `domain` | 标准消息、Agent 回复、意图识别、槽位、对话上下文、风控决策、人工工单、审计事件、服务健康状态 |
| `enums` | 错误码、消息角色、消息类型、对话状态、风险等级、路由决策、工单状态 |
| `exception` | `SmartCsException` 基础异常 |
| `util` | `TraceIds` 等轻量工具 |

约束：

- 不引入 Web 容器依赖
- 不放业务流程实现
- 只承载跨服务契约和基础工具
- 所有敏感操作的执行逻辑必须放在业务模块或人工审核链路中，common 只表达数据结构
