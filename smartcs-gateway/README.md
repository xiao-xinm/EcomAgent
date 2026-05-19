# smartcs-gateway

接入网关与会话管理模块，负责用户触点到 Agent Core 的统一入口。

当前只保留工程骨架，后续可放置：

- REST / WebSocket 入口
- 渠道消息标准化协议
- 鉴权、限流、灰度、熔断过滤器
- 会话生命周期与上下文读写
- Agent Core 路由和异常兜底转人工

边界：

- 不直接执行电商业务动作
- 不直接调用退款、换货、改地址等业务 API
- 所有敏感操作只透传到 Agent Core / Workbench 决策链路
