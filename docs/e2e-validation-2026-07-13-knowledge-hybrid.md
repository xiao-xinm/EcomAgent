# Knowledge 混合检索联调记录（2026-07-13）

## 1. 验收范围

- MySQL `knowledge_faq` 权威数据源。
- Elasticsearch 8.15.0 + `analysis-smartcn` 关键词召回。
- PostgreSQL 16 + pgvector 0.8.2 语义召回。
- DashScope `text-embedding-v4`，1024 维向量。
- Knowledge 服务应用层 RRF 融合和 MySQL 兜底。

## 2. 环境

- Knowledge：`http://localhost:8084`
- 检索模式：`SMARTCS_RETRIEVAL_MODE=hybrid`
- PostgreSQL、Elasticsearch：用户虚拟机 Docker 环境
- 密码和 API Key：仅通过 IDEA Run Configuration 环境变量注入，未写入项目文件

## 3. 发现与修复

首次重建显示 7 条 FAQ、16 个索引操作全部成功，但查询仍无候选。排查发现 MySQL 中已有 FAQ 中文内容实际为 `????`，导致损坏内容被同步到两个检索索引。

处理方式：

1. 在 `infra/sql/09-knowledge-faq-management.sql` 中增加 `SET NAMES utf8mb4`。
2. Windows PowerShell 5.1 执行脚本前设置 `$OutputEncoding` 为无 BOM UTF-8。
3. 重新执行幂等 FAQ 脚本，确认 MySQL 返回正常中文。
4. 重新调用全量索引重建并执行冒烟脚本。

## 4. 验收结果

执行：

```powershell
.\scripts\smoke-knowledge-hybrid.ps1
```

结果：

| 检查项 | 结果 |
|--------|------|
| Knowledge 健康状态 | `UP` |
| 重建 FAQ 文档数 | `7` |
| 成功索引操作数 | `16` |
| 索引失败数 | `0` |
| 精确问句来源 | `hybrid-rrf-v1` |
| 精确问句置信度 | `0.9` |
| 语义改写来源 | `hybrid-rrf-v1` |
| 语义改写置信度 | `0.9` |

## 5. 结论

Phase 5 的 MySQL -> Elasticsearch / pgvector 重建、双路召回、RRF 融合和真实中间件冒烟链路已通过。当前不需要 Redis、RocketMQ、Milvus 或独立 Rerank 模型。
