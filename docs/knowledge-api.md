# Knowledge FAQ API

本文档记录 `smartcs-knowledge` 当前阶段的 FAQ 查询与 FAQ 管理能力。

默认 `keyword` 模式仍使用 MySQL FAQ 关键词匹配。配置 `SMARTCS_RETRIEVAL_MODE=hybrid` 后，查询会优先使用 Elasticsearch 中文关键词/BM25 召回与 pgvector 语义召回，并在 Knowledge 服务内使用 RRF 融合排名。

## 服务信息

- 服务名：`smartcs-knowledge`
- 默认端口：`8084`
- 健康检查：`GET http://localhost:8084/api/health`
- 当前关键词链路依赖：MySQL
- 混合检索依赖：PostgreSQL + pgvector、Elasticsearch + `analysis-smartcn`、DashScope Embedding
- 初始化脚本：`infra/sql/09-knowledge-faq-management.sql`

## 混合检索边界

- MySQL `knowledge_faq` 是 FAQ 权威数据源。
- Elasticsearch 保存可重建的关键词检索文档，负责中文分词、BM25 和字段过滤。
- pgvector 保存可重建的 1024 维语义向量，Embedding 模型固定为 `text-embedding-v4`。
- Knowledge 服务分别获取两路 Top-K，并在应用层执行 RRF，避免直接比较不同检索引擎的原始分数。
- 任一检索引擎不可用时允许降级到另一条检索链路；全部不可用时继续使用现有 FAQ 关键词兜底和人工接管策略。
- 当前阶段不引入 Redis、RocketMQ、Milvus、Nacos、WebSocket 或独立 Rerank 模型。

混合检索返回来源：

- `hybrid-rrf-v1`：Elasticsearch 与 pgvector 都召回同一 FAQ。
- `elasticsearch-bm25-v1`：仅关键词检索命中。
- `pgvector-cosine-v1`：仅语义检索命中。
- `faq-keyword-v1`：混合检索无候选或不可用，回退现有 MySQL/内置 FAQ。

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

### 修复混合检索索引

```http
POST /api/knowledge/faq/index/repair
Content-Type: application/json; charset=utf-8
```

该接口从 MySQL 权威数据源重放 FAQ 到 Elasticsearch 和 pgvector，但不会清空现有索引。适合索引短暂不可用恢复后补写、文档数量不一致修复和指定 FAQ 重试。同步 `DISABLED` FAQ 时，Elasticsearch 会保留其非启用状态，pgvector 会删除对应向量。

请求体可省略或传空对象，此时修复全部 FAQ：

```json
{}
```

也可以指定最多 100 个 FAQ ID；ID 会去重，任一 ID 不存在时请求失败且不开始修复：

```json
{
  "faqIds": ["faq_refund_arrival", "faq_return_policy"]
}
```

响应结构与全量重建相同。`documentCount` 表示本次重放的 FAQ 数，`operations` 记录每个检索写入器的结果。`failureCount>0` 时其他写入器和文档仍会继续处理，可在依赖恢复后再次调用；接口具备幂等性。

坐席工作台 FAQ 管理页提供两种入口：工具栏“修复索引”重放全部 FAQ，表格行内“修复”只重放当前 FAQ。两种操作都会调用 DashScope 重新生成语义向量，执行前应确认 ES、pgvector 和 DashScope 可用；日常单条失败优先使用行内修复，避免无必要地重算全部向量。

### 重建混合检索索引

```http
POST /api/knowledge/faq/index/rebuild
```

接口从 MySQL `knowledge_faq` 读取全部 FAQ，先清空 ES/pgvector 检索副本，再逐条重建。MySQL 数据不会被修改。

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "enabled": true,
    "documentCount": 7,
    "successCount": 16,
    "failureCount": 0,
    "operations": [
      {
        "writer": "elasticsearch",
        "success": true,
        "message": "reset"
      }
    ]
  }
}
```

`enabled=false` 表示当前仍是 `keyword` 模式。`failureCount>0` 表示部分检索副本失败，应查看 `operations` 并在依赖恢复后优先执行非破坏性修复；只有索引结构需要重置时才执行全量重建。

### 查询混合索引状态

```http
GET /api/knowledge/faq/index/status
```

接口只读检查 MySQL 权威数据源以及当前检索模式需要的索引，不会触发重建或修改数据。

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "mode": "hybrid",
    "enabled": true,
    "healthy": true,
    "consistent": true,
    "source": {
      "name": "mysql",
      "available": true,
      "documentCount": 7,
      "message": "ready"
    },
    "indexes": [
      {
        "name": "elasticsearch",
        "available": true,
        "documentCount": 7,
        "message": "ready"
      },
      {
        "name": "pgvector",
        "available": true,
        "documentCount": 7,
        "message": "ready"
      }
    ],
    "checkedAt": "2026-07-13T06:00:00Z"
  }
}
```

状态含义：

- `enabled=false`：当前为 `keyword` 模式，只检查 MySQL，不访问 ES 或 pgvector。
- `healthy=true`：当前模式所需的数据源均可访问，且混合模式下两个索引文档数与 MySQL ACTIVE FAQ 数量一致。
- `consistent=false`：至少一个检索索引不可用、探针未加载，或文档数与 MySQL 不一致；应先查看各项 `message`，必要时执行全量重建。
- 状态响应不会返回连接串、用户名、密码或 DashScope API Key。

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
