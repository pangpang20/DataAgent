# DataAgent 麒麟 V10 离线部署文档

## 文档概述

本文档提供 DataAgent 项目在**银河麒麟 V10（Kylin V10）**操作系统上的完整离线部署方案，使用**达梦数据库（DM8）**作为元数据存储。文档涵盖所有依赖软件的离线安装包准备、安装步骤、配置方法及常见问题排查。

**目标环境**：
- **操作系统**：银河麒麟高级服务器操作系统 V10（aarch64 或 x86_64）
- **元数据库**：达梦数据库 DM8
- **网络环境**：内网离线环境（无互联网连接）

**文档版本**：v1.0  
**创建日期**：2026-01-15  
**适用项目版本**：DataAgent 1.0.0-SNAPSHOT

---

## 目录

- [一、环境要求](#一环境要求)
- [二、离线安装包清单](#二离线安装包清单)
- [三、系统环境准备](#三系统环境准备)
- [四、达梦数据库安装](#四达梦数据库安装)
- [五、JDK 安装配置](#五jdk-安装配置)
- [六、Node.js 安装配置](#六nodejs-安装配置)
- [七、Maven 安装配置](#七maven-安装配置)
- [八、DataAgent 后端部署](#八dataagent-后端部署)
- [九、DataAgent 前端部署](#九dataagent-前端部署)
- [十、Nginx 安装配置](#十nginx-安装配置)
- [十一、系统启动与验证](#十一系统启动与验证)
- [十二、常见问题排查](#十二常见问题排查)
- [十三、系统运维](#十三系统运维)
- [十四、附录](#十四附录)

---

## 一、环境要求

### 1.1 硬件要求

| 组件         | 最低配置 | 推荐配置                   |
| ------------ | -------- | -------------------------- |
| **CPU**      | 4 核     | 8 核及以上                 |
| **内存**     | 8GB      | 16GB 及以上                |
| **磁盘空间** | 50GB     | 100GB 及以上（数据盘另算） |
| **网络**     | 千兆网卡 | 万兆网卡                   |

### 1.2 操作系统要求

| 项目         | 要求                                        |
| ------------ | ------------------------------------------- |
| **操作系统** | 银河麒麟高级服务器操作系统 V10（Kylin V10） |
| **内核版本** | 4.19 及以上                                 |
| **架构**     | aarch64（飞腾、鲲鹏）或 x86_64              |
| **SELinux**  | 建议设置为 permissive 或 disabled           |
| **防火墙**   | 开放必要端口（8065、5173、80、5236）        |

### 1.3 软件版本清单

| 软件名称       | 版本       | 用途              | 必需                           |
| -------------- | ---------- | ----------------- | ------------------------------ |
| 达梦数据库 DM8 | 8.1.3.140+ | 元数据存储        | ✅ 必需                         |
| Oracle JDK     | 17.0.9+    | 后端运行环境      | ✅ 必需                         |
| Node.js        | 18.x LTS   | 前端编译环境      | ✅ 必需                         |
| Apache Maven   | 3.8.6+     | 后端编译工具      | ✅ 必需                         |
| Nginx          | 1.20.1+    | 反向代理/静态资源 | ⚪ 可选                         |
| Python         | 3.9+       | 代码执行环境      | ⚪ 可选（如需 Python 分析功能） |

### 1.4 网络端口规划

| 端口号     | 服务                   | 说明                 |
| ---------- | ---------------------- | -------------------- |
| **5236**   | 达梦数据库             | 默认端口，可自定义   |
| **8065**   | DataAgent 后端         | Spring Boot 服务端口 |
| **5173**   | DataAgent 前端（开发） | Vite 开发服务器端口  |
| **80/443** | Nginx                  | 生产环境反向代理端口 |

---

## 二、离线安装包清单

### 2.1 安装包获取方式

由于目标环境为离线环境，需提前在联网环境准备所有安装包，并通过 U 盘、光盘或内网文件服务器传输至目标服务器。

### 2.2 核心软件安装包

#### 2.2.1 达梦数据库

| 文件名                             | 版本 | 架构    | 大小   | 下载地址                             |
| ---------------------------------- | ---- | ------- | ------ | ------------------------------------ |
| `dm8_20230808_x86_rh6_64.iso`      | DM8  | x86_64  | ~2.5GB | https://www.dameng.com/download.html |
| `dm8_20230808_kylin10_aarch64.iso` | DM8  | aarch64 | ~2.5GB | https://www.dameng.com/download.html |

**获取方法**：
1. 访问达梦官网：https://www.dameng.com
2. 注册账号并登录
3. 进入"产品下载" → "数据库管理系统" → "DM8"
4. 根据服务器架构选择对应版本：
   - **x86_64**：选择 `dm8_xxx_rh6_64.iso`（兼容麒麟 V10）
   - **aarch64**：选择 `dm8_xxx_kylin10_aarch64.iso`（麒麟 V10 专版）

#### 2.2.2 Oracle JDK 17

| 文件名                            | 版本    | 架构    | 大小   | 下载地址                                            |
| --------------------------------- | ------- | ------- | ------ | --------------------------------------------------- |
| `jdk-17_linux-x64_bin.tar.gz`     | 17.0.9+ | x86_64  | ~180MB | https://www.oracle.com/java/technologies/downloads/ |
| `jdk-17_linux-aarch64_bin.tar.gz` | 17.0.9+ | aarch64 | ~180MB | https://www.oracle.com/java/technologies/downloads/ |

**获取方法**：
1. 访问 Oracle JDK 官网：https://www.oracle.com/java/technologies/downloads/
2. 选择 "Java 17" 标签
3. 下载对应架构的 Linux 压缩包（tar.gz 格式）

**替代方案**（推荐）：**毕昇 JDK**（华为鲲鹏优化）
- 下载地址：https://www.hikunpeng.com/developer/devkit/compiler/jdk
- 文件名：`bisheng-jdk-17.0.9-linux-aarch64.tar.gz`
- 优势：针对鲲鹏处理器优化，性能更优

#### 2.2.3 Node.js 18 LTS

| 文件名                             | 版本    | 架构    | 大小  | 下载地址                 |
| ---------------------------------- | ------- | ------- | ----- | ------------------------ |
| `node-v18.19.0-linux-x64.tar.xz`   | 18.19.0 | x86_64  | ~22MB | https://nodejs.org/dist/ |
| `node-v18.19.0-linux-arm64.tar.xz` | 18.19.0 | aarch64 | ~21MB | https://nodejs.org/dist/ |

**获取方法**：
1. 访问 Node.js 官网：https://nodejs.org/dist/
2. 选择 `v18.x.x`（LTS 版本）
3. 下载对应架构的二进制包

#### 2.2.4 Apache Maven

| 文件名                          | 版本  | 架构 | 大小 | 下载地址                              |
| ------------------------------- | ----- | ---- | ---- | ------------------------------------- |
| `apache-maven-3.9.5-bin.tar.gz` | 3.9.5 | all  | ~9MB | https://maven.apache.org/download.cgi |

**获取方法**：
1. 访问 Maven 官网：https://maven.apache.org/download.cgi
2. 下载 Binary tar.gz archive

#### 2.2.5 Nginx（可选）

| 文件名                | 版本   | 架构 | 大小   | 下载地址                          |
| --------------------- | ------ | ---- | ------ | --------------------------------- |
| `nginx-1.24.0.tar.gz` | 1.24.0 | 源码 | ~1.1MB | http://nginx.org/en/download.html |

**替代方案**：使用麒麟 V10 自带的 yum 源安装
```bash
sudo yum install nginx -y
```

#### 2.2.6 Python 3.9+（可选）

如果需要 Python 代码执行功能，需要安装 Python 环境。

| 文件名              | 版本   | 下载地址                                 |
| ------------------- | ------ | ---------------------------------------- |
| `Python-3.9.18.tgz` | 3.9.18 | https://www.python.org/downloads/source/ |

**或使用麒麟 V10 自带版本**：
```bash
sudo yum install python3 python3-pip -y
```

### 2.3 DataAgent 项目文件

| 文件                                | 说明             | 获取方式                                          |
| ----------------------------------- | ---------------- | ------------------------------------------------- |
| `DataAgent-1.0.0-SNAPSHOT.zip`      | 项目源码压缩包   | 从 Git 仓库导出或开发环境打包                     |
| `maven-dependencies-offline.tar.gz` | Maven 离线依赖包 | 在联网环境执行 `mvn dependency:go-offline` 后打包 |
| `node_modules.tar.gz`               | 前端离线依赖包   | 在联网环境执行 `npm install` 后打包               |

### 2.4 依赖包目录结构（建议）

```
dataagent-offline-packages/
├── 00-readme.txt                          # 安装包说明文件
├── 01-dameng/
│   ├── dm8_20230808_kylin10_aarch64.iso   # 达梦数据库安装包
│   └── dm8_install_guide.pdf              # 达梦安装手册
├── 02-jdk/
│   ├── jdk-17_linux-aarch64_bin.tar.gz    # JDK 17 安装包
│   └── bisheng-jdk-17.0.9-linux-aarch64.tar.gz  # 毕昇 JDK（备选）
├── 03-nodejs/
│   └── node-v18.19.0-linux-arm64.tar.xz   # Node.js 安装包
├── 04-maven/
│   ├── apache-maven-3.9.5-bin.tar.gz      # Maven 安装包
│   └── maven-dependencies-offline.tar.gz   # Maven 离线依赖
├── 05-nginx/
│   └── nginx-1.24.0.tar.gz                # Nginx 源码包
├── 06-python/
│   └── Python-3.9.18.tgz                  # Python 源码包（可选）
├── 07-dataagent/
│   ├── DataAgent-1.0.0-SNAPSHOT.zip       # 项目源码
│   ├── dataagent-backend.jar              # 编译好的后端 JAR（可选）
│   ├── dataagent-frontend-dist.tar.gz     # 编译好的前端（可选）
│   └── node_modules.tar.gz                # 前端离线依赖
└── scripts/
    ├── install_all.sh                     # 一键安装脚本
    ├── check_env.sh                       # 环境检查脚本
    └── setup_dataagent.sh                 # DataAgent 部署脚本
```

### 2.5 离线依赖包准备脚本

#### 2.5.1 Maven 离线依赖包准备（在联网环境执行）

```bash
#!/bin/bash
# prepare_maven_offline.sh
# 功能：准备 Maven 离线依赖包

cd /path/to/DataAgent

# 下载所有依赖到本地仓库
mvn dependency:go-offline -DskipTests

# 打包本地仓库
cd ~/.m2
tar -czf maven-repository.tar.gz repository/

echo "Maven 离线依赖包已生成：~/.m2/maven-repository.tar.gz"
```

#### 2.5.2 Node.js 离线依赖包准备（在联网环境执行）

```bash
#!/bin/bash
# prepare_npm_offline.sh
# 功能：准备 Node.js 离线依赖包

cd /path/to/DataAgent/data-agent-frontend

# 安装依赖
npm install

# 打包 node_modules
tar -czf node_modules.tar.gz node_modules/

echo "Node.js 离线依赖包已生成：node_modules.tar.gz"
```

---

## 三、系统环境准备

### 3.1 用户与目录规划

#### 3.1.1 创建部署用户

```bash
# 创建 dataagent 用户组
sudo groupadd dataagent

# 创建 dataagent 用户
sudo useradd -g dataagent -m -s /bin/bash dataagent

# 设置密码
sudo passwd dataagent
# 输入密码：Audaque@2026

# 添加 sudo 权限（可选）
sudo usermod -aG wheel dataagent
```

#### 3.1.2 创建目录结构

```bash
# 切换到 dataagent 用户
su - dataagent

# 创建标准目录结构
mkdir -p ~/software           # 软件安装包目录
mkdir -p ~/apps               # 应用安装目录
mkdir -p ~/apps/dameng        # 达梦数据库
mkdir -p ~/apps/jdk           # JDK
mkdir -p ~/apps/nodejs        # Node.js
mkdir -p ~/apps/maven         # Maven
mkdir -p ~/apps/nginx         # Nginx（可选）
mkdir -p ~/dataagent          # DataAgent 应用目录
mkdir -p ~/dataagent/backend  # 后端应用
mkdir -p ~/dataagent/frontend # 前端应用
mkdir -p ~/dataagent/logs     # 日志目录
mkdir -p ~/dataagent/data     # 数据目录
mkdir -p ~/dataagent/uploads  # 文件上传目录
mkdir -p ~/scripts            # 脚本目录
mkdir -p ~/backups            # 备份目录
```

### 3.2 系统参数优化

#### 3.2.1 修改系统资源限制

```bash
# 编辑 limits.conf
sudo vi /etc/security/limits.conf

# 添加以下内容
dataagent soft nofile 65536
dataagent hard nofile 65536
dataagent soft nproc 4096
dataagent hard nproc 4096
```

#### 3.2.2 调整内核参数

```bash
# 编辑 sysctl.conf
sudo vi /etc/sysctl.conf

# 添加以下内容
fs.file-max = 1000000
net.core.somaxconn = 1024
net.ipv4.tcp_max_syn_backlog = 2048
vm.swappiness = 10

# 应用配置
sudo sysctl -p
```

#### 3.2.3 配置防火墙

```bash
# 检查防火墙状态
sudo firewall-cmd --state

# 开放必要端口
sudo firewall-cmd --zone=public --add-port=5236/tcp --permanent  # 达梦数据库
sudo firewall-cmd --zone=public --add-port=8065/tcp --permanent  # DataAgent 后端
sudo firewall-cmd --zone=public --add-port=5173/tcp --permanent  # DataAgent 前端（开发）
sudo firewall-cmd --zone=public --add-port=80/tcp --permanent    # Nginx HTTP
sudo firewall-cmd --zone=public --add-port=443/tcp --permanent   # Nginx HTTPS

# 重载防火墙规则
sudo firewall-cmd --reload

# 验证端口开放
sudo firewall-cmd --zone=public --list-ports
```

#### 3.2.4 配置 SELinux（可选）

```bash
# 检查 SELinux 状态
getenforce

# 方案一：临时关闭（重启后恢复）
sudo setenforce 0

# 方案二：永久关闭（不推荐）
sudo vi /etc/selinux/config
# 修改：SELINUX=disabled

# 方案三：设置为 permissive 模式（推荐）
sudo vi /etc/selinux/config
# 修改：SELINUX=permissive

# 重启生效
sudo reboot
```

### 3.3 时间同步配置

```bash
# 安装 chrony
sudo yum install chrony -y

# 启动并设置开机自启
sudo systemctl start chronyd
sudo systemctl enable chronyd

# 验证时间同步
chronyc tracking
```

### 3.4 环境检查脚本

创建 `~/scripts/check_env.sh`：

```bash
#!/bin/bash
# check_env.sh
# 功能：检查系统环境是否满足部署要求

echo "======================================"
echo "  DataAgent 环境检查脚本"
echo "======================================"
echo ""

# 检查操作系统
echo "[1/10] 检查操作系统..."
if [ -f /etc/kylin-release ]; then
    os_version=$(cat /etc/kylin-release)
    echo "✓ 操作系统：$os_version"
else
    echo "✗ 警告：未检测到银河麒麟系统"
fi
echo ""

# 检查 CPU 架构
echo "[2/10] 检查 CPU 架构..."
arch=$(uname -m)
echo "✓ CPU 架构：$arch"
echo ""

# 检查 CPU 核心数
echo "[3/10] 检查 CPU 核心数..."
cpu_cores=$(nproc)
if [ "$cpu_cores" -ge 4 ]; then
    echo "✓ CPU 核心数：$cpu_cores（满足要求）"
else
    echo "✗ CPU 核心数：$cpu_cores（推荐 4 核及以上）"
fi
echo ""

# 检查内存
echo "[4/10] 检查内存..."
mem_total=$(free -g | awk '/^Mem:/{print $2}')
if [ "$mem_total" -ge 8 ]; then
    echo "✓ 内存：${mem_total}GB（满足要求）"
else
    echo "✗ 内存：${mem_total}GB（推荐 8GB 及以上）"
fi
echo ""

# 检查磁盘空间
echo "[5/10] 检查磁盘空间..."
disk_avail=$(df -h ~/ | awk 'NR==2{print $4}')
echo "✓ 可用磁盘空间：$disk_avail"
echo ""

# 检查网络端口
echo "[6/10] 检查网络端口占用..."
for port in 5236 8065 5173 80; do
    if sudo netstat -tuln | grep -q ":$port "; then
        echo "✗ 端口 $port 已被占用"
    else
        echo "✓ 端口 $port 可用"
    fi
done
echo ""

# 检查防火墙状态
echo "[7/10] 检查防火墙状态..."
if sudo firewall-cmd --state &>/dev/null; then
    echo "✓ 防火墙已启用"
    echo "  已开放端口：$(sudo firewall-cmd --zone=public --list-ports)"
else
    echo "✓ 防火墙未启用"
fi
echo ""

# 检查 SELinux 状态
echo "[8/10] 检查 SELinux 状态..."
selinux_status=$(getenforce)
echo "  SELinux 状态：$selinux_status"
if [ "$selinux_status" != "Enforcing" ]; then
    echo "✓ SELinux 状态正常"
else
    echo "⚠ SELinux 为 Enforcing，建议设置为 Permissive"
fi
echo ""

# 检查时间同步
echo "[9/10] 检查时间同步..."
if systemctl is-active chronyd &>/dev/null; then
    echo "✓ 时间同步服务运行中"
    echo "  当前时间：$(date '+%Y-%m-%d %H:%M:%S')"
else
    echo "✗ 时间同步服务未运行"
fi
echo ""

# 检查目录结构
echo "[10/10] 检查目录结构..."
required_dirs=(
    "$HOME/software"
    "$HOME/apps"
    "$HOME/dataagent"
    "$HOME/scripts"
    "$HOME/backups"
)
for dir in "${required_dirs[@]}"; do
    if [ -d "$dir" ]; then
        echo "✓ 目录存在：$dir"
    else
        echo "✗ 目录缺失：$dir"
    fi
done
echo ""

echo "======================================"
echo "  环境检查完成"
echo "======================================"
```

执行检查：

```bash
chmod +x ~/scripts/check_env.sh
~/scripts/check_env.sh
```

---

## 四、达梦数据库安装

### 4.1 挂载 ISO 镜像

```bash
# 创建挂载点
sudo mkdir -p /mnt/dm8

# 挂载 ISO 镜像（假设 ISO 文件在 ~/software 目录）
sudo mount -o loop ~/software/dm8_20230808_kylin10_aarch64.iso /mnt/dm8

# 验证挂载
ls /mnt/dm8
```

### 4.2 安装前准备

```bash
# 安装依赖包
sudo yum install -y libaio numactl

# 创建达梦用户组和用户
sudo groupadd dinstall
sudo useradd -g dinstall -m -s /bin/bash dmdba
sudo passwd dmdba
# 输入密码：DM123456

# 创建安装目录
sudo mkdir -p /opt/dmdbms
sudo chown -R dmdba:dinstall /opt/dmdbms

# 创建数据目录
sudo mkdir -p /data/dmdata
sudo chown -R dmdba:dinstall /data/dmdata
```

### 4.3 执行安装程序

```bash
# 切换到达梦用户
su - dmdba

# 进入安装目录
cd /mnt/dm8

# 执行安装（命令行模式）
./DMInstall.bin -i

# 按照提示进行安装：
# - 安装路径：/opt/dmdbms
# - 安装类型：典型安装
# - 时区：21（中国标准时间）
```

**图形界面安装（如果有 X11 环境）**：

```bash
# 方式一：本地图形界面
./DMInstall.bin

# 方式二：远程 X11 转发
ssh -X dmdba@server_ip
cd /mnt/dm8
./DMInstall.bin
```

### 4.4 创建数据库实例

```bash
# 切换到达梦用户
su - dmdba

# 创建数据库实例（使用 dminit 工具）
cd /opt/dmdbms/bin

./dminit \
    PATH=/data/dmdata/DAMENG \
    PAGE_SIZE=16 \
    EXTENT_SIZE=16 \
    CASE_SENSITIVE=N \
    CHARSET=1 \
    DB_NAME=DAMENG \
    INSTANCE_NAME=DMSERVER \
    PORT_NUM=5236 \
    SYSDBA_PWD=SYSDBA

# 输出示例：
# 初始化成功！
# 数据库实例创建在：/data/dmdata/DAMENG
```

**参数说明**：
- `PATH`：数据库实例路径
- `PAGE_SIZE`：页大小（KB），推荐 16 或 32
- `EXTENT_SIZE`：簇大小，推荐与 PAGE_SIZE 一致
- `CASE_SENSITIVE`：是否大小写敏感（N=不敏感）
- `CHARSET`：字符集（1=UTF-8，0=GB18030）
- `DB_NAME`：数据库名称
- `INSTANCE_NAME`：实例名称
- `PORT_NUM`：监听端口
- `SYSDBA_PWD`：SYSDBA 用户密码

### 4.5 配置数据库服务

#### 4.5.1 注册系统服务

```bash
# 切换到 root 用户
exit  # 退出 dmdba 用户

# 注册服务
cd /opt/dmdbms/script/root
sudo ./dm_service_installer.sh -t dmserver -dm_ini /data/dmdata/DAMENG/dm.ini -p DMSERVER

# 验证服务注册
sudo systemctl list-unit-files | grep DmService
```

#### 4.5.2 启动数据库

```bash
# 启动服务
sudo systemctl start DmServiceDMSERVER.service

# 设置开机自启
sudo systemctl enable DmServiceDMSERVER.service

# 查看服务状态
sudo systemctl status DmServiceDMSERVER.service

# 查看端口监听
sudo netstat -tuln | grep 5236
```

### 4.6 连接数据库并创建应用库

```bash
# 切换到达梦用户
su - dmdba

# 使用 disql 工具连接数据库
cd /opt/dmdbms/bin
./disql SYSDBA/SYSDBA@localhost:5236

# 在 SQL 提示符下执行：
SQL> CREATE USER data_agent IDENTIFIED BY "Audaque@123";
SQL> GRANT DBA TO data_agent;
SQL> GRANT RESOURCE TO data_agent;
SQL> COMMIT;
SQL> EXIT;
```

### 4.7 达梦数据库基本运维命令

```bash
# 启动数据库
sudo systemctl start DmServiceDMSERVER.service

# 停止数据库
sudo systemctl stop DmServiceDMSERVER.service

# 重启数据库
sudo systemctl restart DmServiceDMSERVER.service

# 查看数据库状态
sudo systemctl status DmServiceDMSERVER.service

# 连接数据库
/opt/dmdbms/bin/disql SYSDBA/SYSDBA@localhost:5236

# 查看数据库版本
/opt/dmdbms/bin/disql SYSDBA/SYSDBA@localhost:5236 -e "SELECT id_code FROM v\$version;"
```

---

## 五、JDK 安装配置

### 5.1 解压安装包

```bash
# 切换到 dataagent 用户
su - dataagent

# 解压 JDK
cd ~/software
tar -xzf jdk-17_linux-aarch64_bin.tar.gz -C ~/apps/jdk/

# 或解压毕昇 JDK
tar -xzf bisheng-jdk-17.0.9-linux-aarch64.tar.gz -C ~/apps/jdk/

# 重命名目录（可选）
mv ~/apps/jdk/jdk-17.0.9 ~/apps/jdk/jdk17
```

### 5.2 配置环境变量

```bash
# 编辑 .bashrc
vi ~/.bashrc

# 添加以下内容
export JAVA_HOME=$HOME/apps/jdk/jdk17
export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=.:$JAVA_HOME/lib

# 使配置生效
source ~/.bashrc
```

### 5.3 验证安装

```bash
# 检查 Java 版本
java -version

# 预期输出（Oracle JDK）：
# java version "17.0.9" 2023-10-17 LTS
# Java(TM) SE Runtime Environment (build 17.0.9+11-LTS-201)
# Java HotSpot(TM) 64-Bit Server VM (build 17.0.9+11-LTS-201, mixed mode, sharing)

# 或预期输出（毕昇 JDK）：
# openjdk version "17.0.9" 2023-10-17
# OpenJDK Runtime Environment (build 17.0.9+7-LTS)
# OpenJDK 64-Bit Server VM (build 17.0.9+7-LTS, mixed mode, sharing)

# 检查 javac 编译器
javac -version

# 检查环境变量
echo $JAVA_HOME
```

---

## 六、Node.js 安装配置

### 6.1 解压安装包

```bash
# 切换到 dataagent 用户
su - dataagent

# 解压 Node.js
cd ~/software
tar -xJf node-v18.19.0-linux-arm64.tar.xz -C ~/apps/nodejs/

# 重命名目录（可选）
mv ~/apps/nodejs/node-v18.19.0-linux-arm64 ~/apps/nodejs/node18
```

### 6.2 配置环境变量

```bash
# 编辑 .bashrc
vi ~/.bashrc

# 添加以下内容
export NODE_HOME=$HOME/apps/nodejs/node18
export PATH=$NODE_HOME/bin:$PATH

# 使配置生效
source ~/.bashrc
```

### 6.3 配置 npm 离线源（可选）

```bash
# 创建 npm 缓存目录
mkdir -p ~/.npm-offline

# 解压离线依赖包（如果有）
cd ~/software
tar -xzf node_modules.tar.gz -C ~/.npm-offline/

# 配置 npm 使用离线缓存
npm config set cache ~/.npm-offline
npm config set offline true
```

### 6.4 验证安装

```bash
# 检查 Node.js 版本
node -v
# 预期输出：v18.19.0

# 检查 npm 版本
npm -v
# 预期输出：10.2.3

# 检查环境变量
echo $NODE_HOME
```

---

## 七、Maven 安装配置

### 7.1 解压安装包

```bash
# 切换到 dataagent 用户
su - dataagent

# 解压 Maven
cd ~/software
tar -xzf apache-maven-3.9.5-bin.tar.gz -C ~/apps/maven/

# 重命名目录（可选）
mv ~/apps/maven/apache-maven-3.9.5 ~/apps/maven/maven3
```

### 7.2 配置环境变量

```bash
# 编辑 .bashrc
vi ~/.bashrc

# 添加以下内容
export MAVEN_HOME=$HOME/apps/maven/maven3
export PATH=$MAVEN_HOME/bin:$PATH

# 使配置生效
source ~/.bashrc
```

### 7.3 配置 Maven 本地仓库

```bash
# 创建本地仓库目录
mkdir -p ~/.m2/repository

# 解压离线依赖包（如果有）
cd ~/software
tar -xzf maven-repository.tar.gz -C ~/.m2/

# 编辑 Maven 配置文件
vi ~/apps/maven/maven3/conf/settings.xml
```

**settings.xml 关键配置**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
  
  <!-- 本地仓库路径 -->
  <localRepository>/home/dataagent/.m2/repository</localRepository>
  
  <!-- 离线模式（如果完全离线） -->
  <offline>true</offline>
  
  <!-- 镜像配置（如果有内网 Maven 私服） -->
  <mirrors>
    <mirror>
      <id>internal-nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://nexus.internal.com/repository/maven-public/</url>
    </mirror>
  </mirrors>
  
  <!-- 服务器认证（如果私服需要认证） -->
  <servers>
    <server>
      <id>internal-nexus</id>
      <username>maven-user</username>
      <password>maven-pass</password>
    </server>
  </servers>
  
</settings>
```

### 7.4 验证安装

```bash
# 检查 Maven 版本
mvn -v

# 预期输出：
# Apache Maven 3.9.5 (xxx)
# Maven home: /home/dataagent/apps/maven/maven3
# Java version: 17.0.9, vendor: Oracle Corporation
# Default locale: zh_CN, platform encoding: UTF-8
# OS name: "linux", version: "4.19.90-24.4.v2101.ky10.aarch64"

# 检查本地仓库
ls ~/.m2/repository
```

---

## 八、DataAgent 后端部署

### 8.1 准备源码

```bash
# 切换到 dataagent 用户
su - dataagent

# 解压项目源码
cd ~/software
unzip DataAgent-1.0.0-SNAPSHOT.zip -d ~/dataagent/

# 进入项目目录
cd ~/dataagent/DataAgent
```

### 8.2 配置数据库连接

编辑 `data-agent-management/src/main/resources/application.yml`：

```yaml
server:
  port: 8065
  address: 0.0.0.0

# 元数据存储数据库配置（达梦数据库）
spring:
  datasource:
    url: jdbc:dm://127.0.0.1:5236?zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=utf-8
    username: data_agent
    password: Audaque@123
    driver-class-name: dm.jdbc.driver.DmDriver
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      test-while-idle: true
      validation-query: SELECT 1
      
  sql:
    init:
      mode: never  # 手动执行 SQL 脚本
      
  ai:
    retry:
      max-attempts: 5
      initial-interval: 2000ms
      multiplier: 2.0
    alibaba:
      data-agent:
        code-executor:
          code-pool-executor: local  # 生产环境建议使用 docker
        file:
          type: local
          path-prefix: /home/dataagent/dataagent/data
          path: uploads
          url-prefix: /uploads
```

### 8.3 执行数据库初始化脚本

#### 8.3.1 获取达梦版本 SQL 脚本

从 [qoder_agent/dm_metadata_rq.md](file:///c:/data/code/DataAgent/qoder_agent/dm_metadata_rq.md) 文档中获取达梦数据库的 schema.sql 脚本（MySQL → 达梦的语法转换版本）。

创建 `~/dataagent/sql/dameng/schema.sql`：

```sql
-- DataAgent 元数据库表结构（达梦版本）

-- 1. Agent 表
CREATE TABLE agent (
    id INT NOT NULL IDENTITY(1,1),
    name VARCHAR2(255) NOT NULL,
    description CLOB,
    create_time TIMESTAMP DEFAULT SYSDATE,
    update_time TIMESTAMP DEFAULT SYSDATE,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_name ON agent(name);

-- 2. Datasource 表
CREATE TABLE datasource (
    id INT NOT NULL IDENTITY(1,1),
    name VARCHAR2(255) NOT NULL,
    type VARCHAR2(50) NOT NULL,
    host VARCHAR2(255),
    port INT,
    database_name VARCHAR2(255),
    username VARCHAR2(255),
    password VARCHAR2(255),
    connection_url VARCHAR2(1024),
    create_time TIMESTAMP DEFAULT SYSDATE,
    update_time TIMESTAMP DEFAULT SYSDATE,
    PRIMARY KEY (id)
);
CREATE INDEX idx_datasource_type ON datasource(type);

-- ... （其他表结构参考 dm_metadata_rq.md 文档第 4.2 节）
```

#### 8.3.2 执行初始化脚本

```bash
# 连接数据库
/opt/dmdbms/bin/disql data_agent/\"Audaque@123\"@localhost:5236

# 执行脚本
SQL> START '/home/dataagent/dataagent/sql/dameng/schema.sql';

# 验证表创建
SQL> SELECT TABLE_NAME FROM USER_TABLES;
SQL> EXIT;
```

### 8.4 编译项目

```bash
# 进入项目根目录
cd ~/dataagent/DataAgent

# 清理并编译
mvn clean package -DskipTests

# 编译成功后，JAR 文件位于：
# data-agent-management/target/spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar
```

**如果离线环境无法编译**，可以在联网环境编译好后传输 JAR 文件：

```bash
# 在联网环境编译
mvn clean package -DskipTests

# 将 JAR 文件打包
cd data-agent-management/target
tar -czf dataagent-backend.tar.gz spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar

# 传输到目标服务器并解压
cd ~/dataagent/backend
tar -xzf ~/software/dataagent-backend.tar.gz
```

### 8.5 创建启动脚本

创建 `~/dataagent/backend/start.sh`：

```bash
#!/bin/bash
# start.sh - DataAgent 后端启动脚本

APP_NAME="dataagent-backend"
APP_JAR="spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar"
APP_HOME="/home/dataagent/dataagent/backend"
LOG_DIR="/home/dataagent/dataagent/logs"
PID_FILE="$APP_HOME/app.pid"

# 创建日志目录
mkdir -p $LOG_DIR

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null 2>&1; then
        echo "应用已经在运行中（PID: $PID）"
        exit 1
    else
        rm -f $PID_FILE
    fi
fi

# 启动应用
cd $APP_HOME
nohup java -jar \
    -Xms2g \
    -Xmx4g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    $APP_JAR \
    > $LOG_DIR/dataagent-backend.log 2>&1 &

# 保存 PID
echo $! > $PID_FILE

echo "DataAgent 后端启动成功！"
echo "PID: $(cat $PID_FILE)"
echo "日志: tail -f $LOG_DIR/dataagent-backend.log"
```

创建 `~/dataagent/backend/stop.sh`：

```bash
#!/bin/bash
# stop.sh - DataAgent 后端停止脚本

APP_HOME="/home/dataagent/dataagent/backend"
PID_FILE="$APP_HOME/app.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "应用未运行"
    exit 1
fi

PID=$(cat $PID_FILE)

if ps -p $PID > /dev/null 2>&1; then
    echo "正在停止应用（PID: $PID）..."
    kill $PID
    
    # 等待进程结束
    for i in {1..30}; do
        if ! ps -p $PID > /dev/null 2>&1; then
            echo "应用已停止"
            rm -f $PID_FILE
            exit 0
        fi
        sleep 1
    done
    
    # 强制杀死
    echo "强制停止应用..."
    kill -9 $PID
    rm -f $PID_FILE
    echo "应用已强制停止"
else
    echo "应用未运行"
    rm -f $PID_FILE
fi
```

赋予执行权限：

```bash
chmod +x ~/dataagent/backend/start.sh
chmod +x ~/dataagent/backend/stop.sh
```

### 8.6 启动后端服务

```bash
# 启动服务
cd ~/dataagent/backend
./start.sh

# 查看日志
tail -f ~/dataagent/logs/dataagent-backend.log

# 检查服务状态
ps aux | grep dataagent-backend

# 检查端口监听
netstat -tuln | grep 8065

# 测试接口
curl http://localhost:8065/api/health
```

### 8.7 配置系统服务（可选）

创建 `/etc/systemd/system/dataagent-backend.service`：

```ini
[Unit]
Description=DataAgent Backend Service
After=network.target DmServiceDMSERVER.service

[Service]
Type=forking
User=dataagent
Group=dataagent
WorkingDirectory=/home/dataagent/dataagent/backend
ExecStart=/home/dataagent/dataagent/backend/start.sh
ExecStop=/home/dataagent/dataagent/backend/stop.sh
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable dataagent-backend.service
sudo systemctl start dataagent-backend.service
sudo systemctl status dataagent-backend.service
```

---

## 九、DataAgent 前端部署

### 9.1 准备源码

```bash
# 切换到 dataagent 用户
su - dataagent

# 前端源码已包含在项目中
cd ~/dataagent/DataAgent/data-agent-frontend
```

### 9.2 安装依赖（离线模式）

```bash
# 解压离线依赖包
cd ~/dataagent/DataAgent/data-agent-frontend
tar -xzf ~/software/node_modules.tar.gz

# 或在联网环境安装
npm install
```

### 9.3 配置后端 API 地址

编辑 `vite.config.js`：

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8065',  // 后端服务地址
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  }
})
```

### 9.4 编译前端

```bash
# 编译生产版本
cd ~/dataagent/DataAgent/data-agent-frontend
npm run build

# 编译成功后，产物位于 dist/ 目录
ls -lh dist/
```

### 9.5 部署前端文件

#### 方案一：使用 Node.js 预览服务（开发/测试）

```bash
# 使用 Vite 预览服务
npm run preview

# 或直接启动开发服务器
npm run dev
```

创建启动脚本 `~/dataagent/frontend/start.sh`：

```bash
#!/bin/bash
# start.sh - DataAgent 前端启动脚本

APP_HOME="/home/dataagent/dataagent/DataAgent/data-agent-frontend"
LOG_DIR="/home/dataagent/dataagent/logs"
PID_FILE="/home/dataagent/dataagent/frontend/app.pid"

mkdir -p $LOG_DIR

cd $APP_HOME
nohup npm run preview -- --host 0.0.0.0 --port 5173 \
    > $LOG_DIR/dataagent-frontend.log 2>&1 &

echo $! > $PID_FILE
echo "DataAgent 前端启动成功！"
echo "访问地址：http://服务器IP:5173"
```

#### 方案二：使用 Nginx 部署（生产环境推荐）

参见 [十、Nginx 安装配置](#十nginx-安装配置)

---

## 十、Nginx 安装配置

### 10.1 安装 Nginx

#### 方案一：使用 yum 安装（推荐）

```bash
# 安装 Nginx
sudo yum install nginx -y

# 启动并设置开机自启
sudo systemctl start nginx
sudo systemctl enable nginx

# 查看版本
nginx -v
```

#### 方案二：源码编译安装

```bash
# 安装编译依赖
sudo yum install -y gcc pcre-devel zlib-devel openssl-devel

# 解压源码
cd ~/software
tar -xzf nginx-1.24.0.tar.gz
cd nginx-1.24.0

# 配置编译选项
./configure \
    --prefix=/home/dataagent/apps/nginx \
    --with-http_ssl_module \
    --with-http_v2_module \
    --with-http_realip_module \
    --with-http_stub_status_module

# 编译并安装
make && make install

# 配置环境变量
echo 'export PATH=$HOME/apps/nginx/sbin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### 10.2 配置 Nginx

编辑 `/etc/nginx/nginx.conf`（yum 安装）或 `~/apps/nginx/conf/nginx.conf`（源码安装）：

```nginx
user dataagent;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    use epoll;
}

http {
    include       mime.types;
    default_type  application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;

    sendfile        on;
    tcp_nopush     on;
    keepalive_timeout  65;
    gzip  on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    # DataAgent 配置
    upstream dataagent_backend {
        server 127.0.0.1:8065;
    }

    server {
        listen 80;
        server_name localhost;  # 修改为实际域名或 IP

        # 前端静态资源
        location / {
            root /home/dataagent/dataagent/DataAgent/data-agent-frontend/dist;
            index index.html;
            try_files $uri $uri/ /index.html;
        }

        # 后端 API 代理
        location /api/ {
            proxy_pass http://dataagent_backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
        }

        # 文件上传接口
        location /uploads/ {
            alias /home/dataagent/dataagent/data/uploads/;
            autoindex off;
        }

        # 健康检查
        location /health {
            proxy_pass http://dataagent_backend/api/health;
        }

        # 错误页面
        error_page 404 /404.html;
        error_page 500 502 503 504 /50x.html;
        location = /50x.html {
            root /usr/share/nginx/html;
        }
    }
}
```

### 10.3 启动 Nginx

```bash
# 测试配置文件
sudo nginx -t

# 启动 Nginx
sudo systemctl start nginx

# 重新加载配置
sudo systemctl reload nginx

# 查看状态
sudo systemctl status nginx
```

### 10.4 配置 HTTPS（可选）

```bash
# 生成自签名证书（测试用）
sudo mkdir -p /etc/nginx/ssl
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout /etc/nginx/ssl/dataagent.key \
    -out /etc/nginx/ssl/dataagent.crt

# 修改 Nginx 配置
sudo vi /etc/nginx/nginx.conf
```

添加 HTTPS 配置：

```nginx
server {
    listen 443 ssl http2;
    server_name localhost;

    ssl_certificate /etc/nginx/ssl/dataagent.crt;
    ssl_certificate_key /etc/nginx/ssl/dataagent.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # 其他配置同上...
}

# HTTP 重定向到 HTTPS
server {
    listen 80;
    server_name localhost;
    return 301 https://$server_name$request_uri;
}
```

---

## 十一、系统启动与验证

### 11.1 完整启动流程

```bash
# 1. 启动达梦数据库（如果未自启）
sudo systemctl start DmServiceDMSERVER.service
sudo systemctl status DmServiceDMSERVER.service

# 2. 启动后端服务
cd ~/dataagent/backend
./start.sh
tail -f ~/dataagent/logs/dataagent-backend.log

# 3. 启动 Nginx
sudo systemctl start nginx
sudo systemctl status nginx

# 4. 验证服务状态
netstat -tuln | grep -E '5236|8065|80'
```

### 11.2 功能验证

#### 11.2.1 后端接口验证

```bash
# 健康检查
curl http://localhost:8065/api/health

# 预期输出：
# {"status":"UP"}

# 获取 Agent 列表
curl http://localhost:8065/api/agents

# 预期输出：
# {"code":200,"data":[],"message":"success"}
```

#### 11.2.2 前端访问验证

打开浏览器，访问：
- **开发模式**：http://服务器IP:5173
- **生产模式（Nginx）**：http://服务器IP

**验证内容**：
1. 页面正常加载
2. 登录功能正常
3. Agent 列表显示正常
4. 数据源配置正常
5. 文件上传功能正常

#### 11.2.3 数据库连接验证

```bash
# 连接达梦数据库
/opt/dmdbms/bin/disql data_agent/\"Audaque@123\"@localhost:5236

# 查询表数据
SQL> SELECT COUNT(*) FROM agent;
SQL> SELECT COUNT(*) FROM datasource;
SQL> EXIT;
```

### 11.3 性能测试

#### 11.3.1 并发测试（使用 ab 工具）

```bash
# 安装 Apache Bench
sudo yum install httpd-tools -y

# 测试接口并发
ab -n 1000 -c 10 http://localhost:8065/api/health

# 查看结果
# Requests per second: XXX [#/sec]
# Time per request: XXX [ms]
```

#### 11.3.2 数据库压力测试

```bash
# 使用 dmhs 工具（达梦自带）
cd /opt/dmdbms/bin
./dmhs -server localhost -port 5236 -username data_agent -password Audaque@123 -threads 10 -duration 60
```

### 11.4 系统监控

创建监控脚本 `~/scripts/monitor.sh`：

```bash
#!/bin/bash
# monitor.sh - 系统监控脚本

echo "======================================"
echo "  DataAgent 系统监控"
echo "  时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""

# 达梦数据库状态
echo "[1/5] 达梦数据库状态"
if sudo systemctl is-active DmServiceDMSERVER.service > /dev/null 2>&1; then
    echo "✓ 达梦数据库运行中"
    netstat -tuln | grep 5236
else
    echo "✗ 达梦数据库未运行"
fi
echo ""

# 后端服务状态
echo "[2/5] DataAgent 后端状态"
if [ -f "/home/dataagent/dataagent/backend/app.pid" ]; then
    PID=$(cat /home/dataagent/dataagent/backend/app.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "✓ 后端服务运行中（PID: $PID）"
        netstat -tuln | grep 8065
    else
        echo "✗ 后端服务未运行"
    fi
else
    echo "✗ 后端服务未运行"
fi
echo ""

# Nginx 状态
echo "[3/5] Nginx 状态"
if sudo systemctl is-active nginx > /dev/null 2>&1; then
    echo "✓ Nginx 运行中"
    netstat -tuln | grep -E ':80|:443'
else
    echo "✗ Nginx 未运行"
fi
echo ""

# 系统资源
echo "[4/5] 系统资源"
echo "  CPU 使用率：$(top -bn1 | grep "Cpu(s)" | awk '{print $2}')%"
echo "  内存使用：$(free -h | awk '/^Mem:/{print $3"/"$2}')"
echo "  磁盘使用：$(df -h ~ | awk 'NR==2{print $3"/"$2" ("$5")"}')"
echo ""

# 近期错误日志
echo "[5/5] 近期错误日志（最近 10 条）"
if [ -f "/home/dataagent/dataagent/logs/dataagent-backend.log" ]; then
    grep -i error /home/dataagent/dataagent/logs/dataagent-backend.log | tail -10
else
    echo "  无日志文件"
fi
echo ""

echo "======================================"
```

设置定时监控：

```bash
# 添加到 crontab
crontab -e

# 每 5 分钟执行一次监控
*/5 * * * * /home/dataagent/scripts/monitor.sh >> /home/dataagent/dataagent/logs/monitor.log 2>&1
```

---

## 十二、常见问题排查

### 12.1 达梦数据库问题

#### 问题 1：数据库无法启动

**现象**：
```bash
sudo systemctl start DmServiceDMSERVER.service
# Job for DmServiceDMSERVER.service failed
```

**排查步骤**：
```bash
# 查看服务日志
sudo journalctl -u DmServiceDMSERVER.service -n 50

# 查看达梦日志
tail -100 /data/dmdata/DAMENG/log/*.log

# 检查端口占用
sudo netstat -tuln | grep 5236

# 检查数据库文件权限
ls -l /data/dmdata/DAMENG/
```

**常见原因**：
1. 端口被占用 → 修改配置文件中的端口
2. 数据文件损坏 → 恢复备份或重建实例
3. 权限不足 → `chown -R dmdba:dinstall /data/dmdata`

#### 问题 2：连接数据库失败

**现象**：
```
ERROR: [SOCKET][IP:127.0.0.1,PORT:5236] 连接被拒绝
```

**排查步骤**：
```bash
# 检查数据库是否运行
ps aux | grep dmserver

# 检查监听端口
netstat -tuln | grep 5236

# 检查防火墙
sudo firewall-cmd --list-ports

# 测试本地连接
/opt/dmdbms/bin/disql SYSDBA/SYSDBA@localhost:5236
```

#### 问题 3：SQL 执行失败

**现象**：
```
ERROR: 无效的字符串或二进制数据
```

**解决方案**：
1. 检查字符集配置（UTF-8）
2. 检查 SQL 语法（达梦与 MySQL 差异）
3. 检查字段长度是否足够

### 12.2 后端服务问题

#### 问题 1：服务启动失败

**现象**：
```bash
./start.sh
# 启动后立即退出
```

**排查步骤**：
```bash
# 查看启动日志
tail -100 ~/dataagent/logs/dataagent-backend.log

# 检查 JDK 版本
java -version

# 检查 JAR 文件
ls -lh ~/dataagent/backend/*.jar

# 手动启动（查看详细错误）
java -jar ~/dataagent/backend/spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar
```

**常见错误**：

1. **数据库连接失败**
```
Caused by: java.sql.SQLException: [SOCKET] 连接被拒绝
```
解决：检查数据库是否运行、配置是否正确

2. **端口被占用**
```
Port 8065 was already in use
```
解决：`netstat -tuln | grep 8065` 查看占用进程并杀死

3. **内存不足**
```
Could not reserve enough space for object heap
```
解决：减小启动脚本中的 `-Xmx` 参数

#### 问题 2：接口返回 500 错误

**现象**：
```bash
curl http://localhost:8065/api/agents
# {"code":500,"message":"Internal Server Error"}
```

**排查步骤**：
```bash
# 查看错误日志
grep ERROR ~/dataagent/logs/dataagent-backend.log

# 查看数据库连接
grep "datasource" ~/dataagent/logs/dataagent-backend.log

# 连接数据库检查表
/opt/dmdbms/bin/disql data_agent/\"Audaque@123\"@localhost:5236
SQL> SELECT TABLE_NAME FROM USER_TABLES;
```

**常见原因**：
1. 数据库表未创建 → 执行初始化脚本
2. SQL 语法错误 → 检查 Mapper XML 中的 SQL
3. 权限不足 → 赋予用户 DBA 权限

#### 问题 3：文件上传失败

**现象**：
```
上传文件失败：找不到目录
```

**解决方案**：
```bash
# 创建上传目录
mkdir -p ~/dataagent/data/uploads
chmod 755 ~/dataagent/data/uploads

# 检查配置
grep "file.path" data-agent-management/src/main/resources/application.yml
```

### 12.3 前端问题

#### 问题 1：npm install 失败

**现象**：
```bash
npm install
# npm ERR! network request to https://registry.npmjs.org/ failed
```

**解决方案**：
```bash
# 使用离线依赖包
cd ~/dataagent/DataAgent/data-agent-frontend
tar -xzf ~/software/node_modules.tar.gz

# 或配置内网 npm 源
npm config set registry http://nexus.internal.com/repository/npm-group/
```

#### 问题 2：编译失败

**现象**：
```bash
npm run build
# ERROR: Cannot find module 'vite'
```

**解决方案**：
```bash
# 清理并重新安装
rm -rf node_modules
npm install

# 检查 Node.js 版本
node -v  # 应为 18.x
```

#### 问题 3：页面空白

**现象**：
浏览器访问页面显示空白

**排查步骤**：
```bash
# 检查浏览器控制台（F12）
# 查看是否有 API 请求错误

# 检查后端服务
curl http://localhost:8065/api/health

# 检查 Nginx 配置
sudo nginx -t

# 查看 Nginx 错误日志
sudo tail -f /var/log/nginx/error.log
```

### 12.4 Nginx 问题

#### 问题 1：Nginx 无法启动

**现象**：
```bash
sudo systemctl start nginx
# Job for nginx.service failed
```

**排查步骤**：
```bash
# 测试配置文件
sudo nginx -t

# 查看错误日志
sudo tail -f /var/log/nginx/error.log

# 检查端口占用
sudo netstat -tuln | grep :80
```

**常见错误**：
1. 配置文件语法错误 → `nginx -t` 检查
2. 端口被占用 → 修改监听端口或停止占用进程
3. 权限不足 → 检查日志目录权限

#### 问题 2：502 Bad Gateway

**现象**：
访问网站显示 502 错误

**排查步骤**：
```bash
# 检查后端服务是否运行
ps aux | grep dataagent-backend

# 检查后端端口
netstat -tuln | grep 8065

# 测试后端接口
curl http://localhost:8065/api/health

# 查看 Nginx 错误日志
sudo tail -f /var/log/nginx/error.log
```

**解决方案**：
1. 启动后端服务
2. 检查 Nginx upstream 配置
3. 检查防火墙规则

### 12.5 性能问题

#### 问题 1：响应速度慢

**排查步骤**：
```bash
# 检查数据库连接池
grep "druid" ~/dataagent/logs/dataagent-backend.log

# 检查慢查询
# 在达梦数据库中执行
SELECT * FROM V$SQL_HISTORY WHERE ELAPSED_TIME > 1000;

# 检查系统资源
top
iostat 1 10
```

**优化方案**：
1. 增加数据库连接池大小
2. 添加索引
3. 优化 SQL 查询
4. 增加服务器资源

#### 问题 2：内存占用高

**排查步骤**：
```bash
# 查看 Java 进程内存
jps -lvm
jmap -heap <PID>

# 生成内存快照
jmap -dump:format=b,file=heap.bin <PID>

# 分析内存（需要 jvisualvm 或 MAT）
```

**优化方案**：
1. 调整 JVM 参数（-Xmx、-Xms）
2. 分析内存泄漏
3. 优化代码逻辑

### 12.6 安全问题

#### 问题 1：默认密码安全

**建议修改的默认密码**：
1. 达梦 SYSDBA：`SYSDBA` → 修改为复杂密码
2. 应用数据库用户：`Audaque@123` → 修改为复杂密码
3. 操作系统用户：修改 dataagent 和 dmdba 用户密码

```bash
# 修改达梦数据库密码
/opt/dmdbms/bin/disql SYSDBA/SYSDBA@localhost:5236
SQL> ALTER USER SYSDBA IDENTIFIED BY "NewPassword@2026";
SQL> ALTER USER data_agent IDENTIFIED BY "NewPassword@2026";
```

#### 问题 2：端口暴露

**安全加固**：
```bash
# 限制数据库只监听本地
# 编辑 /data/dmdata/DAMENG/dm.ini
LISTEN_ADDR = 127.0.0.1

# 防火墙限制访问
sudo firewall-cmd --zone=public --add-rich-rule='rule family="ipv4" source address="192.168.1.0/24" port protocol="tcp" port="8065" accept' --permanent
sudo firewall-cmd --reload
```

---

## 十三、系统运维

### 13.1 日志管理

#### 13.1.1 日志位置

| 组件           | 日志位置                                 | 说明          |
| -------------- | ---------------------------------------- | ------------- |
| 达梦数据库     | `/data/dmdata/DAMENG/log/`               | *.log 文件    |
| DataAgent 后端 | `~/dataagent/logs/dataagent-backend.log` | 应用日志      |
| Nginx 访问日志 | `/var/log/nginx/access.log`              | HTTP 访问日志 |
| Nginx 错误日志 | `/var/log/nginx/error.log`               | HTTP 错误日志 |
| 系统日志       | `/var/log/messages`                      | 系统日志      |

#### 13.1.2 日志轮转配置

创建 `/etc/logrotate.d/dataagent`：

```
/home/dataagent/dataagent/logs/*.log {
    daily
    rotate 30
    missingok
    notifempty
    compress
    delaycompress
    copytruncate
    su dataagent dataagent
}
```

### 13.2 数据备份

#### 13.2.1 达梦数据库备份

**完整备份脚本** `~/scripts/backup_db.sh`：

```bash
#!/bin/bash
# backup_db.sh - 达梦数据库备份脚本

BACKUP_DIR="/home/dataagent/backups/database"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/dameng_backup_$DATE.dmp"

mkdir -p $BACKUP_DIR

# 使用 dmfldr 导出数据
/opt/dmdbms/bin/dmfldr USERID=data_agent/Audaque@123@localhost:5236 \
    FILE=$BACKUP_FILE \
    MODE=FULL \
    LOG=$BACKUP_DIR/backup_$DATE.log

if [ $? -eq 0 ]; then
    echo "备份成功：$BACKUP_FILE"
    
    # 压缩备份文件
    gzip $BACKUP_FILE
    
    # 删除 30 天前的备份
    find $BACKUP_DIR -name "*.dmp.gz" -mtime +30 -delete
else
    echo "备份失败！"
    exit 1
fi
```

**设置定时备份**：

```bash
# 添加到 crontab
crontab -e

# 每天凌晨 2 点备份
0 2 * * * /home/dataagent/scripts/backup_db.sh >> /home/dataagent/dataagent/logs/backup.log 2>&1
```

#### 13.2.2 应用文件备份

```bash
#!/bin/bash
# backup_app.sh - 应用文件备份脚本

BACKUP_DIR="/home/dataagent/backups/application"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# 备份配置文件
tar -czf $BACKUP_DIR/config_$DATE.tar.gz \
    ~/dataagent/DataAgent/data-agent-management/src/main/resources/application.yml

# 备份上传文件
tar -czf $BACKUP_DIR/uploads_$DATE.tar.gz \
    ~/dataagent/data/uploads/

# 删除 30 天前的备份
find $BACKUP_DIR -name "*.tar.gz" -mtime +30 -delete

echo "应用文件备份完成"
```

### 13.3 数据恢复

#### 13.3.1 达梦数据库恢复

```bash
# 停止应用服务
~/dataagent/backend/stop.sh

# 解压备份文件
gunzip /home/dataagent/backups/database/dameng_backup_20260115_020000.dmp.gz

# 导入数据
/opt/dmdbms/bin/dmfldr USERID=data_agent/Audaque@123@localhost:5236 \
    FILE=/home/dataagent/backups/database/dameng_backup_20260115_020000.dmp \
    MODE=FROMUSER \
    LOG=/home/dataagent/backups/database/restore.log

# 启动应用服务
~/dataagent/backend/start.sh
```

#### 13.3.2 应用文件恢复

```bash
# 恢复配置文件
tar -xzf /home/dataagent/backups/application/config_20260115_020000.tar.gz -C /

# 恢复上传文件
tar -xzf /home/dataagent/backups/application/uploads_20260115_020000.tar.gz -C ~/dataagent/data/
```

### 13.4 版本升级

#### 13.4.1 后端升级

```bash
# 1. 备份当前版本
cp ~/dataagent/backend/*.jar ~/backups/backend_$(date +%Y%m%d).jar

# 2. 停止服务
~/dataagent/backend/stop.sh

# 3. 替换新版本 JAR
cp /path/to/new_version.jar ~/dataagent/backend/

# 4. 执行数据库升级脚本（如有）
/opt/dmdbms/bin/disql data_agent/\"Audaque@123\"@localhost:5236 < upgrade_v1.1.sql

# 5. 启动服务
~/dataagent/backend/start.sh

# 6. 验证版本
curl http://localhost:8065/api/version
```

#### 13.4.2 前端升级

```bash
# 1. 备份当前版本
tar -czf ~/backups/frontend_$(date +%Y%m%d).tar.gz ~/dataagent/DataAgent/data-agent-frontend/dist/

# 2. 编译新版本
cd ~/dataagent/DataAgent/data-agent-frontend
npm run build

# 3. 重载 Nginx
sudo systemctl reload nginx

# 4. 清理浏览器缓存并验证
```

### 13.5 性能优化

#### 13.5.1 JVM 参数优化

编辑 `~/dataagent/backend/start.sh`：

```bash
nohup java -jar \
    # 内存配置
    -Xms4g \
    -Xmx8g \
    -XX:MetaspaceSize=256m \
    -XX:MaxMetaspaceSize=512m \
    
    # GC 配置
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:G1ReservePercent=10 \
    -XX:InitiatingHeapOccupancyPercent=45 \
    
    # GC 日志
    -Xlog:gc*:file=~/dataagent/logs/gc.log:time,uptime:filecount=10,filesize=100M \
    
    # JMX 监控
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=9999 \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    
    $APP_JAR \
    > $LOG_DIR/dataagent-backend.log 2>&1 &
```

#### 13.5.2 达梦数据库优化

```sql
-- 连接达梦数据库
/opt/dmdbms/bin/disql data_agent/\"Audaque@123\"@localhost:5236

-- 分析表统计信息
ANALYZE TABLE agent;
ANALYZE TABLE datasource;
-- ... 其他表

-- 优化索引
CREATE INDEX idx_agent_name ON agent(name);
CREATE INDEX idx_datasource_type ON datasource(type);

-- 调整缓存大小（修改 dm.ini）
-- CACHE = 1024  # 单位 MB
```

#### 13.5.3 Nginx 优化

编辑 `/etc/nginx/nginx.conf`：

```nginx
worker_processes auto;
worker_rlimit_nofile 65535;

events {
    worker_connections 4096;
    use epoll;
    multi_accept on;
}

http {
    # 开启文件缓存
    open_file_cache max=10000 inactive=20s;
    open_file_cache_valid 30s;
    open_file_cache_min_uses 2;
    
    # 调整缓冲区
    client_body_buffer_size 128k;
    client_max_body_size 100m;
    
    # 开启 Gzip
    gzip on;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript;
    
    # 其他配置...
}
```

### 13.6 监控与告警

#### 13.6.1 监控指标

**系统级监控**：
- CPU 使用率
- 内存使用率
- 磁盘使用率
- 网络流量

**应用级监控**：
- 后端服务状态
- 接口响应时间
- 错误率
- 并发连接数

**数据库监控**：
- 数据库连接数
- 慢查询数量
- 锁等待时间
- 表空间使用率

#### 13.6.2 告警脚本

创建 `~/scripts/alert.sh`：

```bash
#!/bin/bash
# alert.sh - 系统告警脚本

# 配置
EMAIL="admin@example.com"  # 告警邮箱
CPU_THRESHOLD=80           # CPU 阈值
MEM_THRESHOLD=80           # 内存阈值
DISK_THRESHOLD=85          # 磁盘阈值

# 检查 CPU
CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print int($2)}')
if [ $CPU_USAGE -gt $CPU_THRESHOLD ]; then
    echo "告警：CPU 使用率 $CPU_USAGE% 超过阈值 $CPU_THRESHOLD%" | mail -s "CPU告警" $EMAIL
fi

# 检查内存
MEM_USAGE=$(free | awk '/Mem/{printf("%d", $3/$2*100)}')
if [ $MEM_USAGE -gt $MEM_THRESHOLD ]; then
    echo "告警：内存使用率 $MEM_USAGE% 超过阈值 $MEM_THRESHOLD%" | mail -s "内存告警" $EMAIL
fi

# 检查磁盘
DISK_USAGE=$(df -h ~ | awk 'NR==2{print int($5)}')
if [ $DISK_USAGE -gt $DISK_THRESHOLD ]; then
    echo "告警：磁盘使用率 $DISK_USAGE% 超过阈值 $DISK_THRESHOLD%" | mail -s "磁盘告警" $EMAIL
fi

# 检查服务状态
if ! ps aux | grep -q "[d]ataagent-backend"; then
    echo "告警：DataAgent 后端服务未运行" | mail -s "服务告警" $EMAIL
fi
```

---

## 十四、附录

### 14.1 完整安装脚本

创建 `~/scripts/install_all.sh`（一键安装脚本）：

```bash
#!/bin/bash
# install_all.sh - DataAgent 一键安装脚本
# 使用方法：./install_all.sh

set -e

echo "========================================"
echo "  DataAgent 一键安装脚本"
echo "  目标系统：银河麒麟 V10"
echo "========================================"
echo ""

# 1. 环境检查
echo "[1/10] 环境检查..."
~/scripts/check_env.sh
read -p "是否继续安装？(y/n): " CONTINUE
if [ "$CONTINUE" != "y" ]; then
    echo "安装取消"
    exit 0
fi

# 2. 安装达梦数据库
echo "[2/10] 安装达梦数据库..."
read -p "是否需要安装达梦数据库？(y/n): " INSTALL_DM
if [ "$INSTALL_DM" = "y" ]; then
    echo "请参考文档手动安装达梦数据库（需要 root 权限）"
    read -p "达梦数据库安装完成后按回车继续..." DUMMY
fi

# 3. 安装 JDK
echo "[3/10] 安装 JDK 17..."
if [ ! -d "$HOME/apps/jdk/jdk17" ]; then
    cd ~/software
    tar -xzf jdk-17_linux-aarch64_bin.tar.gz -C ~/apps/jdk/
    mv ~/apps/jdk/jdk-17* ~/apps/jdk/jdk17
    echo "export JAVA_HOME=\$HOME/apps/jdk/jdk17" >> ~/.bashrc
    echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
    source ~/.bashrc
    echo "✓ JDK 安装完成"
else
    echo "✓ JDK 已安装"
fi

# 4. 安装 Node.js
echo "[4/10] 安装 Node.js..."
if [ ! -d "$HOME/apps/nodejs/node18" ]; then
    cd ~/software
    tar -xJf node-v18.19.0-linux-arm64.tar.xz -C ~/apps/nodejs/
    mv ~/apps/nodejs/node-v18* ~/apps/nodejs/node18
    echo "export NODE_HOME=\$HOME/apps/nodejs/node18" >> ~/.bashrc
    echo "export PATH=\$NODE_HOME/bin:\$PATH" >> ~/.bashrc
    source ~/.bashrc
    echo "✓ Node.js 安装完成"
else
    echo "✓ Node.js 已安装"
fi

# 5. 安装 Maven
echo "[5/10] 安装 Maven..."
if [ ! -d "$HOME/apps/maven/maven3" ]; then
    cd ~/software
    tar -xzf apache-maven-3.9.5-bin.tar.gz -C ~/apps/maven/
    mv ~/apps/maven/apache-maven* ~/apps/maven/maven3
    echo "export MAVEN_HOME=\$HOME/apps/maven/maven3" >> ~/.bashrc
    echo "export PATH=\$MAVEN_HOME/bin:\$PATH" >> ~/.bashrc
    source ~/.bashrc
    
    # 解压离线依赖
    tar -xzf ~/software/maven-repository.tar.gz -C ~/.m2/
    echo "✓ Maven 安装完成"
else
    echo "✓ Maven 已安装"
fi

# 6. 解压项目源码
echo "[6/10] 解压项目源码..."
cd ~/software
unzip -o DataAgent-1.0.0-SNAPSHOT.zip -d ~/dataagent/
echo "✓ 项目源码解压完成"

# 7. 初始化数据库
echo "[7/10] 初始化数据库..."
read -p "是否需要初始化数据库？(y/n): " INIT_DB
if [ "$INIT_DB" = "y" ]; then
    /opt/dmdbms/bin/disql data_agent/\"Audaque@123\"@localhost:5236 < ~/dataagent/sql/dameng/schema.sql
    echo "✓ 数据库初始化完成"
fi

# 8. 编译后端
echo "[8/10] 编译后端项目..."
cd ~/dataagent/DataAgent
mvn clean package -DskipTests
cp data-agent-management/target/*.jar ~/dataagent/backend/
echo "✓ 后端编译完成"

# 9. 编译前端
echo "[9/10] 编译前端项目..."
cd ~/dataagent/DataAgent/data-agent-frontend
tar -xzf ~/software/node_modules.tar.gz
npm run build
echo "✓ 前端编译完成"

# 10. 配置服务
echo "[10/10] 配置系统服务..."
cp ~/scripts/start.sh ~/dataagent/backend/
cp ~/scripts/stop.sh ~/dataagent/backend/
chmod +x ~/dataagent/backend/*.sh
echo "✓ 服务配置完成"

echo ""
echo "========================================"
echo "  安装完成！"
echo "========================================"
echo ""
echo "后续步骤："
echo "1. 启动后端：~/dataagent/backend/start.sh"
echo "2. 启动 Nginx：sudo systemctl start nginx"
echo "3. 访问系统：http://服务器IP"
echo ""
```

### 14.2 环境变量配置模板

创建 `~/.bash_profile`（或追加到 `~/.bashrc`）：

```bash
# DataAgent 环境变量配置

# JDK
export JAVA_HOME=$HOME/apps/jdk/jdk17
export PATH=$JAVA_HOME/bin:$PATH

# Node.js
export NODE_HOME=$HOME/apps/nodejs/node18
export PATH=$NODE_HOME/bin:$PATH

# Maven
export MAVEN_HOME=$HOME/apps/maven/maven3
export PATH=$MAVEN_HOME/bin:$PATH

# Nginx（如果是源码安装）
export PATH=$HOME/apps/nginx/sbin:$PATH

# 应用目录
export DATAAGENT_HOME=$HOME/dataagent
export DATAAGENT_LOGS=$DATAAGENT_HOME/logs

# 达梦数据库客户端（可选）
export PATH=/opt/dmdbms/bin:$PATH

# 字符集
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8
```

### 14.3 常用命令速查

```bash
# 系统信息
uname -a                # 查看系统信息
cat /etc/kylin-release  # 查看麒麟版本
free -h                 # 查看内存
df -h                   # 查看磁盘

# 达梦数据库
sudo systemctl status DmServiceDMSERVER.service  # 查看状态
/opt/dmdbms/bin/disql SYSDBA/SYSDBA@localhost:5236  # 连接数据库

# DataAgent 后端
~/dataagent/backend/start.sh   # 启动
~/dataagent/backend/stop.sh    # 停止
tail -f ~/dataagent/logs/dataagent-backend.log  # 查看日志

# Nginx
sudo systemctl start nginx     # 启动
sudo systemctl stop nginx      # 停止
sudo systemctl reload nginx    # 重载配置
sudo nginx -t                  # 测试配置

# 网络
netstat -tuln | grep 8065     # 查看端口
sudo firewall-cmd --list-ports  # 查看防火墙
curl http://localhost:8065/api/health  # 测试接口

# 日志
tail -f /var/log/messages      # 系统日志
sudo tail -f /var/log/nginx/access.log  # Nginx 访问日志
sudo tail -f /var/log/nginx/error.log   # Nginx 错误日志
```

### 14.4 目录结构总览

```
/home/dataagent/
├── apps/                      # 软件安装目录
│   ├── jdk/jdk17/             # JDK 17
│   ├── nodejs/node18/         # Node.js 18
│   ├── maven/maven3/          # Maven 3
│   ├── nginx/                 # Nginx（可选）
│   └── dameng/                # 达梦客户端（可选）
├── dataagent/                 # 应用目录
│   ├── DataAgent/             # 项目源码
│   ├── backend/               # 后端运行目录
│   │   ├── *.jar              # 后端 JAR 文件
│   │   ├── start.sh           # 启动脚本
│   │   ├── stop.sh            # 停止脚本
│   │   └── app.pid            # 进程 PID 文件
│   ├── frontend/              # 前端运行目录（可选）
│   ├── logs/                  # 日志目录
│   │   ├── dataagent-backend.log
│   │   ├── dataagent-frontend.log
│   │   ├── gc.log
│   │   └── monitor.log
│   ├── data/                  # 数据目录
│   │   └── uploads/           # 文件上传目录
│   └── sql/                   # SQL 脚本
│       └── dameng/
│           └── schema.sql
├── software/                  # 安装包目录
├── scripts/                   # 脚本目录
│   ├── check_env.sh          # 环境检查
│   ├── install_all.sh        # 一键安装
│   ├── backup_db.sh          # 数据库备份
│   ├── backup_app.sh         # 应用备份
│   ├── monitor.sh            # 系统监控
│   └── alert.sh              # 告警脚本
├── backups/                   # 备份目录
│   ├── database/             # 数据库备份
│   └── application/          # 应用备份
└── .m2/                       # Maven 本地仓库
    └── repository/

/opt/dmdbms/                   # 达梦数据库安装目录（root）
/data/dmdata/DAMENG/           # 达梦数据库数据目录（dmdba）
```

### 14.5 参考文档

1. **银河麒麟 V10 官方文档**
   - https://eco.kylinos.cn/document.html

2. **达梦数据库文档**
   - 安装手册：https://eco.dameng.com/document/dm/zh-cn/start/index.html
   - SQL 参考：https://eco.dameng.com/document/dm/zh-cn/sql-dev/index.html
   - 运维手册：https://eco.dameng.com/document/dm/zh-cn/ops/index.html

3. **Spring Boot 官方文档**
   - https://docs.spring.io/spring-boot/docs/current/reference/html/

4. **Vue 3 官方文档**
   - https://cn.vuejs.org/guide/introduction.html

5. **Nginx 官方文档**
   - http://nginx.org/en/docs/

### 14.6 联系与支持

如在部署过程中遇到问题，可以通过以下方式获取帮助：

1. **查看项目文档**：`docs/` 目录下的相关文档
2. **提交 Issue**：https://github.com/audaque/DataAgent/issues
3. **技术支持邮箱**：support@audaque.com

---

## 文档结束

本文档提供了 DataAgent 在银河麒麟 V10 系统上使用达梦数据库的完整离线部署方案。

**关键要点总结**：

✅ **环境准备**：麒麟 V10 + 达梦 DM8 + JDK 17 + Node.js 18 + Maven 3
✅ **离线安装**：所有依赖包提前准备，支持完全离线部署
✅ **数据库配置**：达梦数据库作为元数据存储，支持高可用配置
✅ **应用部署**：后端 + 前端 + Nginx 完整部署方案
✅ **运维管理**：备份恢复、监控告警、日志管理、性能优化
✅ **故障排查**：详细的问题排查步骤和解决方案

**后续优化建议**：

1. **高可用部署**：配置达梦数据库主备、应用集群
2. **容器化部署**：使用 Docker/Kubernetes 进行容器化
3. **自动化运维**：使用 Ansible/SaltStack 实现自动化部署
4. **监控平台**：接入 Prometheus + Grafana 监控体系
5. **日志分析**：接入 ELK/Loki 日志分析平台

**祝部署顺利！** 🎉