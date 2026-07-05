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

`VITE_AUTH_TOKEN` 为空时不会发送 `Authorization`。SSE 使用浏览器 `EventSource`，当前不能附加自定义 Header，因此强制鉴权前需要单独确认 Cookie 或 query token 策略。
