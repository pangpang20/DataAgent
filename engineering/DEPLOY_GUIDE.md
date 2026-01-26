# DataAgent 编译与安装指南

本指南说明如何使用新的编译和安装脚本部署 DataAgent 系统。

## 概述

部署流程分为两个独立阶段：

1. **编译阶段**：在开发机器上编译前后端代码，生成 `output` 目录
2. **安装阶段**：在目标服务器上从 `output` 目录安装部署

这种分离模式的优点：
- ✅ 开发与生产环境分离
- ✅ 支持离线部署
- ✅ 可重复部署到多台服务器
- ✅ 版本管理更清晰

---

## 一、编译阶段

### 1.1 环境要求

编译环境需要安装：
- Java 17 或更高版本
- Node.js 16 或更高版本
- Yarn 包管理器
- Maven（项目自带 Maven Wrapper）

### 1.2 执行编译

在项目根目录执行：

```bash
# 默认输出到 ./output 目录
./build-dataagent.sh

# 指定输出目录
./build-dataagent.sh /path/to/custom/output
```

### 1.3 编译产物

编译完成后，`output` 目录结构如下：

```
output/
├── backend/
│   ├── dataagent-backend.jar        # 后端 JAR 包
│   └── dataagent-backend.jar.sha256 # 校验和
├── frontend/
│   ├── index.html                   # 前端入口
│   └── assets/                      # 前端资源
├── config/
│   ├── application.yml.template     # 后端配置模板
│   ├── nginx-dataagent.conf.template # Nginx 配置模板
│   └── sql/                         # 数据库初始化脚本
│       ├── mysql/
│       │   └── schema.sql
│       └── dameng/
│           └── schema.sql
├── VERSION.txt                       # 版本信息
└── INSTALL.txt                       # 安装说明
```

### 1.4 打包传输

将 `output` 目录打包并传输到目标服务器：

```bash
# 打包
tar -czf dataagent-output.tar.gz output/

# 传输到目标服务器
scp dataagent-output.tar.gz user@server:/path/to/destination/

# 在目标服务器解压
ssh user@server
cd /path/to/destination
tar -xzf dataagent-output.tar.gz
```

---

## 二、安装阶段（Linux）

### 2.1 支持的操作系统

- CentOS 7/8
- Kylin V10
- Ubuntu/Debian（实验性支持）

### 2.2 环境要求

目标服务器需要：
- Java 17 或更高版本（脚本可自动安装）
- Nginx（脚本可自动安装）
- 数据库：MySQL 或达梦数据库

### 2.3 基本安装

最简单的安装方式（自动安装依赖，使用默认配置）：

```bash
./install-dataagent.sh --output-dir ./output
```

### 2.4 自定义安装

指定数据库和部署目录：

```bash
./install-dataagent.sh \
  --output-dir ./output \
  --deploy-dir /opt/myapp \
  --db-type mysql \
  --db-host 192.168.1.100 \
  --db-port 3306 \
  --db-name dataagent \
  --db-user admin \
  --db-password 'YourPassword123' \
  --backend-port 8065 \
  --frontend-port 80
```

### 2.5 使用达梦数据库

```bash
./install-dataagent.sh \
  --output-dir ./output \
  --db-type dameng \
  --db-host 192.168.1.200 \
  --db-port 5236 \
  --db-user SYSDBA \
  --db-password 'SYSDBA'
```

### 2.6 配置向量库（Milvus）

```bash
./install-dataagent.sh \
  --output-dir ./output \
  --vector-store milvus \
  --db-host 192.168.1.100
```

> **注意**：需要先启动 Milvus 服务，参考 `docker-file/config/milvus-docker-compose.yml`

### 2.7 跳过依赖安装

如果已手动安装依赖，可跳过自动安装：

```bash
./install-dataagent.sh \
  --output-dir ./output \
  --skip-deps
```

### 2.8 完整参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--output-dir` | 编译输出目录（必需） | - |
| `--deploy-dir` | 部署目录 | `/opt/dataagent` |
| `--db-type` | 数据库类型：`mysql` 或 `dameng` | `mysql` |
| `--db-host` | 数据库主机 | `127.0.0.1` |
| `--db-port` | 数据库端口 | MySQL: `3306`<br>达梦: `5236` |
| `--db-name` | 数据库名称 | `data_agent` |
| `--db-user` | 数据库用户 | MySQL: `root`<br>达梦: `SYSDBA` |
| `--db-password` | 数据库密码 | - |
| `--backend-port` | 后端端口 | `8065` |
| `--frontend-port` | 前端端口 | `80` |
| `--vector-store` | 向量库类型：`simple` 或 `milvus` | `simple` |
| `--skip-deps` | 跳过依赖安装 | `false` |
| `--help` | 显示帮助信息 | - |

---

## 三、安装阶段（Windows）

### 3.1 环境要求

