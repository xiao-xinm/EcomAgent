# infra

基础设施配置目录。

当前仅保留目录占位，后续按环境补齐：

- `docker/`：本地开发 Docker Compose
- `sql/`：DDL、迁移脚本、初始化数据
- `postgres/`：Knowledge pgvector 索引结构
- `elasticsearch/`：Knowledge 中文关键词索引 mapping
- `rocketmq/`：Topic、Consumer Group、ACL 配置
- `k8s/`：Kubernetes manifests 或 Helm Chart

约束：

- SQL 只放结构和初始化配置，不写业务测试数据到主脚本
- 敏感配置通过环境变量或密钥系统注入
- 本地配置与生产配置分层维护
