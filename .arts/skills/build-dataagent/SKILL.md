---
name: build-dataagent
description: 编译打包 DataAgent 项目，支持前后端编译、打包输出和 Linux 部署。当用户要求编译 DataAgent、打包项目、部署服务或执行 build-dataagent.sh 时使用。
---

# DataAgent 编译部署

执行 `engineering/build-dataagent.sh` 脚本进行项目编译和部署。

## 前置要求

- Java 17+
- Node.js 16+
- Yarn
- Bash 环境（Linux/WSL/Git Bash）

## 仅编译打包

生成 `output` 目录，包含后端 JAR 和前端静态文件：

```bash
cd /path/to/DataAgent/engineering
./build-dataagent.sh [output_dir]
```

默认输出到 `./output`，包含：
- `backend/dataagent-backend.jar` - 后端 JAR
- `frontend/` - 前端静态文件
- `config/` - 配置模板和 SQL 脚本

## 编译并部署

编译后自动部署到 Linux 服务器：

```bash
./build-dataagent.sh --deploy \
  --deploy-dir /opt/dataagent \
  --db-type mysql \
  --db-host 127.0.0.1 \
  --db-port 3306 \
  --db-name data_agent \
  --db-user root \
  --db-password your_password
```

## 仅部署（使用已有打包）

```bash
./build-dataagent.sh --deploy \
  --package-dir ./output \
  --deploy-dir /opt/dataagent \
  --db-type mysql \
  --db-user root \
  --db-password your_password
```

## 常用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--deploy` | 启用部署模式 | - |
| `--deploy-dir` | 部署目录 | /opt/dataagent |
| `--package-dir` | 打包文件目录 | ./output |
| `--db-type` | 数据库类型 mysql/dameng | mysql |
| `--db-host` | 数据库主机 | 127.0.0.1 |
| `--db-port` | 数据库端口 | 3306/5236 |
| `--db-name` | 数据库名称 | data_agent |
| `--db-user` | 数据库用户 | root |
| `--db-password` | 数据库密码 | - |
| `--backend-port` | 后端端口 | 8065 |
| `--frontend-port` | 前端端口 | 80 |
| `--vector-store` | 向量库类型 simple/milvus | simple |

## Milvus 向量库参数

```bash
--vector-store milvus \
--milvus-host 127.0.0.1 \
--milvus-port 19530 \
--milvus-username root \
--milvus-password Milvus \
--milvus-database default \
--milvus-collection data_agent_vector
```

## 部署后管理

```bash
# 查看服务状态
sudo systemctl status dataagent

# 查看实时日志
sudo journalctl -u dataagent -f

# 重启服务
sudo systemctl restart dataagent

# 停止服务
sudo systemctl stop dataagent
```

## Windows 开发环境

Windows 下需使用 Git Bash 或 WSL 执行：

```bash
# Git Bash
cd /c/data/code/DataAgent/engineering
./build-dataagent.sh

# WSL
cd /mnt/c/data/code/DataAgent/engineering
./build-dataagent.sh
```
