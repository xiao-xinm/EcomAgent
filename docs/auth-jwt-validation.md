# SmartCS JWT Validation Access Point

本文档对应 `docs/project-roadmap.md` 的 Phase 8，用于记录当前已经落地的生产 Token / JWT 校验接入点。

## Scope

本轮只完成无状态 Bearer JWT 解析入口，不引入 Redis Session，也不新增用户、坐席、角色、权限表。

覆盖模块：

- `smartcs-common`：公共 JWT 解析器和身份来源枚举。
- `smartcs-gateway`：用户端聊天入口支持 `Authorization: Bearer <token>`。
- `smartcs-workbench`：坐席操作入口支持 `Authorization: Bearer <token>`。

## Configuration

默认关闭 JWT 校验，保持当前本地开发链路不变。

```yaml
smartcs:
  auth:
    jwt:
      enabled: false
      secret:
      issuer:
      audience:
```

对应环境变量：

```bash
SMARTCS_AUTH_JWT_ENABLED=true
SMARTCS_AUTH_JWT_SECRET=0123456789abcdef0123456789abcdef
SMARTCS_AUTH_JWT_ISSUER=smartcs
SMARTCS_AUTH_JWT_AUDIENCE=gateway
```

说明：

- `secret` 使用 HMAC 签名密钥，长度至少 32 字节。
- `issuer` 和 `audience` 为空时不校验对应 claim。
- Gateway 和 Workbench 使用同一组配置项，后续如果需要拆分用户端/坐席端密钥，可以再单独增加配置。

## Supported Claims

```json
{
  "sub": "u1001",
  "principal_type": "CUSTOMER",
  "roles": ["CUSTOMER"],
  "permissions": [],
  "iss": "smartcs",
  "aud": "gateway"
}
```

字段说明：

- `sub`：必填，对应 SmartCS 的 `principalId`。
- `principal_type`：推荐填写，支持 `CUSTOMER`、`AGENT`、`SUPERVISOR`、`ADMIN`。
- `roles`：可为字符串数组或逗号分隔字符串。
- `permissions`：可为字符串数组或逗号分隔字符串。
- `iss` / `aud`：当配置了 `issuer` / `audience` 时必须匹配。

兼容字段：

- `principalType`
- `type`

## Resolution Order

Gateway 用户端身份解析顺序：

1. Bearer JWT
2. 标准身份头：`X-SmartCS-Principal-*`
3. 开发兼容头：`X-SmartCS-User-Id`
4. 旧请求体字段：`userId`

Workbench 坐席身份解析顺序：

1. Bearer JWT
2. 标准身份头：`X-SmartCS-Principal-*`
3. 开发兼容头：`X-SmartCS-Operator-Id`
4. 旧请求体字段：`operatorId`
5. 本地开发兜底：`agent_001`

注意：

- Workbench 的 Bearer JWT 必须解析为 `AGENT` / `SUPERVISOR` / `ADMIN`，不能用用户 Token 回退到请求体 `operatorId`。
- Gateway 的 Bearer JWT 只接受 `CUSTOMER`。
- JWT 默认关闭时，即使请求里带了 Bearer Token，也继续走原兼容链路。

## Error Semantics

启用 JWT 后：

- Token 签名无效、缺少 `sub`、issuer/audience 不匹配：返回 `1002 UNAUTHORIZED`。
- Workbench 使用用户 Token 调用坐席操作：返回 `1002 UNAUTHORIZED`。
- 坐席身份存在但无坐席角色：继续返回 `1003 FORBIDDEN`。

## Verification

已执行：

```bash
cd backend
mvn -pl smartcs-common,smartcs-gateway,smartcs-workbench -am test
```

覆盖测试：

- 公共 JWT 解析器可解析合法 HMAC JWT。
- 公共 JWT 解析器会拒绝 issuer 不匹配的 Token。
- JWT 关闭时不影响现有兼容链路。
- Gateway Bearer Token 优先于开发头和旧 `userId`。
- Workbench Bearer Token 优先于旧 `operatorId`。
- Workbench 用户 Token 不能回退成坐席身份。

## Middleware

本轮不需要新增中间件。

后续只有在切换 Redis Session、跨实例会话状态或分布式 Token 黑名单时，才需要提前评估并启动 Redis。
