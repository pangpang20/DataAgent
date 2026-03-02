# DataAgent基于达梦部署手册

## 概述

本文档详细介绍了如何在达梦数据库（DM8）环境下部署DataAgent系统。达梦数据库是中国自主产权的关系型数据库管理系统，具有高安全性、高性能的特点。DataAgent支持使用达梦数据库作为元数据存储，与原有的MySQL版本并行存在，用户可根据实际需求选择使用。

## 部署前准备

### 0. 获取部署包和资源

在开始部署前，您需要准备以下资源：

1. **前端包**：`data-agent-frontend/dist` 目录下的构建产物
2. **后端包**：`data-agent-management/target/spring-ai-audaque-data-agent-management-*.jar`
3. **初始化SQL脚本**：
   - 表结构脚本：`docker-file/config/dameng/schema.sql`
   - 初始数据脚本：`docker-file/config/dameng/data.sql`
4. **安装文档**：本文档及达梦数据库官方安装手册
5. **达梦JDBC驱动**：`DmJdbcDriver18-8.1.3.140.jar`

## 第一步：创建达梦用户和数据库

### 1.1 安装达梦数据库

1. 下载达梦数据库 DM8 安装包
2. 执行安装程序，按照提示完成安装
3. 配置实例参数（内存分配、字符集等）

### 1.2 创建业务数据库

