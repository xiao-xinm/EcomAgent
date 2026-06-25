# backend

后端聚合工程目录，使用 Maven 多模块管理。

模块划分：

- `smartcs-gateway`：接入网关、会话管理、消息标准化
- `smartcs-agent-core`：Agent 核心编排、对话管理、风控路由
- `smartcs-skill-engine`：技能注册、技能路由、声明式 API 编排
- `smartcs-workbench`：坐席工作台后端、人工审核、人工接管
- `smartcs-knowledge`：知识库与 RAG 检索增强
- `smartcs-notification`：通知分发
- `smartcs-common`：公共契约、DTO、枚举、错误码和工具

当前只保留工程骨架，不包含具体业务实现。

## 常用命令

```bash
mvn clean install
mvn -pl smartcs-gateway spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl smartcs-agent-core spring-boot:run -Dspring-boot.run.profiles=dev
```

本地健康检查：

- Gateway: `GET http://localhost:8080/api/health`
- Agent Core: `GET http://localhost:8081/api/health`
- Skill Engine: `GET http://localhost:8082/api/health`
- Workbench: `GET http://localhost:8083/api/health`
- Knowledge: `GET http://localhost:8084/api/health`
