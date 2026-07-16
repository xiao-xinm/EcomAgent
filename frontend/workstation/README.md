# frontend/workstation

坐席工作台前端目录。

当前已具备坐席工作台最小闭环：

- 工单列表、筛选和统计。
- 工单详情、审批、领取、内部备注。
- 人工接管开始、发送人工消息、结束接管。
- 操作日志和用户可见消息回写。
- FAQ 管理：列表查询、新增、编辑、启用、停用和非破坏性索引修复。

## 知识库配置

FAQ 管理页通过 Knowledge 服务访问 `docs/knowledge-api.md` 中的 FAQ 管理接口。

工具栏“修复索引”会把全部 MySQL FAQ 重新同步到 Elasticsearch 和 pgvector；表格行内“修复”只处理当前 FAQ。两者都不会清空索引；混合检索未启用时页面会提示无需修复。

常用环境变量：

- `VITE_WORKSTATION_KNOWLEDGE_API_BASE_URL`：Knowledge 服务地址，默认 `http://localhost:8084`。

## 通知事件配置

通知事件页通过 Notification 服务查询投递状态、重试次数和失败原因，只读展示，不会修改投递状态。页面顶部同时展示：

- Workbench Outbox 摘要：待处理、当前到期、失败待重试、重试耗尽、租约中、已发送和最老积压时间。
- Notification Delivery 摘要：已接收、当前到期、失败待重试、重试耗尽、租约中、已投递和最老积压时间。

两个摘要独立加载；其中一个服务暂时不可用时，另一个摘要和下方事件列表仍可继续使用。

页面会结合消息直写、outbox、Notification、retry worker 和 `USER_SESSION` 通道状态展示切换结论：同步直写中、可切换异步、灰度/异步存在积压、异步交付中或异步配置异常。该结论只读，不会自动修改后端开关。

常用环境变量：

- `VITE_WORKSTATION_NOTIFICATION_API_BASE_URL`：Notification 服务地址，默认 `http://localhost:8085`。

本地跨域访问默认允许 `http://localhost:3001` 和 `http://127.0.0.1:3001`。部署到其他域名时，通过 Notification 服务的 `SMARTCS_CORS_ALLOWED_ORIGINS` 配置允许来源。

## 身份配置

Phase 8 兼容期内，API client 会自动附加开发坐席身份头，并在执行操作前调用 `GET /api/workbench/me` 获取当前坐席：

```http
X-SmartCS-Operator-Id: <VITE_WORKSTATION_OPERATOR_ID>
X-SmartCS-Roles: <VITE_WORKSTATION_ROLES>
Authorization: Bearer <VITE_WORKSTATION_AUTH_TOKEN>
```

`VITE_WORKSTATION_AUTH_TOKEN` 为空时不会发送 `Authorization`。接入真实登录系统时，推荐由统一登录壳或后台门户注入：

```ts
window.__SMARTCS_WORKSTATION_AUTH__ = {
  getAccessToken: () => "current-access-token",
  refreshAccessToken: async () => "new-access-token",
  redirectToLogin: ({ reason, currentUrl }) => {
    window.location.href = `/login?redirect=${encodeURIComponent(currentUrl)}&reason=${reason}`;
  },
};
```

API 请求前会动态读取最新 Token。遇到 `401` / `1002` 时，如果宿主提供 `refreshAccessToken`，会刷新并重试一次；仍失败时根据 `VITE_WORKSTATION_LOGIN_URL` 跳转登录页。遇到 `403` / `1003` 时不会刷新，只提示无权访问并触发登录兜底。

常用环境变量：

- `VITE_WORKSTATION_AUTH_TOKEN_STORAGE_KEY`：Token 的 sessionStorage 缓存 key。
- `VITE_WORKSTATION_LOGIN_URL`：坐席登录页地址，支持相对路径。
- `VITE_WORKSTATION_LOGIN_REDIRECT_ENABLED`：是否在鉴权失败时自动跳转，默认 `true`。

鉴权失败时，坐席工作台会把 `401` / `1002` 显示为登录过期，把 `403` / `1003` 显示为当前坐席无权执行操作。
