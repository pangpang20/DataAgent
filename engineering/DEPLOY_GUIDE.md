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
- npm 或 Yarn 包管理器
- Maven（项目自带 Maven Wrapper）
- 至少 8GB 可用内存（推荐 16GB 以上）

> **注意**：
> 
> 1. 编译 DataAgent 项目需要较大内存，如果在编译过程中遇到 `Native memory allocation` 或 `insufficient memory` 错误，请增加系统虚拟内存或在更高配置的机器上编译。
> 2. 如果遇到 `MalformedInputException` 或字符编码相关错误，请确保系统区域设置为 UTF-8，或在运行编译脚本前设置 `MAVEN_OPTS` 环境变量：
>    ```bash
>    export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
>    ```

### 1.2 执行编译

在项目根目录执行：

#### Linux/Mac
```bash
# 默认输出到 ./output 目录
./build-dataagent.sh

# 指定输出目录
./build-dataagent.sh /path/to/custom/output
```

#### Windows（PowerShell）
```powershell
# 标准编译和打包模式（推荐）
.\build-dataagent.ps1

# 指定输出目录
.\build-dataagent.ps1 -OutputDir "C:\Output"

# 使用内存优化模式（低配置机器）
.\build-dataagent.ps1 -OutputDir "C:\Output" -MemoryOptimized

# 编码优化模式（默认启用）
.\build-dataagent.ps1 -OutputDir "C:\Output" -EncodingOptimized

# 显示帮助
.\build-dataagent.ps1 -Help
```

### 1.3 编译产物

编译完成后，`output` 目录结构如下：

```
output/
├── backend/
│   ├── dataagent-backend.jar        # 后端 JAR 包
│   └── dataagent-backend.jar.sha256 # SHA256 校验和
├── frontend/
│   ├── index.html                   # 前端入口
│   ├── assets/                      # 前端资源
│   └── ...                          # 其他前端文件
├── config/
│   ├── application.yml.template     # 后端配置模板
│   ├── nginx-dataagent.conf.template # Nginx 配置模板
│   └── sql/                         # 数据库初始化脚本
│       ├── mysql/
│       │   ├── schema.sql           # MySQL 初始化脚本
│       │   └── ...
│       └── dameng/
│           ├── schema.sql           # 达梦初始化脚本
│           └── ...
├── scripts/
│   ├── build-dataagent.ps1          # Windows 一体化脚本
│   ├── install-dataagent.sh         # Linux 安装脚本
│   └── ...
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

### 2.8 完整参数说明（Linux）

| 参数                  | 说明                             | 默认值                          |
| --------------------- | -------------------------------- | ------------------------------- |
| `--output-dir`        | 编译输出目录（必需）             | -                               |
| `--deploy-dir`        | 部署目录                         | `/opt/dataagent`                |
| `--db-type`           | 数据库类型：`mysql` 或 `dameng`  | `mysql`                         |
| `--db-host`           | 数据库主机                       | `127.0.0.1`                     |
| `--db-port`           | 数据库端口                       | MySQL: `3306`<br>达梦: `5236`   |
| `--db-name`           | 数据库名称                       | `data_agent`                    |
| `--db-user`           | 数据库用户                       | MySQL: `root`<br>达梦: `SYSDBA` |
| `--db-password`       | 数据库密码                       | -                               |
| `--backend-port`      | 后端端口                         | `8065`                          |
| `--frontend-port`     | 前端端口                         | `80`                            |
| `--vector-store`      | 向量库类型：`simple` 或 `milvus` | `simple`                        |
| `--milvus-host`       | Milvus 服务器地址                | `127.0.0.1`                     |
| `--milvus-port`       | Milvus 服务器端口                | `19530`                         |
| `--milvus-collection` | Milvus 集合名称                  | `data_agent`                    |
| `--skip-deps`         | 跳过依赖安装                     | `false`                         |
| `--help`              | 显示帮助信息                     | -                               |

---

## 三、Windows 端一体化部署方案

### 3.1 环境要求

Windows 服务器需要：
- Java 17 或更高版本（需手动安装）
- 数据库：MySQL 或达梦数据库
- PowerShell 5.1 或更高版本
- 至少 8GB 可用内存用于编译（如果在 Windows 上编译）

> **注意**：
>
> 1. 如果在 Windows 上进行编译，遇到内存不足错误，请增加系统虚拟内存或在更高配置的机器上编译。编译完成后，部署过程对内存要求较低。
> 2. 如果遇到 `MalformedInputException` 或字符编码相关错误，请在运行编译脚本前设置 `MAVEN_OPTS` 环境变量：
>    ```powershell
>    $env:MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
>    ```

### 3.2 使用 build-dataagent.ps1 一体化脚本

`build-dataagent.ps1` 支持三种模式：

#### 模式一：仅编译和打包
```powershell
.\build-dataagent.ps1 -OutputDir "C:\Output"
```
编译前后端，生成 output 目录，不进行部署。

#### 模式二：编译、打包并部署（推荐）
```powershell
.\build-dataagent.ps1 -OutputDir "C:\Output" `
  -Deploy `
  -DeployDir "C:\DataAgent" `
  -DbType mysql `
  -DbUser "root" `
  -DbPassword "password123"
```
完整的编译、打包和部署流程。

