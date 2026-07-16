# infra/docker

本地开发 Docker Compose 配置。

当前最小闭环只需要 MySQL，因此 `compose.yml` 只启动：

- `mysql`：MySQL 8.x，默认端口 `3306`，默认库名 `smartcs_agent`，账号密码 `root/root`。

首次创建数据卷时，Compose 会按文件名顺序执行 `../sql/*.sql`，完成表结构和基础种子数据初始化。

## 启动

```powershell
cd D:\NewProject\EcomAgent\infra\docker
docker compose up -d
```

如需调整端口或密码：

```powershell
Copy-Item .env.example .env
# 修改 .env 后再启动
docker compose up -d
```

## 常用命令

```powershell
docker compose ps
docker compose logs -f mysql
docker compose down
```

如需重新执行初始化 SQL，需要删除数据卷：

```powershell
docker compose down -v
docker compose up -d
```

项目内 Compose 当前不启用 Redis、RocketMQ、Elasticsearch 或 pgvector。Phase 5 混合检索使用外部 PostgreSQL + pgvector 与 Elasticsearch；连接配置见 `docs/production-deployment-config.md`，不要把真实密码写入本目录的示例文件。