Windows 服务器需要：
- Java 17 或更高版本（需手动安装）
- 数据库：MySQL 或达梦数据库
- PowerShell 5.1 或更高版本

### 3.2 基本安装

在 PowerShell 中执行：

```powershell
.\install-dataagent.ps1 -OutputDir .\output
```

### 3.3 自定义安装

指定数据库配置：

```powershell
.\install-dataagent.ps1 `
  -OutputDir .\output `
  -DeployDir "C:\DataAgent" `
  -DbType mysql `
  -DbHost 192.168.1.100 `
  -DbPort 3306 `
  -DbName dataagent `
  -DbUser admin `
  -DbPassword "YourPassword123" `
  -BackendPort 8065 `
  -FrontendPort 8080
```

### 3.4 使用达梦数据库

```powershell
.\install-dataagent.ps1 `
  -OutputDir .\output `
  -DbType dameng `
  -DbHost 192.168.1.200 `
  -DbPort 5236 `
  -DbUser SYSDBA `
  -DbPassword "SYSDBA"
```

### 3.5 启动前端服务

Windows 安装脚本不会自动启动前端服务，需要手动启动：

**方式一：使用 Python**
```powershell
cd C:\DataAgent\frontend
python -m http.server 8080
```

**方式二：使用 Node.js**
```powershell
cd C:\DataAgent\frontend
npx http-server . -p 8080
```

**方式三：使用 Nginx for Windows**（高级用户）