#### 模式三：仅部署（使用现有打包）
```powershell
.\build-dataagent.ps1 -Deploy `
  -DeployDir "C:\DataAgent" `
  -PackageDir "C:\Output" `
  -DbType mysql `
  -DbUser "root" `
  -DbPassword "password123"
```
使用已有的 output 目录直接部署。

### 3.3 自定义部署配置

指定所有数据库参数：

```powershell
.\build-dataagent.ps1 -OutputDir "C:\Output" -Deploy `
  -DeployDir "C:\DataAgent" `
  -DbType mysql `
  -DbHost 192.168.1.100 `
  -DbPort 3306 `
  -DbName dataagent `
  -DbUser admin `
  -DbPassword "YourPassword123" `
  -BackendPort 8065 `
  -FrontendPort 8080 `
  -VectorStore simple
```

### 3.4 使用达梦数据库

```powershell
.\build-dataagent.ps1 -OutputDir "C:\Output" -Deploy `
  -DeployDir "C:\DataAgent" `
  -DbType dameng `
  -DbHost 192.168.1.200 `
  -DbPort 5236 `
  -DbUser SYSDBA `
  -DbPassword "SYSDBA"
```

### 3.5 使用向量库（Milvus）

```powershell
.\build-dataagent.ps1 -OutputDir "C:\Output" -Deploy `
  -DeployDir "C:\DataAgent" `
  -VectorStore milvus `
  -MilvusHost 192.168.1.150
```

### 3.6 启动前端服务

部署完成后，前端服务需要手动启动：

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

### 3.7 完整参数说明

| 参数                 | 说明                             | 默认值                          |
| -------------------- | -------------------------------- | ------------------------------- |
| `-OutputDir`         | 编译输出目录                     | `./output`                      |
| `-Deploy`            | 启用部署模式                     | 仅编译                          |
| `-DeployDir`         | 部署目录                         | `C:\DataAgent`                  |
| `-PackageDir`        | 打包目录（部署模式下使用）       | 同 OutputDir                    |
| `-DbType`            | 数据库类型：`mysql` 或 `dameng`  | `mysql`                         |
| `-DbHost`            | 数据库主机                       | `127.0.0.1`                     |
| `-DbPort`            | 数据库端口                       | MySQL: `3306`<br>达梦: `5236`   |
| `-DbName`            | 数据库名称                       | `data_agent`                    |
| `-DbUser`            | 数据库用户                       | MySQL: `root`<br>达梦: `SYSDBA` |
| `-DbPassword`        | 数据库密码                       | -                               |
| `-BackendPort`       | 后端端口                         | `8065`                          |
| `-FrontendPort`      | 前端端口                         | `8080`                          |
| `-VectorStore`       | 向量库类型：`simple` 或 `milvus` | `simple`                        |
| `-MilvusHost`        | Milvus 服务器地址                | `127.0.0.1`                     |
| `-MilvusPort`        | Milvus 服务器端口                | `19530`                         |
| `-MilvusCollection`  | Milvus 集合名称                  | `data_agent`                    |
| `-MemoryOptimized`   | 内存优化模式（编译阶段）         | 禁用                            |
| `-EncodingOptimized` | 编码优化模式（编译阶段）         | 启用                            |
| `-SkipDeps`          | 跳过依赖检查                     | `false`                         |
| `-Help`              | 显示帮助信息                     | -                               |

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

