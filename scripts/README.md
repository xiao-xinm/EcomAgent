# scripts

开发、检查、部署脚本目录。

## smoke-e2e.ps1

`smoke-e2e.ps1` 用于本地最小闭环烟测。默认假设你已经用 IDEA 启动：

- Gateway：`8080`
- Agent Core：`8081`
- Skill Engine：`8082`
- Workbench：`8083`
- Knowledge：`8084`
- Notification：`8085`

执行：

```powershell
cd D:\NewProject\EcomAgent
.\scripts\smoke-e2e.ps1
```

覆盖入口级检查：

- FAQ 自动回复
- 查订单
- 查物流
- 取消订单确认
- 退款进入人工审核
- 人工客服进入人工接管

脚本会临时插入一条取消订单测试数据，验证后删除。若当前环境没有 `mysql` 命令，可跳过取消订单的数据准备：

```powershell
.\scripts\smoke-e2e.ps1 -SkipOrderCancelDbSetup
```