下载 [Nginx for Windows](http://nginx.org/en/download.html) 并配置。

### 3.6 完整参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-OutputDir` | 编译输出目录（必需） | - |
| `-DeployDir` | 部署目录 | `C:\DataAgent` |
| `-DbType` | 数据库类型：`mysql` 或 `dameng` | `mysql` |
| `-DbHost` | 数据库主机 | `127.0.0.1` |
| `-DbPort` | 数据库端口 | MySQL: `3306`<br>达梦: `5236` |
| `-DbName` | 数据库名称 | `data_agent` |
| `-DbUser` | 数据库用户 | MySQL: `root`<br>达梦: `SYSDBA` |
| `-DbPassword` | 数据库密码 | - |
| `-BackendPort` | 后端端口 | `8065` |
| `-FrontendPort` | 前端端口 | `8080` |
| `-VectorStore` | 向量库类型：`simple` 或 `milvus` | `simple` |
| `-SkipDeps` | 跳过依赖检查 | `false` |
| `-Help` | 显示帮助信息 | - |

---

## 四、数据库初始化

首次部署需要手动初始化数据库。

### 4.1 MySQL 初始化

```bash
# 创建数据库
mysql -h<DB_HOST> -P<DB_PORT> -u<DB_USER> -p<DB_PASSWORD> -e "CREATE DATABASE data_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行初始化脚本
mysql -h<DB_HOST> -P<DB_PORT> -u<DB_USER> -p<DB_PASSWORD> data_agent < output/config/sql/mysql/schema.sql
```

### 4.2 达梦数据库初始化

```bash
# 连接达梦数据库
disql <DB_USER>/"<DB_PASSWORD>"@<DB_HOST>:<DB_PORT>

# 执行初始化脚本
SQL> start output/config/sql/dameng/schema.sql
```

---

## 五、服务管理

### 5.1 Linux 服务管理

**查看后端日志**
```bash
tail -f /opt/dataagent/logs/application.log
```

**停止后端服务**
```bash
kill $(cat /opt/dataagent/backend.pid)
```

**重启后端服务**
```bash
# 停止
kill $(cat /opt/dataagent/backend.pid)

# 启动
cd /opt/dataagent
nohup java -Xmx4g -Xms2g -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar dataagent-backend.jar > /dev/null 2>&1 &
echo $! > backend.pid
```

**重启 Nginx**
```bash
sudo systemctl restart nginx
```

### 5.2 Windows 服务管理

**查看后端日志**
```powershell
Get-Content "C:\DataAgent\logs\application.log" -Tail 50 -Wait
```

**停止后端服务**
```powershell
Stop-Process -Id (Get-Content "C:\DataAgent\backend.pid")
```

**重启后端服务**
```powershell
# 停止
Stop-Process -Id (Get-Content "C:\DataAgent\backend.pid")

# 启动
cd C:\DataAgent
Start-Process java -ArgumentList "-Xmx4g", "-Xms2g", "-XX:+UseG1GC", "-Dfile.encoding=UTF-8", "-jar", "dataagent-backend.jar" -WindowStyle Hidden
```

---

## 六、验证部署

### 6.1 检查后端服务

```bash
# 健康检查
curl http://localhost:8065/actuator/health

# API 测试
curl http://localhost:8065/api/agent/list
```

预期响应：
```json
{
  "status": "UP"
}
```

### 6.2 检查前端服务

在浏览器访问：
- Linux: `http://<server_ip>:80`
- Windows: `http://<server_ip>:8080`

### 6.3 检查数据库连接

查看后端日志，确认数据库连接成功：

```bash
grep -i "database" /opt/dataagent/logs/application.log
```

---

## 七、故障排查

### 7.1 后端启动失败

**检查日志**
```bash
tail -100 /opt/dataagent/logs/application.log
```

**常见问题**
- Java 版本过低：升级到 Java 17+
- 端口被占用：修改 `--backend-port` 参数
- 数据库连接失败：检查数据库配置和网络连通性

### 7.2 前端无法访问

**检查 Nginx 状态**（Linux）
```bash
sudo systemctl status nginx
sudo nginx -t  # 测试配置文件
```

**检查防火墙**
```bash
# CentOS/RHEL/Kylin
sudo firewall-cmd --list-ports

# Ubuntu
sudo ufw status
```

### 7.3 数据库连接失败

**测试连接**

MySQL:
```bash
mysql -h<DB_HOST> -P<DB_PORT> -u<DB_USER> -p<DB_PASSWORD> -e "SELECT 1;"
```

达梦:
```bash
echo "SELECT 1 FROM DUAL;" | disql <DB_USER>/"<DB_PASSWORD>"@<DB_HOST>:<DB_PORT>
```

### 7.4 Milvus 向量库连接失败

**检查 Milvus 服务**
```bash
# 如果使用 Docker 部署
cd docker-file
docker-compose -f config/milvus-docker-compose.yml ps
```

**测试连接**
```bash
curl http://<MILVUS_HOST>:19121/healthz
```

---

## 八、升级部署

### 8.1 升级流程

1. 编译新版本
2. 备份当前部署（脚本自动完成）
3. 停止服务
4. 执行安装脚本
5. 验证服务

### 8.2 回滚

如果升级失败，可回滚到备份版本：

```bash
# 查看备份
ls -la /opt/dataagent/backup/

# 回滚
BACKUP_DIR=/opt/dataagent/backup/20240126_143000
cp $BACKUP_DIR/dataagent-backend.jar /opt/dataagent/
cp $BACKUP_DIR/application.yml /opt/dataagent/

# 重启服务
kill $(cat /opt/dataagent/backend.pid)
cd /opt/dataagent
nohup java -Xmx4g -Xms2g -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar dataagent-backend.jar > /dev/null 2>&1 &
echo $! > backend.pid
```

---

## 九、多服务器部署

编译一次，部署多次：

```bash
# 1. 编译
./build-dataagent.sh

# 2. 打包
tar -czf dataagent-v1.0.0.tar.gz output/

# 3. 部署到服务器 A
scp dataagent-v1.0.0.tar.gz userA@serverA:/tmp/
ssh userA@serverA
cd /tmp
tar -xzf dataagent-v1.0.0.tar.gz
./install-dataagent.sh --output-dir ./output --db-host 192.168.1.100

# 4. 部署到服务器 B
scp dataagent-v1.0.0.tar.gz userB@serverB:/tmp/
ssh userB@serverB
cd /tmp
tar -xzf dataagent-v1.0.0.tar.gz
./install-dataagent.sh --output-dir ./output --db-host 192.168.1.100
```

---

## 十、总结

| 阶段 | 脚本 | 执行位置 | 作用 |
|------|------|----------|------|
| 编译 | `build-dataagent.sh` | 开发机器 | 编译前后端，生成 output 目录 |
| 安装（Linux） | `install-dataagent.sh` | 目标服务器 | 从 output 部署到 Linux |
| 安装（Windows） | `install-dataagent.ps1` | 目标服务器 | 从 output 部署到 Windows |

**关键优势**
- ✅ 编译与部署分离
- ✅ 支持离线部署
- ✅ 配置灵活（命令行参数）
- ✅ 自动安装依赖
- ✅ 自动备份旧版本
- ✅ 跨平台支持（Linux + Windows）

---

## 附录：配置文件位置

| 文件 | Linux 路径 | Windows 路径 |
|------|-----------|--------------|
| 后端 JAR | `/opt/dataagent/dataagent-backend.jar` | `C:\DataAgent\dataagent-backend.jar` |
| 后端配置 | `/opt/dataagent/application.yml` | `C:\DataAgent\application.yml` |
| 前端文件 | `/var/www/dataagent/` | `C:\DataAgent\frontend\` |
| Nginx 配置 | `/etc/nginx/conf.d/dataagent.conf` | N/A |
| 后端日志 | `/opt/dataagent/logs/application.log` | `C:\DataAgent\logs\application.log` |
| 备份目录 | `/opt/dataagent/backup/` | `C:\DataAgent\backup\` |

---

## 支持与反馈

如遇问题，请查看：
- 项目文档：`docs/` 目录
- 快速开始：`docs/QUICK_START.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 开发指南：`docs/DEVELOPER_GUIDE.md`