1. 使用达梦管理工具或命令行连接数据库
2. 创建业务数据库和用户：
   ```sql
   -- 创建表空间
   CREATE TABLESPACE DATA_AGENT_DATA DATAFILE '/dm8/data/data_agent_data.dbf' SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE 2048M;
   
   -- 创建用户
   CREATE USER DATA_AGENT IDENTIFIED BY "DataAgent2023!" DEFAULT TABLESPACE DATA_AGENT_DATA;
   
   -- 授予权限
   GRANT CONNECT,RESOURCE,DBA TO DATA_AGENT;
   GRANT CREATE VIEW,CREATE PROCEDURE,CREATE SEQUENCE,CREATE TABLE TO DATA_AGENT;
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
2. 创建Milvus配置文件：
   ```yaml
   version: '3.5'
   services:
     etcd:
       container_name: milvus-etcd
       image: quay.io/coreos/etcd:v3.5.5
       environment:
         - ETCD_AUTO_COMPACTION_MODE=revision
         - ETCD_AUTO_COMPACTION_RETENTION=1000
         - ETCD_QUOTA_BACKEND_BYTES=4294967296
         - ETCD_SNAPSHOT_COUNT=50000
       volumes:
         - ${DOCKER_VOLUME_DIRECTORY}/etcd:/etcd
       command: >
         etcd 
         -advertise-client-urls=http://127.0.0.1:2379 
         -listen-client-urls=http://0.0.0.0:2379 
         -initial-advertise-peer-urls=http://127.0.0.1:2380 
         -listen-peer-urls=http://0.0.0.0:2380 
         -initial-cluster=default=http://127.0.0.1:2380 
         -initial-cluster-state=new 
         -data-dir=/etcd
       networks:
         - milvus
    
     minio:
       container_name: milvus-minio
       image: minio/minio:RELEASE.2023-09-04T19-57-37Z
       environment:
         MINIO_ACCESS_KEY: minioadmin
         MINIO_SECRET_KEY: minioadmin
       ports:
         - "9001:9001"
         - "9000:9000"
       volumes:
         - ${DOCKER_VOLUME_DIRECTORY}/minio:/minio_data
       command: minio server /minio_data --console-address ":9001"
       networks:
         - milvus
    
     standalone:
       container_name: milvus-standalone
       image: milvusdb/milvus:v2.3.1
       command: ["milvus", "run", "standalone"]
       environment:
         - ETCD_ENDPOINTS=etcd:2379
         - MINIO_ADDRESS=minio:9000
       volumes:
         - ${DOCKER_VOLUME_DIRECTORY}/milvus:/var/lib/milvus
       ports:
         - "19530:19530"
       depends_on:
         - "etcd"
         - "minio"
       networks:
         - milvus
   
   networks:
     milvus:
       driver: bridge
   
   volumes:
     etcd:
     minio:
     milvus:
   ```

3. 启动Milvus服务：
   ```bash
   docker-compose up -d
   ```

## 第三步：安装Nginx

### 3.1 安装Nginx

在CentOS/RHEL系统上：
```bash
sudo yum install nginx -y
```

在Ubuntu/Debian系统上：
```bash
sudo apt-get install nginx -y
```

### 3.2 配置Nginx

创建Nginx配置文件 `/etc/nginx/sites-available/dataagent`：
```nginx
server {
    listen 80;
    server_name your-domain.com;  # 修改为实际域名或 IP

    # 前端静态文件
    location / {
        root /var/www/dataagent;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://localhost:8065/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 文件上传配置
    client_max_body_size 10M;
}
```

启用配置并重启Nginx：
```bash
sudo ln -s /etc/nginx/sites-available/dataagent /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

## 第四步：配置application.yml

### 4.1 达梦数据库配置

修改DataAgent的application.yml配置文件，配置达梦数据库连接：

```yaml
spring:
  datasource:
    platform: dameng  # 指定数据库平台为达梦
    url: jdbc:dm://localhost:5236/DATA_AGENT?charset=utf8  # 达梦数据库连接URL
    username: DATA_AGENT  # 数据库用户名
    password: DataAgent2023!  # 数据库密码
    driver-class-name: dm.jdbc.driver.DmDriver  # 达梦驱动类名
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1 FROM DUAL  # 达梦验证查询
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
      filters: stat,wall,log4j
      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000
      use-global-data-source-stat: true
      default-transaction-isolation: 2  # READ_COMMITTED

# 向量数据库配置
spring:
  ai:
    vectorstore:
      milvus:
        host: localhost
        port: 19530
        database: default
        username: 
        password:

# 大语言模型配置
spring:
  ai:
    openai:
      chat:
        base-url: https://your-openai-compatible-api.com/v1
        api-key: your-api-key
        model: gpt-4  # 或其他模型名称
      embedding:
        base-url: https://your-openai-compatible-api.com/v1
        api-key: your-api-key
        model: text-embedding-ada-002  # 或其他嵌入模型

# 服务器配置
server:
  port: 8065  # 后端服务端口

# 日志配置
logging:
  level:
    com:
      audaque: DEBUG
    org:
      springframework: INFO
  file:
    name: /var/log/dataagent/application.log

# 应用配置
spring:
  ai:
    audaque:
      data-agent:
        # 向量存储配置
        vector-store:
          enable-hybrid-search: true
          similarity-threshold: 0.7
          top-k: 5
        # 代码执行器配置
        code-executor:
          type: docker  # 或 local
          docker-image: continuumio/anaconda3:latest
          timeout: 300
        # LLM服务类型
        llm-service-type: STREAM  # STREAM 或 BLOCK
```

## 第五步：环境检查

### 5.1 JDK要求

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

### 5.2 其他依赖检查

- Maven 3.6.3+（推荐3.9.9）
- Node.js 16+
- npm 9.x 或 10.x
- Docker 20.10+

### 5.3 端口检查

确保以下端口未被占用：
- 8065：DataAgent后端服务端口
- 80：Nginx前端端口
- 5236：达梦数据库端口
- 19530：Milvus向量数据库端口
- 9000, 9001：MinIO对象存储端口
- 2379, 2380：Etcd服务端口

## 第六步：部署前端并启动

### 6.1 部署前端文件

1. 将前端构建产物复制到Web目录：
   ```bash
   sudo mkdir -p /var/www/dataagent
   sudo cp -r /path/to/frontend/dist/* /var/www/dataagent/
   ```

2. 设置正确的文件权限：
   ```bash
   sudo chown -R www-data:www-data /var/www/dataagent
   sudo chmod -R 755 /var/www/dataagent
   ```

### 6.2 启动前端服务

如果使用Nginx，前端已通过Nginx服务自动启动。

如果需要单独启动前端开发服务：
```bash
cd data-agent-frontend
npm install
npm run serve
```

## 第七步：启动后端服务

### 7.1 启动DataAgent后端

```bash
# 进入部署目录
cd /opt/dataagent

# 启动服务
java -Xmx2g -Xms2g \
  -XX:+UseG1GC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/dataagent/heapdump.hprof \
  -Dfile.encoding=UTF-8 \
  -jar dataagent-backend.jar
```

### 7.2 后台运行服务

创建systemd服务文件：
```bash
sudo vi /etc/systemd/system/dataagent.service
```

添加以下内容：
```ini
[Unit]
Description=Audaque DataAgent Service
After=network.target dameng.service

[Service]
Type=simple
User=dataagent
WorkingDirectory=/opt/dataagent
ExecStart=/usr/bin/java -Xmx2g -Xms2g -jar /opt/dataagent/dataagent-backend.jar
Restart=always
RestartSec=10
StandardOutput=append:/var/log/dataagent/console.log
StandardError=append:/var/log/dataagent/error.log

# 环境变量
Environment="SPRING_DATASOURCE_URL=jdbc:dm://localhost:5236/DATA_AGENT"
Environment="SPRING_DATASOURCE_USERNAME=DATA_AGENT"
Environment="SPRING_DATASOURCE_PASSWORD=DataAgent2023!"

# 外部Prompt目录配置（可选）
Environment="DATAAGENT_PROMPT_DIR=/opt/dataagent/prompts"

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl start dataagent
sudo systemctl enable dataagent
```

## 第八步：访问应用，配置向量模型和大语言模型

### 8.1 访问应用

打开浏览器，访问以下地址：
- 前端界面：`http://your-server-ip` 或 `http://your-domain.com`
- 后端API文档：`http://your-server-ip:8065/swagger-ui.html`

### 8.2 配置向量模型

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

### 8.3 配置大语言模型

1. 在"模型配置"页面选择"Chat模型"
2. 配置大语言模型信息：
   - 模型类型：OpenAI Compatible或其他支持的模型类型
   - API地址：大语言模型服务地址
   - API密钥：大语言模型API密钥
   - 模型名称：使用的聊天模型名
   - 温度参数：根据需要调整（通常0.1-0.7）
3. 点击"测试连接"验证配置
4. 点击"激活"使配置生效

### 8.4 验证系统功能

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

- 应用日志：`/var/log/dataagent/application.log`
- 控制台日志：`/var/log/dataagent/console.log`
- Nginx访问日志：`/var/log/nginx/access.log`
- Nginx错误日志：`/var/log/nginx/error.log`

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

### 知识库备份

备份向量数据库中的知识库数据，以及原始知识文件。

## 升级指南

当需要升级DataAgent版本时：

1. 停止当前服务
2. 备份现有数据和配置
3. 替换新的后端JAR包和前端文件
4. 检查配置文件兼容性
5. 启动新版本服务
6. 验证功能完整性