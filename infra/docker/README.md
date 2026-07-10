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

当前阶段不启用 Redis、RocketMQ、Milvus、Elasticsearch。后续如果业务进入实时广播、异步通知或 RAG 检索，再单独评估并扩展 Compose。
