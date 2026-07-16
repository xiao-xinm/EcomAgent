# Knowledge 索引修复验收记录（2026-07-16）

## 验收范围

- Knowledge 非破坏性索引修复接口。
- 坐席工作台 FAQ 管理页修复入口。
- Elasticsearch、pgvector、DashScope 真实混合检索环境。

## 自动化结果

- `mvn -pl smartcs-knowledge -am test`：通过；Common 6 项、Knowledge 50 项。
- `npm run lint --workspace @smartcs/workstation`：通过。
- `npm run typecheck --workspace @smartcs/workstation`：通过。
- `npm run test --workspace @smartcs/workstation`：通过，10 项。
- `npm run build --workspace @smartcs/workstation`：通过。
- `npm run test:e2e:workstation`：通过，7 条页面场景。

## 真实环境结果

环境沿用项目既有配置：MySQL、PostgreSQL 16 + pgvector 0.8.2、Elasticsearch 8.15.0 + `analysis-smartcn`、DashScope `text-embedding-v4`。

1. 空请求体调用 `POST /api/knowledge/faq/index/repair`：
   - `enabled=true`
   - `documentCount=7`
   - `successCount=14`
   - `failureCount=0`
2. 重复指定 `faq_refund_arrival` 调用修复接口：
   - 请求 ID 成功去重
   - `documentCount=1`
   - ES、pgvector 各完成一次写入
3. 修复后状态检查：
   - MySQL ACTIVE FAQ：7 条
   - Elasticsearch：7 条
   - pgvector：7 条
   - `healthy=true`
   - `consistent=true`
4. 查询“退款多久到账”：
   - `matched=true`
   - `source=hybrid-rrf-v1`
   - `confidence=0.9`

## 清理结果

- 临时 Knowledge 进程已停止。
- 本地 `8084` 端口已释放。
- 临时启动日志已删除。
- 验收未新增或删除 MySQL FAQ，未执行索引 reset。
