# PostgreSQL + pgvector

该目录保存 Knowledge 语义检索所需的可重建索引结构。MySQL `knowledge_faq` 仍是 FAQ 权威数据源，PostgreSQL 表不与 MySQL 建立外键。

在 `smartcs_knowledge` 数据库中执行：

```bash
psql \
  --host "$SMARTCS_VECTOR_DB_HOST" \
  --port "${SMARTCS_VECTOR_DB_PORT:-5432}" \
  --username "$SMARTCS_VECTOR_DB_USERNAME" \
  --dbname smartcs_knowledge \
  --file 01-knowledge-vector-schema.sql
```

密码通过 `PGPASSWORD` 或交互提示提供，不写入脚本。

验证：

```sql
SELECT extversion FROM pg_extension WHERE extname = 'vector';
SELECT indexname FROM pg_indexes WHERE tablename = 'knowledge_faq_embedding';
```

向量模型固定为 DashScope `text-embedding-v4`，维度固定为 1024。更换模型或维度时必须重建全部向量数据和 HNSW 索引。
