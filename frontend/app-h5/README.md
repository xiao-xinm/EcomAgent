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
- 当前阶段不接 APP 原生 Bridge，不做登录态注入。
- 后续接入 APP 容器时，可在本应用中增加 Bridge 适配层，再向复用的聊天 Store 传入用户身份和渠道信息。
