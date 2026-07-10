# Knowledge FAQ API

本文档记录 `smartcs-knowledge` 当前阶段的 FAQ 查询与 FAQ 管理能力。

当前目标是把 `Agent Core -> Knowledge -> 用户回复` 的 FAQ 链路从硬编码升级为可维护数据，不引入 Milvus、ES、Redis、RocketMQ 或 WebSocket。

## 服务信息

- 服务名：`smartcs-knowledge`
- 默认端口：`8084`
- 健康检查：`GET http://localhost:8084/api/health`
- 本地依赖：MySQL
- 初始化脚本：`infra/sql/09-knowledge-faq-management.sql`

## FAQ 查询

Agent Core 使用该接口查询知识类问题。

```http
POST /api/knowledge/faq/query
Content-Type: application/json; charset=utf-8
```

请求体：

```json
{
  "traceId": "faq-smoke-001",
  "sessionId": "s_faq_smoke",
  "userId": "u1001",
  "channel": "h5",
  "question": "退款多久到账"
}
```

响应体：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "answerId": "faq_refund_arrival",
    "question": "退款多久到账",
    "answer": "退款到账时间取决于支付渠道...",
    "matched": true,
    "confidence": 0.78,
    "matchedKeywords": ["退款", "到账", "多久"],
    "source": "faq-keyword-v1",
    "createdAt": "2026-07-04T03:00:00Z"
  },
  "traceId": "faq-smoke-001"
}
```

查询说明：

- 服务优先读取 `knowledge_faq` 表中 `ACTIVE` 状态的 FAQ。
- 如果表未初始化或没有 `ACTIVE` 数据，会回退到内置 FAQ，保证用户聊天链路不中断。
- 当前仍是关键词匹配，`source` 保持为 `faq-keyword-v1`。

## FAQ 管理接口

这些接口用于知识库后台或运营工具接入。坐席工作台已提供 FAQ 管理入口：

```text
frontend/workstation -> /knowledge/faq
```

前端默认通过 `VITE_WORKSTATION_KNOWLEDGE_API_BASE_URL` 指向 Knowledge 服务，本地默认端口为 `8084`。

### 查询 FAQ 列表

```http
GET /api/knowledge/faq?pageNo=1&pageSize=20&status=ACTIVE&keyword=退款
```

响应体：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "records": [
      {
        "faqId": "faq_refund_arrival",
        "question": "退款多久到账",
        "answer": "退款到账时间取决于支付渠道...",
        "keywords": ["退款", "到账", "多久", "几天", "退钱"],
        "category": "after_sale",
        "status": "ACTIVE",
        "priority": 100,
        "createdAt": "2026-07-04T03:00:00Z",
        "updatedAt": "2026-07-04T03:00:00Z"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 20
  },
  "traceId": "..."
}
```

### 新增 FAQ

```http
POST /api/knowledge/faq
Content-Type: application/json; charset=utf-8
```

请求体：

```json
{
  "question": "优惠券过期了还能用吗",
  "answer": "优惠券过期后通常不能继续使用，具体以活动规则为准。",
  "keywords": ["优惠券", "过期", "活动"],
  "category": "promotion",
  "status": "ACTIVE",
  "priority": 10
}
```

### 更新 FAQ

```http
PUT /api/knowledge/faq/{faqId}
Content-Type: application/json; charset=utf-8
```

请求体与新增 FAQ 相同。

### 更新 FAQ 状态

```http
POST /api/knowledge/faq/{faqId}/status
Content-Type: application/json; charset=utf-8
```

请求体：

```json
{
  "status": "DISABLED"
}
```

状态枚举：

- `DRAFT`：草稿，不参与用户查询。
- `ACTIVE`：启用，参与用户查询。
- `DISABLED`：停用，不参与用户查询。

## Agent Core 路由

Agent Core 当前只把政策、规则、时效类知识问法识别为 `faq.query`，例如：

- `退款多久到账`
- `退货规则是什么`
- `发票怎么开`
- `运费怎么算`
- `保价规则`

敏感操作仍保持人工兜底：

- `我要退款` -> `refund.apply` -> `HUMAN_REVIEW`
- `我要换货` -> `exchange.apply` -> `HUMAN_REVIEW`

FAQ 命中后的用户回复应满足：

- `routeDecision = AUTO_REPLY`
- `riskLevel = L0`
- `metadata.intent = faq.query`
- `metadata.knowledgeAnswerId` 存在
- `metadata.knowledgeSource = faq-keyword-v1`

FAQ 未命中或置信度低于 Agent Core 阈值时：

- `metadata.knowledgeFallback = true`
- `metadata.knowledgeMissReason = FAQ_NOT_MATCHED` 或 `FAQ_LOW_CONFIDENCE`
- `metadata.knowledgeMissStrategy = REPHRASE_OR_REQUEST_HUMAN`
- `quickActions` 会包含 `actionType = REQUEST_HUMAN` 的“转人工客服”按钮
- 用户点击后会创建 `HUMAN_TAKEOVER` 工单和人工接管记录

## 本地验证命令

```powershell
$body = @{
  traceId = "faq-smoke-001"
  sessionId = "s_faq_smoke"
  userId = "u1001"
  channel = "h5"
  question = "退款多久到账"
} | ConvertTo-Json -Compress

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8084/api/knowledge/faq/query `
  -ContentType 'application/json; charset=utf-8' `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```
