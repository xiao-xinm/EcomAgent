# 运行时就绪检查验收记录（2026-07-16）

## 验收范围

- Knowledge 关键词/混合检索依赖状态。
- Workbench Outbox 与 Notification Delivery 运行配置组合。
- 通知积压、重试耗尽和异步切换状态。

## 离线回归

执行：

```powershell
.\scripts\test-runtime-readiness.ps1
```

结果：6/6 通过。

- `KEYWORD_READY + DIRECT`：通过。
- `HYBRID_READY + ASYNC_ACTIVE`：通过。
- `HYBRID_INCONSISTENT`：按预期返回退出码 `1`。
- `ASYNC_CONFIG_INVALID`：按预期返回退出码 `1`。
- `RETRY_EXHAUSTED`：按预期返回退出码 `1`。
- `ASYNC_BACKLOG`：通过并提示继续观察积压。

## 真实服务验收

临时启动 Workbench `8083`、Knowledge `8084` 和 Notification `8085`，Knowledge 复用既有混合检索环境，通知保持默认同步直写模式。

执行：

```powershell
.\scripts\check-runtime-readiness.ps1
```

结果：

```text
[ok] knowledge      HYBRID_READY  source=7, elasticsearch=7, pgvector=7
[ok] notification   DIRECT        Workbench transactionally writes user messages
[readiness] runtime dependencies are ready
```

## 清理结果

- 临时 Workbench、Knowledge、Notification 进程已停止。
- `8083`、`8084`、`8085` 端口已释放。
- 临时日志已删除。
- 检查过程只读，未创建工单、FAQ 或通知事件。
