# Elasticsearch FAQ index

该目录保存 Knowledge 关键词检索索引的可重建 mapping。运行前确认 Elasticsearch 已安装 `analysis-smartcn`。

```bash
docker exec elasticsearch bin/elasticsearch-plugin list
```

创建开发索引：

```bash
curl --fail-with-body \
  --user "${SMARTCS_ES_USERNAME}:${SMARTCS_ES_PASSWORD}" \
  --header "Content-Type: application/json" \
  --request PUT \
  --data-binary @knowledge-faq-index.json \
  "${SMARTCS_ES_URL}/smartcs_knowledge_faq_v1"
```

验证 mapping：

```bash
curl --fail-with-body \
  --user "${SMARTCS_ES_USERNAME}:${SMARTCS_ES_PASSWORD}" \
  "${SMARTCS_ES_URL}/smartcs_knowledge_faq_v1/_mapping?pretty"
```

用户名和密码只通过环境变量传入。该索引只保存 MySQL FAQ 的检索副本，出现数据漂移时以 MySQL 为准重建。
