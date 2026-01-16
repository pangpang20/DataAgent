# DataAgent Docker 部署指南

## 概述

本目录包含 DataAgent 项目的 Docker 容器化部署配置，支持使用本地代码构建镜像。

## 目录结构

```
docker-file/
├── config/                          # 配置文件目录
│   ├── mysql/                       # MySQL 数据源初始化脚本
│   │   └── product_db.sql
│   ├── postgres/                    # PostgreSQL 数据源初始化脚本
│   │   └── china_population_db.sql
│   └── nginx.conf                   # Nginx 配置文件
├── Dockerfile-backend               # 后端服务镜像构建文件
├── Dockerfile-frontend              # 前端服务镜像构建文件
├── docker-compose.yml               # 完整服务编排配置
├── docker-compose-datasource.yml    # 仅数据源服务编排配置
└── README.md                        # 本文件
```

## 服务说明

### 完整服务 (docker-compose.yml)

包含以下服务：
- **mysql**: 内部元数据数据库 (nl2sql_db)
- **backend**: Java 后端服务 (端口 8065)
- **frontend**: Vue 前端 + Nginx (端口 3000)
- **mysql-data**: MySQL 模拟数据源
- **postgres-data**: PostgreSQL 模拟数据源

### 独立数据源 (docker-compose-datasource.yml)

仅包含测试用的数据源服务：
- **mysql-data**: MySQL 数据源 (端口 13306)
- **postgres-data**: PostgreSQL 数据源 (端口 15432)

## 前置要求

1. **Docker** 和 **Docker Compose** 已安装
2. **配置环境变量**：需要设置 AI 服务的 API Key
   ```bash
   # Windows PowerShell
   $env:AI_DASHSCOPE_API_KEY="your-api-key-here"
   
   # Linux/Mac
   export AI_DASHSCOPE_API_KEY="your-api-key-here"
   ```

## 快速启动

### 启动完整服务

```bash
cd docker-file

# 构建并启动所有服务
docker-compose build
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

访问地址：
- 前端界面: http://localhost:3000
- 后端API: http://localhost:8065

### 仅启动数据源服务

```bash
cd docker-file

# 启动数据源
docker-compose -f docker-compose-datasource.yml up -d
```

数据源连接信息：
- MySQL: `localhost:13306`, 用户名/密码: `root/root`
- PostgreSQL: `localhost:15432`, 用户名/密码: `postgres/postgres`

## 代码更新与重新构建

**重要**: 本项目的 Dockerfile 使用本地代码构建，当你修改本地代码后，需要重新构建镜像才能生效。

### 重新构建并启动所有服务

```bash
cd docker-file

docker-compose build
docker-compose up -d
```

### 仅重新构建某个服务

```bash
cd docker-file

# 重新构建后端服务
docker-compose build backend
docker-compose up -d backend

# 重新构建前端服务
docker-compose build frontend
docker-compose up -d frontend
```

### 快速重启（不重新构建）

如果只是修改了配置文件，不需要重新构建镜像：

```bash
docker-compose restart backend
docker-compose restart frontend
```

## 常用命令

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend
```

### 进入容器

```bash
# 进入后端容器
docker-compose exec backend bash

# 进入前端容器
docker-compose exec frontend sh
```

### 清理

```bash
# 停止并删除所有容器、网络
docker-compose down

# 删除构建的镜像
docker rmi saa-data-agent-backend:1.0.0-SNAPSHOT
docker rmi saa-data-agent-frontend:1.0.0-SNAPSHOT

# 清理未使用的镜像和容器
docker system prune -a
```

## 配置说明

### 后端环境变量

可以通过修改 `docker-compose.yml` 中的环境变量来配置后端服务：

```yaml
environment:
  - NL2SQL_DATASOURCE_URL=jdbc:mysql://mysql:3306/nl2sql_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
  - NL2SQL_DATASOURCE_USERNAME=root
  - NL2SQL_DATASOURCE_PASSWORD=root
  - AI_DASHSCOPE_API_KEY=${AI_DASHSCOPE_API_KEY}
```

### 自定义配置文件

后端支持外部配置文件，可以挂载自定义的 `application.yml`：

```yaml
backend:
  volumes:
    - ./config/application.yml:/app/config/application.yml
```

### Nginx 配置

前端的 Nginx 配置文件位于 `config/nginx.conf`，修改后需要重启前端服务：

```bash
docker-compose restart frontend
```

## 网络配置

所有服务运行在同一个 Docker 网络 `data-agent-network` 中，服务间可以通过服务名称互相访问。

## 数据持久化

当前配置默认不持久化 MySQL 数据。如需持久化，可以取消 `docker-compose.yml` 中的卷挂载注释：

```yaml
volumes:
  - nl2sql-mysql-inner-data:/var/lib/mysql
```

## 故障排查

### 服务启动失败

1. 检查端口是否被占用：
   ```bash
   # Windows
   netstat -ano | findstr "3000"
   netstat -ano | findstr "8065"
   
   # Linux/Mac
   lsof -i :3000
   lsof -i :8065
   ```

2. 查看服务日志：
   ```bash
   docker-compose logs backend
   docker-compose logs frontend
   ```

### 数据库连接失败

1. 确保数据库服务健康：
   ```bash
   docker-compose ps
   ```

2. 检查数据库健康检查状态：
   ```bash
   docker inspect data-agent-mysql-inner
   ```

### 构建失败

1. 确保在项目根目录有所需的文件：
   - `pom.xml`
   - `mvnw` 和 `mvnw.cmd`
   - `.mvn` 目录
   - `data-agent-management` 目录
   - `data-agent-frontend` 目录

2. 清理后重新构建：
   ```bash
   docker-compose down
   docker-compose build --no-cache
   docker-compose up -d
   ```

## 开发环境推荐

对于日常开发，推荐使用以下方式：

1. **仅使用 Docker 运行数据源**：
   ```bash
   docker-compose -f docker-compose-datasource.yml up -d
   ```

2. **本地运行前后端服务**（支持热更新）：
   ```bash
   # 后端
   cd data-agent-management
   ./mvnw spring-boot:run
   
   # 前端
   cd data-agent-frontend
   npm run dev
   ```

这样可以获得更好的开发体验，前端支持 Vite 热更新，后端可以使用 Spring Boot DevTools 热重载。

## 生产部署建议

1. 使用环境变量管理敏感信息
2. 启用数据卷持久化
3. 配置资源限制（CPU、内存）
4. 使用反向代理（如 Nginx）统一入口
5. 配置日志收集和监控
6. 定期备份数据库

## 支持

如有问题，请参考项目主文档或提交 Issue。
