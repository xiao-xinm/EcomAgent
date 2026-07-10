# frontend/workstation

坐席工作台前端目录。

当前已具备坐席工作台最小闭环：

- 工单列表、筛选和统计。
- 工单详情、审批、领取、内部备注。
- 人工接管开始、发送人工消息、结束接管。
- 操作日志和用户可见消息回写。

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
