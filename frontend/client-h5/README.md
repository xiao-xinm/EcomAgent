# frontend/client-h5

用户端 H5 聊天组件目录。

当前目录已作为独立前端工程入口，包含 Vue 3 H5 聊天页面、短轮询消息同步、可选 SSE、快捷动作和地址确认表单。

## 身份配置

Phase 8 兼容期内仍保留开发态用户配置。API client 会自动附加：

```http
X-SmartCS-User-Id: <VITE_USER_ID>
X-SmartCS-Roles: <VITE_USER_ROLES>
Authorization: Bearer <VITE_AUTH_TOKEN>
```

`VITE_AUTH_TOKEN` 为空时不会发送 `Authorization`。进入真实登录后，推荐由宿主页面或 APP WebView 注入：

```ts
window.__SMARTCS_AUTH__ = {
  getAccessToken: () => 'current-access-token',
  refreshAccessToken: async () => 'new-access-token',
  redirectToLogin: ({ reason, currentUrl }) => {
    window.location.href = `/login?redirect=${encodeURIComponent(currentUrl)}&reason=${reason}`
  },
}
```

H5 请求前会动态读取最新 Token。遇到 `401` / `1002` 时，如果宿主提供 `refreshAccessToken`，会刷新并重试一次；仍失败时再按 `VITE_LOGIN_URL` 跳转登录页。遇到 `403` / `1003` 时不会刷新，直接提示无权访问并触发登录兜底。

常用环境变量：

- `VITE_AUTH_TOKEN_STORAGE_KEY`：Token 的 sessionStorage 缓存 key。
- `VITE_AUTH_REQUIRED`：严格鉴权环境设为 `true`。
- `VITE_LOGIN_URL`：登录页地址，支持相对路径。
- `VITE_LOGIN_REDIRECT_ENABLED`：是否在鉴权失败时自动跳转，默认 `true`。
- `VITE_CHAT_SSE_AUTH_MODE`：`none` 或 `cookie`。

SSE 使用浏览器 `EventSource`，不能附加自定义 Header。`VITE_AUTH_REQUIRED=true` 且 `VITE_CHAT_SSE_AUTH_MODE` 不是 `cookie` 时，H5 会自动回落到短轮询，避免严格鉴权下 SSE 请求被网关拒绝。

鉴权失败时，H5 会把 `401` / `1002` 显示为登录过期，把 `403` / `1003` 显示为无权访问当前会话。
