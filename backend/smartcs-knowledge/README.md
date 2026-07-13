# smartcs-knowledge

知识库与 RAG 检索增强模块，负责 FAQ、政策、流程说明等非实时知识。

当前已具备：

- MySQL FAQ 管理、关键词兜底和人工接管降级。
- Elasticsearch BM25 与 pgvector 语义双路召回。
- DashScope Embedding 和应用层 RRF 融合。
- MySQL 到两个可重建检索索引的增量同步及全量重建。
- MySQL、Elasticsearch、pgvector 文档数量和一致性状态检查。

主要接口：

- `POST /api/knowledge/faq/query`
- `GET /api/knowledge/faq`
- `POST /api/knowledge/faq/index/rebuild`
- `GET /api/knowledge/faq/index/status`

详细契约见 `docs/knowledge-api.md`。

边界：

- 订单状态、退款状态、物流轨迹等实时数据不走 RAG
- 实时数据必须通过业务 API 查询
- RAG 结果只作为回答依据，不直接触发业务动作
