# DataAgent基于达梦部署手册


本文档详细介绍了如何在达梦数据库（DM8）环境下部署DataAgent系统的安装、配置和首次运行。

## 环境要求

- **JDK**: 17
- **dameng**: V8
- **Node.js**: v22.22.0
- **Nginx**: 1.21.5 
- **向量数据库milvus**: v2.6.7


## 部署前准备

### 获取部署包和资源

在开始部署前，您需要准备以下资源：`DataAgent_YYYYMMDD_xxxx.tar.gz`

解压
```base
tar -zxf DataAgent_20260302_144625.tar.gz
mv DataAgent_20260302_144625 DataAgent
```

目录结构：

```
output/
├── backend/                      # 后端文件
│   ├── dataagent-backend.jar         # 后端 JAR 包
│   └── dataagent-backend.jar.sha256  # 校验和
├── frontend/                     # 前端文件
│   ├── favicon.png
│   ├── index.html
│   ├── logo.png
│   ├── widget.js
│   └── widget-test.html
├── config/                       # 配置模板
│   ├── application.yml.template  # 后端配置模板
│   ├── nginx-dataagent.conf.template  # Nginx 配置模板
│   └── sql/                     # 数据库初始化脚本
│       ├── mysql/
│       └── dameng/
├── scripts/                      # 安装脚本
│   ├── build-dataagent          # DataAgent 安装脚本
│   ├── install_milvus.sh        # Milvus 向量数据库安装脚本
├── VERSION.txt                   # 版本信息
└── INSTALL.txt                   # 本文件
```

### JDK要求

DataAgent需要JDK 17+版本：

```bash
java -version
# 应输出类似：openjdk version "17.0.x" 或更高版本
```

如果未安装JDK 17+，请安装：
```bash
# CentOS/RHEL
sudo yum install java-17-openjdk-devel -y

# Ubuntu/Debian
sudo apt-get install openjdk-17-jdk -y
```

## 第一步：创建达梦用户和数据库

### 1.1 安装达梦数据库

一般从环境管理员处获取账号密码即可，如果安装可以参DM8的安装手册。
1. 下载达梦数据库 DM8 安装包
2. 执行安装程序，按照提示完成安装
3. 配置实例参数（内存分配、字符集等）

### 1.2 创建业务数据库

1. 使用达梦管理工具或命令行连接数据库
2. 创建业务数据库和用户：
   ```sql
   -- 创建用户
   CREATE USER ADQ_DATA_AGENT IDENTIFIED BY "DataAgent@2026" TEMPORARY TABLESPACE TEMP;
   
   -- 授予权限
   GRANT RESOURCE TO ADQ_DATA_AGENT;
   GRANT CREATE VIEW,CREATE PROCEDURE,CREATE SEQUENCE,CREATE TABLE TO ADQ_DATA_AGENT;
   ```

### 1.3 初始化业务库和源数据库

1. 连接到新建的DATA_AGENT用户
2. 执行表结构初始化脚本：
   ```sql
   -- 连接数据库
   DISQL DATA_AGENT/DATA_AGENT@localhost:5236
   
   -- 执行表结构脚本
   START /path/to/schema.sql
   ```
3. 执行初始数据脚本：
   ```sql
   START /path/to/data.sql
   ```

## 第二步：安装向量数据库

DataAgent使用向量数据库来支持RAG（检索增强生成）功能。

### 2.1 安装Milvus向量数据库

1. 安装Docker和Docker Compose

```bash
# 使用阿里云的 docker-ce 源）
sudo dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 关键步骤：把 $releasever 全部替换成 8
sudo sed -i 's|$releasever|8|g' /etc/yum.repos.d/docker-ce.repo
sudo dnf clean all
sudo dnf makecache
sudo dnf install docker-ce docker-ce-cli containerd.io docker-compose-plugin docker-buildx-plugin


```


2. 导出并导入镜像

导出镜像

    ```bash
    docker save \
    milvusdb/milvus:v2.6.9 \
    quay.io/coreos/etcd:v3.5.25 \
    minio/minio:RELEASE.2024-12-18T13-15-44Z \
    | gzip > milvus-images.tar.gz

    ```

从产品处获取离线的镜像文件 `milvus-images.tar.gz`

导入镜像

    ```bash
    gzip -dc milvus-images.tar.gz | docker load
    docker images
    ```


2. 创建Milvus配置文件：


    ```bash
    mkdir -p /data/milvus_docker
    cd /data/milvus_docker
    touch docker-compose.yml
    ```

以下是 `docker-compose.yml` 的内容
```yaml
version: '3.5'

services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.25
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://etcd:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2024-12-18T13-15-44Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
      - "9000:9000"
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/minio:/minio_data
    command: minio server /minio_data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  standalone:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.6.9
    command: ["milvus", "run", "standalone"]
    security_opt:
    - seccomp:unconfined
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
      MQ_TYPE: woodpecker
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/milvus:/var/lib/milvus
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      start_period: 90s
      timeout: 20s
      retries: 3
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - "etcd"
      - "minio"

networks:
  default:
    name: milvus
```

