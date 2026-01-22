# DataAgent 支持达梦数据库部署说明

## 概述

DataAgent 现已支持使用达梦数据库（DM8）作为元数据存储数据库，与原有的 MySQL 版本并行存在，用户可以根据需要选择使用。

## 部署方式

### 方式一：使用 MySQL（默认）

```bash
cd docker-file
docker-compose up -d
```

### 方式二：使用达梦数据库

```bash
cd docker-file
docker-compose -f docker-compose-dameng.yml up -d
```

## 达梦数据库配置说明

### 1. Docker Compose 配置

达梦版本的 Docker Compose 配置文件：[docker-compose-dameng.yml](./docker-compose-dameng.yml)

关键配置项：
- **镜像**：`dameng/dm8:latest`
- **端口**：5236
- **用户名**：SYSDBA
- **密码**：SYSDBA

### 2. 环境变量

后端服务自动配置了以下环境变量：

```yaml
- DATASOURCE_PLATFORM=dameng              # 数据库平台标识
- DATA_AGENT_DATASOURCE_URL=jdbc:dm://dameng-metadata:5236?...
- DATASOURCE_DRIVER_CLASS=dm.jdbc.driver.DmDriver
- DRUID_VALIDATION_QUERY=SELECT 1 FROM DUAL
- DRUID_TRANSACTION_ISOLATION=2
```

### 3. SQL 初始化脚本

达梦数据库的初始化脚本位于：
- `config/dameng/schema.sql` - 表结构定义
- `config/dameng/data.sql` - 初始数据

## 技术实现要点

### 1. SQL 方言适配

项目实现了 `SqlDialectResolver` 工具类，根据配置的数据库平台自动切换 SQL 函数：

- **当前时间函数**：
  - MySQL: `NOW()`
  - 达梦: `SYSDATE`

- **分页语法**：
  - MySQL: `LIMIT offset, size`
  - 达梦: `LIMIT size OFFSET offset`

### 2. 数据类型映射

| MySQL 类型        | 达梦类型            |
| ----------------- | ------------------- |
| `AUTO_INCREMENT`  | `IDENTITY(1,1)`     |
| `TEXT/MEDIUMTEXT` | `CLOB`              |
| `VARCHAR(n)`      | `VARCHAR(n)` (兼容) |
| `JSON`            | `CLOB`              |

### 3. 触发器实现

达梦数据库使用触发器实现 `update_time` 字段的自动更新：

```sql
CREATE OR REPLACE TRIGGER trg_agent_update_time
BEFORE UPDATE ON agent FOR EACH ROW
BEGIN :NEW.update_time := SYSDATE; END;
/
```

## 验证步骤

### 1. 检查容器状态

```bash
docker ps
```

应该看到以下容器正在运行：
- `data-agent-dameng-metadata` - 达梦数据库
- `audaque-data-agent-backend` - 后端服务
- `audaque-data-agent-frontend` - 前端服务

### 2. 检查数据库连接

```bash
# 进入达梦容器
docker exec -it data-agent-dameng-metadata bash

# 连接数据库
/opt/dmdbms/bin/disql SYSDBA/SYSDBA@localhost:5236

# 查看表
SQL> SELECT TABLE_NAME FROM USER_TABLES;
SQL> EXIT;
```

### 3. 访问应用

打开浏览器访问：http://localhost:3000

## 故障排查

### 1. 达梦容器启动失败

检查日志：
```bash
docker logs data-agent-dameng-metadata
```

### 2. 后端连接数据库失败

检查后端日志：
```bash
docker logs audaque-data-agent-backend
```

常见问题：
- 驱动类名错误：确保使用 `dm.jdbc.driver.DmDriver`
- 连接 URL 格式错误：确保格式为 `jdbc:dm://host:port`

### 3. SQL 执行错误

如果遇到 SQL 语法错误，检查：
- 是否使用了 MySQL 特有的语法（如 `ENGINE=InnoDB`）
- 字段类型是否正确映射（如 `TEXT` → `CLOB`）

## 切换数据库

### 从 MySQL 切换到达梦

1. 停止 MySQL 版本：
   ```bash
   docker-compose down
   ```

2. 启动达梦版本：
   ```bash
   docker-compose -f docker-compose-dameng.yml up -d
   ```

### 从达梦切换回 MySQL

1. 停止达梦版本：
   ```bash
   docker-compose -f docker-compose-dameng.yml down
   ```

2. 启动 MySQL 版本：
   ```bash
   docker-compose up -d
   ```

## 开发环境配置

如果需要在本地开发环境使用达梦数据库，修改 `application.yml`：

```yaml
spring:
  datasource:
    platform: dameng
    url: jdbc:dm://localhost:5236
    username: SYSDBA
    password: SYSDBA
    driver-class-name: dm.jdbc.driver.DmDriver
    druid:
      validation-query: SELECT 1 FROM DUAL
      default-transaction-isolation: 2
```

## 注意事项

1. **数据不兼容**：MySQL 和达梦数据库的数据不能直接互通，切换数据库后会是全新的环境
2. **驱动依赖**：确保 Maven 仓库包含达梦 JDBC 驱动（`DmJdbcDriver18-8.1.3.140`）
3. **端口占用**：达梦数据库使用 5236 端口，确保该端口未被占用
4. **性能差异**：达梦数据库的性能特征与 MySQL 不同，建议进行压力测试

## 相关文档

- [达梦数据库迁移技术方案](../qoder_agent/dm_metadata_rq.md)
- [Docker 部署文档](./README.md)
- [达梦数据库官方文档](https://eco.dameng.com/document/dm/zh-cn/start/index.html)
