# Knowledge FAQ API

本文档记录 `smartcs-knowledge` 当前阶段的最小 FAQ 能力。

当前实现目标是打通 `Agent Core -> Knowledge -> 用户回复` 的链路，不引入 Milvus、ES、Redis、RocketMQ 或 WebSocket。

## 服务

- 服务名：`smartcs-knowledge`
- 默认端口：`8084`
- 本地健康检查：`GET http://localhost:8084/api/health`
- 本地依赖：无新增中间件

## FAQ 查询

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
    "answerId": "faq_xxx",
    "question": "退款多久到账",
    "answer": "退款到账时间取决于支付渠道...",
    "matched": true,
    "confidence": 0.88,
    "matchedKeywords": ["退款", "到账", "多久"],
    "source": "faq-keyword-v1",
    "createdAt": "2026-06-25T03:00:00Z"
  },
  "traceId": "faq-smoke-001"
}
```

## Agent Core 路由

Agent Core 当前只把知识问法识别为 `faq.query`，例如：

- `退款多久到账`
- `退货规则是什么`
- `发票怎么开`
- `运费怎么算`
- `保价规则`

敏感操作仍保持原有人工兜底：

- `我要退款` -> `refund.apply` -> `HUMAN_REVIEW`
- `我要换货` -> `exchange.apply` -> `HUMAN_REVIEW`

FAQ 命中后的用户回复应满足：

- `routeDecision = AUTO_REPLY`
- `riskLevel = L0`
- `metadata.intent = faq.query`
- `metadata.knowledgeAnswerId` 存在
- `metadata.knowledgeSource = faq-keyword-v1`

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