3. 启动Milvus服务：
   ```bash
   docker-compose up -d
   ```

4. milvus WEB页面

http://xx.xx.xx.xx:9091/webui/


## 第三步：安装Nginx

### 3.1 安装Nginx

在 `CentOS/RHEL/KylinV10` 系统上：
```bash
sudo yum install nginx -y
```

在 `Ubuntu/Debian` 系统上：
```bash
sudo apt-get install nginx -y
```

### 第四步：自动部署

根据实际配置信息填写并执行

```bash



# 改为实际的java路径
export JAVA_HOME="/usr/lib/jvm/jdk-17.0.2"
export PATH=$JAVA_HOME/bin:$PATH

# 改为实际安装包目录，`DataAgent_YYYYMMDD_xxxx.tar.gz` 解压的路径
package_dir="/data/DataAgent"

# 改为实际部署目录
deploy_dir="/data/adqdataagent"

# 进入解压的路径
cd /path/to/scripts

# 需要修改的参数信息： db-host，db-port，db-name, db-user，db-password, milvus-host（本机IP）
# 其他使用默认
./build-dataagent.sh \
  --deploy \
  --deploy-dir ${deploy_dir} \
  --package-dir ${package_dir} \
  --db-type dameng \
  --db-host 172.16.1.137 \
  --db-port 5236 \
  --db-name adq_data_agent \
  --db-user adq_data_agent \
  --db-password "DataAgent@2026" \
  --backend-port 8065 \
  --frontend-port 8080 \
  --vector-store milvus \
  --milvus-host 172.16.1.137 \
  --milvus-port 19530 \
  --milvus-username root \
  --milvus-password Milvus \
  --milvus-database default \
  --milvus-collection data_agent_vector

```


## 第五步：访问应用，配置向量模型和大语言模型

### 5.1 访问应用

打开浏览器，访问以下地址：
- 前端界面：`http://your-server-ip` 或 `http://your-domain.com`
- 后端API文档：`http://your-server-ip:8065/swagger-ui.html`

### 5.2 配置向量模型

1. 登录DataAgent管理后台
2. 进入"模型配置"页面
3. 选择"Embedding模型"
4. 配置向量模型信息：
   - 模型类型：OpenAI Compatible
   - API地址：向量模型服务地址
   - API密钥：向量模型API密钥
   - 模型名称：使用的嵌入模型名
5. 点击"测试连接"验证配置
6. 点击"激活"使配置生效

### 5.3 配置大语言模型

1. 在"模型配置"页面选择"Chat模型"
2. 配置大语言模型信息：
   - 模型类型：OpenAI Compatible或其他支持的模型类型
   - API地址：大语言模型服务地址
   - API密钥：大语言模型API密钥
   - 模型名称：使用的聊天模型名
3. 点击"测试连接"验证配置
4. 点击"激活"使配置生效

### 5.4 验证系统功能

1. 创建测试数据源连接
2. 上传业务知识文档
3. 创建智能体并测试对话功能
4. 验证NL2SQL功能
5. 检查向量检索功能是否正常

## 常见问题及解决方案

### 问题1：达梦数据库连接失败

**现象**：后端启动时报数据库连接错误
**解决方法**：
1. 检查达梦数据库是否正常运行
2. 检查连接URL、用户名、密码是否正确
3. 确认达梦JDBC驱动已正确添加到classpath
4. 检查防火墙是否阻塞5236端口

### 问题2：向量数据库连接失败

**现象**：知识库功能无法使用
**解决方法**：
1. 检查Milvus服务是否正常运行
2. 验证Milvus连接配置
3. 确认网络连通性

### 问题3：大语言模型无法调用

**现象**：对话功能报错
**解决方法**：
1. 检查API密钥是否正确
2. 确认模型服务地址可达
3. 验证网络连接和防火墙设置

## 维护和监控

### 日志监控

- 应用日志：`/部署路径/logs/application.log`
- Nginx访问日志：`/var/log/nginx/dataagent_access.log`
- Nginx错误日志：`/var/log/nginx/dataagent_error.log`

### 性能监控

- 定期检查JVM内存使用情况
- 监控数据库连接池状态
- 检查向量数据库性能指标
- 关注API响应时间

## 备份和恢复

### 数据库备份

定期备份达梦数据库：
```bash
# 使用达梦备份工具
dmrman BACKUP DATABASE '/dm8/data/DAMENG/dm.ini' FULL
```

### 配置文件备份

备份重要配置文件：
- application.yml
- Nginx配置文件
- systemd服务文件

