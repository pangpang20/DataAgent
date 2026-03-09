---
name: deploy-remote
description: 远程部署 DataAgent 到 Linux 服务器。SSH 连接到目标服务器执行部署脚本。当用户要求远程部署、部署到服务器、执行远程部署脚本时使用。
---

# 远程部署 DataAgent

通过 SSH 连接到 Linux 服务器，执行 DataAgent 部署脚本。

## 服务器信息

| 配置项     | 值                  |
| ---------- | ------------------- |
| 服务器     | 172.16.1.137        |
| 用户名     | root                |
| 密码       | Audaque@123         |
| 部署目录   | /data               |
| 部署脚本   | deploy_dataagent.sh |
| Java的位置 | /opt/java/bin/java  |

## 执行部署

在 PowerShell 中使用 SSH 连接并执行部署：

```powershell
ssh root@172.16.1.137 "cd /data ; sh deploy_dataagent.sh"
```

如果需要交互式输入密码，使用：

```powershell
ssh root@172.16.1.137
# 输入密码后执行：
cd /data
sh deploy_dataagent.sh
```

## 使用 sshpass 自动化（推荐）

如果安装了 sshpass（或在 WSL 中）：

```bash
sshpass -p 'Audaque@123' ssh -o StrictHostKeyChecking=no root@172.16.1.137 "cd /data ; sh deploy_dataagent.sh"
```

## 注意事项

1. 确保本地已安装 SSH 客户端
2. 首次连接需确认主机指纹
3. 部署过程会实时输出到终端
4. 如遇网络问题，检查防火墙和网络连通性

## 部署前检查

```powershell
# 测试网络连通性
ping 172.16.1.137

# 测试 SSH 连接
ssh root@172.16.1.137 "echo 'SSH连接成功'"
```
