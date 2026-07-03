# frontend/app-h5

APP 内嵌 H5 客服入口。

当前实现为一个轻量壳应用，复用 `frontend/client-h5/src` 中的聊天视图、状态管理、短轮询、快捷动作和修改地址确认表单。

## 启动

```powershell
cd D:\NewProject\EcomAgent\frontend
npm run dev:app-h5
```

默认端口：`http://localhost:3002`

## 说明

- API 仍走 Gateway：`VITE_API_BASE_URL`，默认与用户端 H5 保持一致。
- 渠道默认是 `app-h5`，通过 `VITE_CHANNEL` 或 URL 参数 `channel` 配置。
- 用户身份默认使用 `VITE_USER_ID`，APP 容器可以通过 URL 参数 `userId` / `uid` 注入。
- API 地址可以通过 URL 参数 `apiBaseUrl` 覆盖，便于 APP 容器按环境下发网关地址。
- 会话本地缓存 key 默认按 `channel + userId` 生成，避免和普通 H5 入口混用会话；也可以用 `VITE_SESSION_STORAGE_KEY` 或 URL 参数 `sessionKey` 覆盖。
- 当前阶段不接 APP 原生 Bridge，不做登录态鉴权；后续接 APP 容器时，可在 `window.__SMARTCS_CHAT_CONFIG__` 中注入同名运行时配置。