### Windows 统一方案
| 场景      | 脚本                  | 执行位置  | 作用                                 |
| --------- | --------------------- | --------- | ------------------------------------ |
| 编译/部署 | `build-dataagent.ps1` | 开发/目标 | 编译、打包、部署的一体化脚本（推荐） |

### 跨平台方案
| 阶段            | 脚本                   | 执行位置   | 作用                         |
| --------------- | ---------------------- | ---------- | ---------------------------- |
| 编译（Linux）   | `build-dataagent.sh`   | 开发机器   | 编译前后端，生成 output 目录 |
| 安装（Linux）   | `install-dataagent.sh` | 目标服务器 | 从 output 部署到 Linux       |
| 编译（Windows） | `build-dataagent.ps1`  | 开发机器   | 编译前后端，生成 output 目录 |
| 部署（Windows） | `build-dataagent.ps1`  | 目标服务器 | 从 output 部署到 Windows     |

**关键优势**
- ✅ Windows 一体化：编译、打包、部署统一脚本
- ✅ 编译与部署分离：支持离线部署
- ✅ 配置灵活：命令行参数灵活配置
- ✅ 自动安装依赖：自动检查和配置环境
- ✅ 自动备份旧版本：升级时自动备份
- ✅ 跨平台支持：Linux + Windows 完整覆盖
- ✅ 多种优化模式：支持内存优化、编码优化

---

## 附录：工程目录说明

### engineering/ 目录文件

| 文件                      | 平台    | 用途                              |
| ------------------------- | ------- | --------------------------------- |
| **`build-dataagent.ps1`** | Windows | 一体化构建脚本：编译、打包、部署  |
| `build-dataagent.sh`      | Linux   | 编译脚本：编译前后端，生成 output |
| `install-dataagent.sh`    | Linux   | 安装脚本：从 output 部署到 Linux  |
| `DEPLOY_GUIDE.md`         | 通用    | 部署指南（本文件）                |
| `MEMORY_OPTIMIZATION.md`  | 通用    | 内存优化说明                      |

### 配置文件和日志位置

| 文件       | Linux 路径                             | Windows 路径                         |
| ---------- | -------------------------------------- | ------------------------------------ |
| 后端 JAR   | `/opt/dataagent/dataagent-backend.jar` | `C:\DataAgent\dataagent-backend.jar` |
| 后端配置   | `/opt/dataagent/application.yml`       | `C:\DataAgent\application.yml`       |
| 前端文件   | `/var/www/dataagent/`                  | `C:\DataAgent\frontend\`             |
| Nginx 配置 | `/etc/nginx/conf.d/dataagent.conf`     | N/A                                  |
| 后端日志   | `/opt/dataagent/logs/application.log`  | `C:\DataAgent\logs\application.log`  |
| 备份目录   | `/opt/dataagent/backup/`               | `C:\DataAgent\backup\`               |

---

## 快速参考

### Windows 一键部署
```powershell
# 完整流程：编译 → 打包 → 部署 → 启动
.\build-dataagent.ps1 -OutputDir "C:\Output" -Deploy -DeployDir "C:\DataAgent" `
  -DbType mysql -DbUser "root" -DbPassword "password123"
```

### Linux 两步部署
```bash
# 步骤 1：编译（开发机）
./build-dataagent.sh

# 步骤 2：部署（目标服务器）
./install-dataagent.sh --output-dir ./output --db-user admin --db-password 'password123'
```

## 支持与反馈

如遇问题，请查看：
- 项目文档：`docs/` 目录
- 快速开始：`docs/QUICK_START.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 开发指南：`docs/DEVELOPER_GUIDE.md`
- 记忆优化：`engineering/MEMORY_OPTIMIZATION.md`
