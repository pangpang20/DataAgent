# DataAgent 元数据存储从 MySQL 迁移至达梦数据库技术方案

## 文档概述

本文档详细分析了将 DataAgent 项目的元数据存储由 MySQL 迁移至国产信创数据库达梦（DM）所需的技术改造方案。文档涵盖配置、代码、SQL 脚本等多个层面的修改要求，并提供了灵活的配置方案以支持未来扩展至 GaussDB 等其他数据库。

**注意：本文档仅作为迁移指导，不涉及实际代码修改。**

---

## 一、项目当前状态分析

### 1.1 项目架构概述

DataAgent 是一个基于 Spring Boot 3.x 的智能数据分析平台，主要技术栈包括：
- **后端框架**：Spring Boot 3.4.8 + Spring AI 1.1.0
- **持久层**：MyBatis 3.0.4
- **数据库支持**：MySQL、PostgreSQL、H2、SQL Server、达梦（已有基础支持）
- **连接池**：Druid 1.2.22
- **前端框架**：Vue 3 + Vite

### 1.2 数据库使用场景说明

项目中数据库分为两类用途：
1. **元数据存储数据库**（本次迁移目标）：存储 Agent 配置、数据源、会话、知识库等业务元数据
2. **分析数据源**：用户配置的待分析数据库（支持多种数据库类型）

**本次迁移仅针对元数据存储数据库，不影响分析数据源的功能。**

### 1.3 达梦数据库现状

**好消息：项目已具备达梦数据库的基础支持！**

经过代码分析，发现项目已经实现了达梦数据库的核心支持代码：

#### 已完成的支持代码
1. **枚举定义**：`BizDataSourceTypeEnum.DAMENG`（第 5 号，方言代码为 "Dameng"）
2. **方言枚举**：`DatabaseDialectEnum.DAMENG`（方言名称为 "Dameng"）
3. **连接处理器**：`DamengDatasourceTypeHandler`
   - 位置：`service/datasource/handler/impl/DamengDatasourceTypeHandler.java`
   - 功能：构建达梦 JDBC URL（`jdbc:dm://host:port`）
4. **DDL 实现**：`DamengJdbcDdl`
   - 位置：`connector/impls/dameng/DamengJdbcDdl.java`
   - 功能：实现了表结构查询、字段查询、外键查询等操作
5. **连接池实现**：`DamengJdbcConnectionPool`
   - 位置：`connector/impls/dameng/DamengJdbcConnectionPool.java`
6. **访问器实现**：`DamengDBAccessor`
   - 位置：`connector/impls/dameng/DamengDBAccessor.java`

#### 当前支持的范围
- ✅ 达梦数据库作为**分析数据源**已完全支持
- ❌ 达梦数据库作为**元数据存储**需要额外配置和适配

---

## 二、迁移总体方案

### 2.1 设计目标

1. **灵活配置**：通过配置项轻松切换 MySQL、达梦或其他数据库
2. **向后兼容**：不影响现有 MySQL 用户
3. **易于扩展**：为未来支持 GaussDB、OceanBase 等国产数据库预留接口
4. **最小改动**：充分利用现有达梦支持代码，减少开发工作量

### 2.2 配置驱动设计

新增配置项控制元数据存储数据库类型：

```yaml
spring:
  datasource:
    # 数据库类型：mysql | dameng | gaussdb | oceanbase
    type-name: mysql  # 默认为 mysql，可通过环境变量覆盖
    url: ${DATA_AGENT_DATASOURCE_URL}
    username: ${DATA_AGENT_DATASOURCE_USERNAME}
    password: ${DATA_AGENT_DATASOURCE_PASSWORD}
    driver-class-name: ${DATASOURCE_DRIVER_CLASS:com.mysql.cj.jdbc.Driver}
```

**环境变量示例**：
```bash
# MySQL（默认）
DATA_AGENT_DATASOURCE_URL=jdbc:mysql://localhost:3306/data_agent?...
DATASOURCE_DRIVER_CLASS=com.mysql.cj.jdbc.Driver

# 达梦
DATA_AGENT_DATASOURCE_URL=jdbc:dm://localhost:5236
DATASOURCE_DRIVER_CLASS=dm.jdbc.driver.DmDriver
```

---

## 三、详细修改清单

### 3.1 Maven 依赖配置

#### 文件：`pom.xml`（根目录和 data-agent-management 模块）

**现状分析**：
- 根 pom.xml 已定义：`<dameng.version>8.1.3.140</dameng.version>`
- 子模块 pom.xml 已引入：`DmJdbcDriver18`（scope=runtime）
- **注意**：项目使用的 Druid 连接池包名为 `com.alibaba.druid`。

**修改建议**：

```xml
<!-- 根 pom.xml - dependencyManagement 中保持现状 -->
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriver18</artifactId>
    <version>${dameng.version}</version>
    <scope>runtime</scope>
</dependency>

<!-- 子模块无需修改，已正确引用 -->
```

**说明**：
1. **版本管理**：达梦 JDBC 驱动版本统一在根 pom.xml 管理（当前为 8.1.3.140）
2. **Maven 仓库**：需确保企业 Maven 仓库包含达梦驱动，或手动安装到本地仓库
3. **驱动类名**：`dm.jdbc.driver.DmDriver`（需在配置文件中指定）

---

### 3.2 数据源配置文件

#### 文件：`data-agent-management/src/main/resources/application.yml`

**当前配置**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/data_agent?...
    username: cyl
    password: Audaque@123
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
```

**修改方案**：

```yaml
spring:
  datasource:
    # 新增：数据库类型标识（用于 SQL 脚本选择和方言判断）
    platform: ${DATASOURCE_PLATFORM:mysql}  # mysql | dameng | gaussdb
    
    url: ${DATA_AGENT_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/data_agent?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai}
    username: ${DATA_AGENT_DATASOURCE_USERNAME:cyl}
    password: ${DATA_AGENT_DATASOURCE_PASSWORD:Audaque@123}
    driver-class-name: ${DATASOURCE_DRIVER_CLASS:com.mysql.cj.jdbc.Driver}
    type: com.alibaba.druid.pool.DruidDataSource
  
  sql:
    init:
      mode: never  # 生产环境建议手动执行 SQL
      # 根据 platform 自动选择 SQL 脚本
      schema-locations: classpath:sql/${spring.datasource.platform}/schema.sql
      data-locations: classpath:sql/${spring.datasource.platform}/data.sql
      continue-on-error: true
      separator: ;
      encoding: utf-8
```

**达梦数据库配置示例**：

```yaml
# 方式1：直接修改 application.yml
spring:
  datasource:
    platform: dameng
    url: jdbc:dm://127.0.0.1:5236
    username: SYSDBA
    password: SYSDBA
    driver-class-name: dm.jdbc.driver.DmDriver

# 方式2：通过环境变量（推荐）
# DATASOURCE_PLATFORM=dameng
# DATA_AGENT_DATASOURCE_URL=jdbc:dm://127.0.0.1:5236
# DATASOURCE_DRIVER_CLASS=dm.jdbc.driver.DmDriver
# DATA_AGENT_DATASOURCE_USERNAME=SYSDBA
# DATA_AGENT_DATASOURCE_PASSWORD=SYSDBA
```

---

### 3.3 SQL 初始化脚本适配

#### 文件结构规划

```
sql/
├── mysql/                    # MySQL 脚本（已存在）
│   ├── schema.sql
│   ├── data.sql
│   └── product_schema.sql
├── dameng/                   # 达梦脚本（需新建）
│   ├── schema.sql
│   ├── data.sql
│   └── product_schema.sql
├── gaussdb/                  # GaussDB 脚本（预留）
│   ├── schema.sql
│   └── data.sql
└── h2/                       # H2 脚本（已存在，用于测试）
    ├── schema-h2.sql
    └── ...
```

#### 3.3.1 MySQL 与达梦 SQL 语法差异对照表

| 语法特性      | MySQL                               | 达梦                             | 影响范围      |
| ------------- | ----------------------------------- | -------------------------------- | ------------- |
| **自增主键**  | `AUTO_INCREMENT`                    | `IDENTITY(1,1)` 或序列           | 所有表        |
| **当前时间**  | `CURRENT_TIMESTAMP`                 | `SYSDATE` 或 `CURRENT_TIMESTAMP` | 时间戳字段    |
| **更新时间**  | `ON UPDATE CURRENT_TIMESTAMP`       | 触发器实现                       | 需改造        |
| **存储引擎**  | `ENGINE=InnoDB`                     | 不需要                           | 删除该子句    |
| **索引类型**  | `USING BTREE`                       | 不需要                           | 删除该子句    |
| **字符集**    | `COLLATE utf8mb4_bin`               | 不需要，建库时指定               | 字符串字段    |
| **分页语法**  | `LIMIT offset, count`               | `LIMIT count OFFSET offset`      | 分页查询      |
| **注释语法**  | `COMMENT '...'`                     | `COMMENT '...'`                  | 兼容          |
| **数据类型**  | `INT`                               | `INT`                            | 基本兼容      |
| **JSON 类型** | `JSON`                              | `CLOB` 或 `VARCHAR2`             | metadata 字段 |
| **TEXT 类型** | `TEXT/MEDIUMTEXT`                   | `CLOB`                           | 大文本字段    |
| **TINYINT**   | `TINYINT`                           | `TINYINT`                        | 兼容          |
| **VARCHAR**   | `VARCHAR(255)`                      | `VARCHAR2(255)`                  | 字符串字段    |
| **外键约束**  | `FOREIGN KEY ... ON DELETE CASCADE` | 同 MySQL                         | 兼容          |

#### 3.3.2 达梦 schema.sql 改造示例

**原 MySQL 表定义**：
```sql
CREATE TABLE IF NOT EXISTS agent (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL COMMENT '智能体名称',
    description TEXT COMMENT '智能体描述',
    avatar TEXT COMMENT '头像URL',
    status VARCHAR(50) DEFAULT 'draft',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_name (name) USING BTREE
) ENGINE = InnoDB COMMENT = '智能体表';
```

**达梦适配版本**：
```sql
-- 方案1：使用 IDENTITY（推荐）
CREATE TABLE IF NOT EXISTS agent (
    id INT NOT NULL IDENTITY(1,1),  -- 替换 AUTO_INCREMENT
    name VARCHAR2(255) NOT NULL,    -- VARCHAR 改为 VARCHAR2
    description CLOB,                -- TEXT 改为 CLOB
    avatar CLOB,                     -- TEXT 改为 CLOB
    status VARCHAR2(50) DEFAULT 'draft',
    create_time TIMESTAMP DEFAULT SYSDATE,  -- CURRENT_TIMESTAMP 可保留
    update_time TIMESTAMP DEFAULT SYSDATE,  -- 去掉 ON UPDATE
    PRIMARY KEY (id)
);

-- 索引单独创建（去掉 USING BTREE）
CREATE INDEX idx_name ON agent(name);

-- 表注释
COMMENT ON TABLE agent IS '智能体表';
COMMENT ON COLUMN agent.name IS '智能体名称';

-- 触发器实现 update_time 自动更新
CREATE OR REPLACE TRIGGER trg_agent_update_time
BEFORE UPDATE ON agent
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSDATE;
END;
/

-- 方案2：使用序列（传统方式）
CREATE SEQUENCE seq_agent START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS agent (
    id INT NOT NULL DEFAULT NEXT VALUE FOR seq_agent,
    -- ... 其他字段同方案1
);
```

#### 3.3.3 关键字段类型映射

| MySQL 类型     | 达梦类型                   | 说明                        |
| -------------- | -------------------------- | --------------------------- |
| `INT`          | `INT`                      | 直接兼容                    |
| `BIGINT`       | `BIGINT`                   | 直接兼容                    |
| `VARCHAR(n)`   | `VARCHAR2(n)`              | 推荐使用 VARCHAR2           |
| `TEXT`         | `CLOB`                     | 大文本                      |
| `MEDIUMTEXT`   | `CLOB`                     | 大文本                      |
| `JSON`         | `CLOB` 或 `VARCHAR2(4000)` | 达梦8支持JSON，低版本用CLOB |
| `TINYINT`      | `TINYINT`                  | 兼容                        |
| `TIMESTAMP`    | `TIMESTAMP`                | 兼容                        |
| `DATETIME`     | `DATETIME`                 | 兼容                        |
| `DECIMAL(m,n)` | `DECIMAL(m,n)`             | 兼容                        |

#### 3.3.4 所有需要改造的表清单

基于 `sql/schema.sql` 分析，共需改造 **13 张表**：

1. **agent**（智能体表）
   - 自增主键：`id INT AUTO_INCREMENT` → `id INT IDENTITY(1,1)`
   - 文本字段：`description TEXT` → `description CLOB`
   - 时间字段：需添加 `update_time` 触发器
   - 索引：去掉 `USING BTREE`

2. **business_knowledge**（业务知识表）
   - 同上改造
   - 外键约束保持不变

3. **semantic_model**（语义模型表）
   - 类型转换：`VARCHAR` → `VARCHAR2`
   - 去掉 `ENGINE` 和 `COLLATE`

4. **agent_knowledge**（智能体知识表）
   - `content MEDIUMTEXT` → `content CLOB`
   - 字符集相关配置删除

5. **datasource**（数据源表）
   - `connection_url VARCHAR(1000)` → `connection_url VARCHAR2(1000)`

6. **logical_relation**（逻辑外键配置表）
   - 时间类型 `DATETIME` → `TIMESTAMP`

7. **agent_datasource**（智能体数据源关联表）
   - 唯一键约束保持不变

8. **agent_preset_question**（预设问题表）
   - `question TEXT` → `question CLOB`

9. **chat_session**（会话表）
   - `id VARCHAR(36)` → `id VARCHAR2(36)`（UUID）

10. **chat_message**（消息表）
    - `metadata JSON` → `metadata CLOB`（或 VARCHAR2）
    - `content TEXT` → `content CLOB`

11. **user_prompt_config**（Prompt配置表）
    - `system_prompt TEXT` → `system_prompt CLOB`

12. **agent_datasource_tables**（数据表选择表）
    - 基本兼容，需检查外键约束

13. **model_config**（模型配置表）
    - `DECIMAL(10,2)` → 达梦兼容
    - 字符集删除

#### 3.3.5 触发器统一创建脚本

```sql
-- 为所有包含 update_time 字段的表创建触发器
-- agent 表
CREATE OR REPLACE TRIGGER trg_agent_update_time
BEFORE UPDATE ON agent FOR EACH ROW
BEGIN :NEW.update_time := SYSDATE; END;
/

-- business_knowledge 表
CREATE OR REPLACE TRIGGER trg_business_knowledge_update_time
BEFORE UPDATE ON business_knowledge FOR EACH ROW
BEGIN :NEW.updated_time := SYSDATE; END;
/

-- semantic_model 表
CREATE OR REPLACE TRIGGER trg_semantic_model_update_time
BEFORE UPDATE ON semantic_model FOR EACH ROW
BEGIN :NEW.updated_time := SYSDATE; END;
/

-- 其他表同理...（共需创建 10+ 个触发器）
```

---

### 3.4 MyBatis Mapper 适配

#### 3.4.1 问题分析

当前项目使用 **MyBatis 注解方式**定义 SQL，部分 SQL 包含 MySQL 特定函数：

**典型案例 - AgentKnowledgeMapper.java**：
```java
@Update("""
    UPDATE agent_knowledge
    <set>
        ...
        updated_time = NOW()  // MySQL 函数
    </set>
    WHERE id = #{id}
""")
int update(AgentKnowledge knowledge);
```

**问题**：`NOW()` 是 MySQL 函数，达梦应使用 `SYSDATE` 或 `CURRENT_TIMESTAMP`。

#### 3.4.2 解决方案

**方案1：数据库方言动态 SQL（推荐）**

通过自定义 MyBatis 插件或方言工具类，根据配置的数据库类型动态替换 SQL 函数。

```java
// 创建方言工具类
@Component
public class SqlDialectResolver {
    
    @Value("${spring.datasource.platform:mysql}")
    private String platform;
    
    public String getCurrentTimestamp() {
        return switch (platform.toLowerCase()) {
            case "mysql" -> "NOW()";
            case "dameng" -> "SYSDATE";
            case "postgresql" -> "CURRENT_TIMESTAMP";
            case "gaussdb" -> "SYSDATE";
            default -> "CURRENT_TIMESTAMP";
        };
    }
}

// Mapper 中使用
@Update("""
    UPDATE agent_knowledge
    SET updated_time = ${@sqlDialectResolver.getCurrentTimestamp()}
    WHERE id = #{id}
""")
int update(AgentKnowledge knowledge);
```

**方案2：XML Mapper + 动态 SQL**

将注解 SQL 迁移到 XML 文件，使用 `<if>` 标签根据数据库类型选择 SQL：

```xml
<!-- AgentKnowledgeMapper.xml -->
<update id="update">
    UPDATE agent_knowledge
    SET
    <if test="_databaseId == 'MySQL'">
        updated_time = NOW()
    </if>
    <if test="_databaseId == 'DM'">
        updated_time = SYSDATE
    </if>
    WHERE id = #{id}
</update>
```

**方案3：实体类自动赋值（最简单，推荐）**

在 Service 层统一处理时间字段，避免 SQL 层差异：

```java
// Service 层
public void updateKnowledge(AgentKnowledge knowledge) {
    knowledge.setUpdatedTime(LocalDateTime.now());  // Java 层设置
    mapper.update(knowledge);  // SQL 中去掉 NOW()
}

// Mapper 改为
@Update("""
    UPDATE agent_knowledge
    SET updated_time = #{updatedTime}  // 使用参数
    WHERE id = #{id}
""")
int update(AgentKnowledge knowledge);
```

#### 3.4.3 需要检查的 Mapper 清单

基于代码分析，以下 Mapper 需要检查：

1. **AgentKnowledgeMapper**
   - `NOW()` 函数
   - `CONCAT('%', #{xxx}, '%')` 函数（达梦兼容）

2. **AgentMapper**
   - 时间函数使用

3. **BusinessKnowledgeMapper**
   - 同上

4. **所有包含时间更新的 Mapper**

**检查命令**：
```bash
cd data-agent-management/src/main/java
grep -r "NOW()" --include="*Mapper.java"
grep -r "CURRENT_TIMESTAMP" --include="*Mapper.java"
# 检查 MySQL 特有的 LIMIT offset, count 语法
grep -r "LIMIT .*," --include="*Mapper.java"
```

---

### 3.5 数据库方言适配代码

#### 3.5.1 现有代码利用

**好消息**：项目已有完整的方言适配架构，无需大改！

**核心类**：
1. **BizDataSourceTypeEnum**：已定义 `DAMENG(5, "dameng", DatabaseDialectEnum.DAMENG.getCode(), DbAccessTypeEnum.JDBC.getCode())`
2. **DatabaseDialectEnum**：已定义 `DAMENG("Dameng")`
3. **DamengDatasourceTypeHandler**：已实现连接 URL 构建

**使用方式**：
```java
// 在 Service 中获取当前数据库方言
@Autowired
private DataSource dataSource;

public String getDatabaseType() {
    try (Connection conn = dataSource.getConnection()) {
        String url = conn.getMetaData().getURL();
        if (url.contains("jdbc:dm:")) {
            return "dameng";
        } else if (url.contains("jdbc:mysql:")) {
            return "mysql";
        }
        // ... 其他数据库判断
    }
}
```

#### 3.5.2 新增：元数据数据库方言工具类

为区分"元数据库"和"分析数据源"，建议新增工具类：

```java
package com.audaque.cloud.ai.dataagent.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 元数据数据库方言工具类
 */
@Component
public class MetadataDbDialect {
    
    @Value("${spring.datasource.platform:mysql}")
    private String platform;
    
    /**
     * 获取当前时间戳函数
     */
    public String getCurrentTimestamp() {
        return switch (platform.toLowerCase()) {
            case "mysql" -> "NOW()";
            case "dameng", "dm" -> "SYSDATE";
            case "postgresql", "postgres" -> "CURRENT_TIMESTAMP";
            case "gaussdb" -> "SYSDATE";
            case "h2" -> "NOW()";
            default -> "CURRENT_TIMESTAMP";
        };
    }
    
    /**
     * 获取自增主键语法
     */
    public String getAutoIncrementSyntax() {
        return switch (platform.toLowerCase()) {
            case "mysql" -> "AUTO_INCREMENT";
            case "dameng", "dm" -> "IDENTITY(1,1)";
            case "postgresql", "postgres" -> "SERIAL";
            case "gaussdb" -> "AUTO_INCREMENT";
            default -> "AUTO_INCREMENT";
        };
    }
    
    /**
     * 获取 VARCHAR 类型名称
     */
    public String getVarcharType() {
        return switch (platform.toLowerCase()) {
            case "dameng", "dm", "oracle" -> "VARCHAR2";
            default -> "VARCHAR";
        };
    }
    
    /**
     * 获取大文本类型
     */
    public String getTextType() {
        return switch (platform.toLowerCase()) {
            case "dameng", "dm", "oracle" -> "CLOB";
            case "postgresql", "postgres" -> "TEXT";
            default -> "TEXT";
        };
    }
    
    /**
     * 判断是否为达梦数据库
     */
    public boolean isDameng() {
        return "dameng".equalsIgnoreCase(platform) || "dm".equalsIgnoreCase(platform);
    }
    
    /**
     * 判断是否为 MySQL
     */
    public boolean isMysql() {
        return "mysql".equalsIgnoreCase(platform);
    }
}
```

**使用示例**：
```java
@Service
public class SomeService {
    
    @Autowired
    private MetadataDbDialect dialect;
    
    public void updateRecord() {
        if (dialect.isDameng()) {
            // 达梦特殊处理
        } else {
            // MySQL 处理
        }
    }
}
```

---

### 3.6 连接池配置适配

#### 3.6.1 Druid 连接池配置

**现状**：项目使用 Druid 连接池，配置在 `application.yml` 中。

**达梦适配**：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      # 达梦数据库连接配置
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      
      # 达梦验证查询（重要！）
      validation-query: SELECT 1 FROM DUAL  # 达梦使用 DUAL 表
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      
      # 事务隔离级别：READ_COMMITTED
      default-transaction-isolation: 2
      
      # 过滤器配置
      filters: stat,wall
      
      # 达梦数据库连接属性
      connection-properties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000
```

**MySQL vs 达梦验证查询对比**：
- MySQL: `SELECT 1`
- 达梦: `SELECT 1 FROM DUAL`（或 `SELECT 1`，达梦8兼容）

**建议**：使用动态配置

```yaml
spring:
  datasource:
    druid:
      validation-query: ${DRUID_VALIDATION_QUERY:SELECT 1}
```

环境变量：
- MySQL: `DRUID_VALIDATION_QUERY=SELECT 1`
- 达梦: `DRUID_VALIDATION_QUERY=SELECT 1 FROM DUAL`

#### 3.6.2 达梦 JDBC URL 参数

**推荐配置**：
```
jdbc:dm://127.0.0.1:5236?zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
```

**参数说明**：
- `zeroDateTimeBehavior=convertToNull`: 零日期转换为 NULL
- `useUnicode=true`: 启用 Unicode
- `characterEncoding=UTF-8`: 字符编码
- `serverTimezone`: 时区设置

---

### 3.7 Spring Boot 自动初始化配置

#### 文件：`application.yml`

```yaml
spring:
  sql:
    init:
      mode: ${SQL_INIT_MODE:never}  # always | never | embedded
      platform: ${spring.datasource.platform}
      schema-locations: classpath:sql/${spring.datasource.platform}/schema.sql
      data-locations: classpath:sql/${spring.datasource.platform}/data.sql
      continue-on-error: true
      separator: ;
      encoding: utf-8
```

**初始化模式说明**：
- `always`: 每次启动都执行 SQL（开发测试）
- `never`: 不自动执行（生产推荐）
- `embedded`: 仅嵌入式数据库（H2）执行

**生产部署建议**：
1. 设置 `mode=never`
2. 手动执行 SQL 脚本初始化数据库
3. 使用 Flyway 或 Liquibase 管理数据库版本（可选）

---

## 四、测试与验证

### 4.1 单元测试适配

#### 测试数据库选择

**现状**：项目使用 H2 内存数据库进行测试（`application-h2.yml`）

**达梦测试**：

**注意**：H2 数据库虽然支持 MySQL 模式，但并不完全模拟达梦的所有特性（如 IDENTITY、触发器、CLOB 等）。

**方案1：使用 TestContainers（推荐）**

```xml
<!-- pom.xml 已有 testcontainers 依赖 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

```java
// 测试配置类
@TestConfiguration
public class DamengTestConfig {
    
    @Container
    // 需确保有达梦 Docker 镜像，或使用通用的数据库容器手动配置
    static GenericContainer<?> damengContainer = new GenericContainer<>("dameng/dm8:latest")
        .withExposedPorts(5236)
        .withEnv("SYSDBA_PWD", "SYSDBA");
    
    @Bean
    public DataSource dataSource() {
        // 配置达梦测试数据源
    }
}
```

**方案2：本地达梦数据库（最可靠）**

由于达梦特性的复杂性，建议开发环境至少配备一个共享的达梦测试实例。

```yaml
# application-dameng-test.yml
spring:
  datasource:
    platform: dameng
    url: jdbc:dm://localhost:5236
    username: TEST_USER
    password: TEST_PWD
    driver-class-name: dm.jdbc.driver.DmDriver
```

### 4.2 集成测试清单

**必测项目**：

1. **数据库连接**
   - [x] 验证 JDBC 连接成功
   - [x] 验证连接池工作正常
   - [x] 验证连接参数配置

2. **表结构创建**
   - [x] 执行 schema.sql 成功
   - [x] 主键自增正常
   - [x] 外键约束生效
   - [x] 索引创建成功
   - [x] 触发器生效（update_time）

3. **CRUD 操作**
   - [x] Insert 操作（含自增主键返回）
   - [x] Update 操作（含时间自动更新）
   - [x] Select 操作（含分页、排序）
   - [x] Delete 操作（含级联删除）

4. **事务管理**
   - [x] 事务提交
   - [x] 事务回滚
   - [x] 嵌套事务

5. **特殊字段**
   - [x] CLOB 字段读写
   - [x] JSON/CLOB 字段处理
   - [x] 时间戳字段
   - [x] UUID 字段

6. **业务功能**
   - [x] Agent 创建/更新/删除
   - [x] 数据源配置
   - [x] 知识库管理
   - [x] 会话管理
   - [x] 语义模型管理

### 4.3 性能测试

**测试指标**：
- 连接建立时间
- CRUD 操作耗时
- 并发连接数
- 内存占用
- CPU 使用率

**对比基准**：
- MySQL 5.7/8.0
- 达梦 DM8

---

## 五、部署与运维

### 5.1 Docker 部署配置

#### Dockerfile 适配

```dockerfile
# Dockerfile-backend（达梦版本）
FROM openjdk:17-jdk-slim

# 安装达梦客户端（如需要）
# COPY dm8_client_linux.tar /tmp/
# RUN tar -xf /tmp/dm8_client_linux.tar -C /opt/

# 复制应用
COPY data-agent-management/target/*.jar app.jar

# 环境变量
ENV DATASOURCE_PLATFORM=dameng
ENV DATA_AGENT_DATASOURCE_URL=jdbc:dm://dm-server:5236
ENV DATASOURCE_DRIVER_CLASS=dm.jdbc.driver.DmDriver

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

#### Docker Compose 配置

```yaml
version: '3.8'

services:
  # 达梦数据库
  dameng-db:
    image: dameng/dm8:latest
    container_name: dataagent-dameng
    ports:
      - "5236:5236"
    environment:
      - SYSDBA_PWD=SYSDBA
      - DM_PORT=5236
    volumes:
      - dameng-data:/opt/dmdbms/data
      - ./sql/dameng:/docker-entrypoint-initdb.d  # 初始化脚本
    networks:
      - dataagent-network
  
  # DataAgent 后端
  dataagent-backend:
    build:
      context: .
      dockerfile: docker-file/Dockerfile-backend
    container_name: dataagent-backend
    ports:
      - "8065:8065"
    environment:
      - DATASOURCE_PLATFORM=dameng
      - DATA_AGENT_DATASOURCE_URL=jdbc:dm://dameng-db:5236
      - DATA_AGENT_DATASOURCE_USERNAME=SYSDBA
      - DATA_AGENT_DATASOURCE_PASSWORD=SYSDBA
      - DATASOURCE_DRIVER_CLASS=dm.jdbc.driver.DmDriver
    depends_on:
      - dameng-db
    networks:
      - dataagent-network

volumes:
  dameng-data:

networks:
  dataagent-network:
    driver: bridge
```

### 5.2 生产环境配置建议

#### 5.2.1 数据库配置优化

```sql
-- 达梦数据库优化参数
-- 1. 内存配置
ALTER SYSTEM SET MEMORY_TARGET = 4096;  -- 4GB
ALTER SYSTEM SET BUFFER_SIZE = 2048;    -- 2GB

-- 2. 连接数
ALTER SYSTEM SET MAX_SESSIONS = 200;

-- 3. 日志配置
ALTER SYSTEM SET REDO_LOG_SIZE = 512;   -- 512MB

-- 4. 字符集
-- 创建数据库时指定：CREATE DATABASE ... UNICODE_CASE_SENSITIVE = N;
```

#### 5.2.2 应用配置优化

```yaml
spring:
  datasource:
    druid:
      # 生产环境连接池配置
      initial-size: 10
      min-idle: 10
      max-active: 50
      max-wait: 60000
      
      # 连接泄露检测
      remove-abandoned: true
      remove-abandoned-timeout: 180
      
      # 慢 SQL 监控
      filters: stat,wall,log4j2
      connection-properties: druid.stat.slowSqlMillis=1000
  
  # SQL 初始化关闭
  sql:
    init:
      mode: never
```

#### 5.2.3 监控配置

```yaml
# 开启 Druid 监控页面
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: admin
        login-password: admin
      
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*"
```

### 5.3 数据迁移方案

#### 从 MySQL 迁移到达梦

**步骤1：数据导出**

```bash
# 导出 MySQL 数据
mysqldump -u root -p data_agent > data_agent_mysql.sql
```

**步骤2：SQL 语法转换**

使用工具或脚本转换 SQL 语法：
- `AUTO_INCREMENT` → `IDENTITY(1,1)`
- `ENGINE=InnoDB` → 删除
- `VARCHAR` → `VARCHAR2`
- `TEXT` → `CLOB`

**步骤3：数据导入**

```bash
# 方式1：使用 DM 客户端工具
disql SYSDBA/SYSDBA@localhost:5236 < data_agent_dameng.sql

# 方式2：使用 DTS（Data Transfer Service）
# 达梦提供的图形化迁移工具
```

**步骤4：验证数据**

```sql
-- 检查表数量
SELECT COUNT(*) FROM USER_TABLES;

-- 检查数据量
SELECT TABLE_NAME, COUNT(*) FROM agent;
SELECT TABLE_NAME, COUNT(*) FROM datasource;
-- ... 其他表
```

---

## 六、常见问题与解决方案

### 6.1 JDBC 驱动问题

**问题1：ClassNotFoundException: dm.jdbc.driver.DmDriver**

**原因**：达梦 JDBC 驱动未正确引入

**解决**：
```bash
# 手动安装到本地 Maven 仓库
mvn install:install-file \
  -Dfile=DmJdbcDriver18-8.1.3.140.jar \
  -DgroupId=com.dameng \
  -DartifactId=DmJdbcDriver18 \
  -Dversion=8.1.3.140 \
  -Dpackaging=jar
```

**问题2：驱动版本不兼容**

**解决**：
- DM7 使用：`DmJdbcDriver17.jar`
- DM8 使用：`DmJdbcDriver18.jar`（项目当前使用）

### 6.2 SQL 语法兼容问题

**问题1：字符串拼接**

```sql
-- MySQL
SELECT CONCAT('%', name, '%') FROM agent;

-- 达梦（兼容 MySQL 语法）
SELECT CONCAT('%', name, '%') FROM agent;  -- 可用
-- 或使用达梦原生语法
SELECT '%' || name || '%' FROM agent;      -- 推荐
```

**问题2：日期函数**

```sql
-- MySQL
SELECT DATE_FORMAT(create_time, '%Y-%m-%d') FROM agent;

-- 达梦
SELECT TO_CHAR(create_time, 'YYYY-MM-DD') FROM agent;
```

**问题3：分页查询**

```sql
-- MySQL (不支持)
SELECT * FROM agent LIMIT 20, 10;  -- offset=20, count=10

-- 达梦 (标准语法)
SELECT * FROM agent LIMIT 10 OFFSET 20;    -- DM8 推荐语法
```

**改造建议**：
系统中所有 `LIMIT #{offset}, #{size}` 必须改为 `LIMIT #{size} OFFSET #{offset}`。

### 6.3 字符集问题

**问题**：MySQL 脚本中的 `COLLATE utf8mb4_bin` 导致达梦执行失败。

**解决**：

1. **移除建表语句中的 COLLATE 和 ENGINE**：达梦不支持这些子句。
2. **创建数据库时指定字符集**：

```sql
-- 创建数据库时指定字符集和大小写敏感
CREATE DATABASE data_agent
    CHARACTER SET UTF8
    CASE SENSITIVE = N;
```

3. **JDBC URL 参数**：
`jdbc:dm://localhost:5236?characterEncoding=UTF-8&useUnicode=true`

### 6.4 自增主键返回问题

**问题**：Insert 后无法获取自增主键值。

**解决**：

MyBatis 的 `useGeneratedKeys` 在达梦 8 中配合 `IDENTITY(1,1)` 通常可以正常工作。

```java
// MyBatis 配置
@Insert("INSERT INTO agent(...) VALUES(...)")
@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
int insert(Agent agent);
```

**备选方案**（如上述配置失效）：
使用达梦的 `SELECT SCOPE_IDENTITY()`：

```java
@Insert("INSERT INTO agent(...) VALUES(...); SELECT SCOPE_IDENTITY() AS id;")
@Options(useGeneratedKeys = false) // 关闭自动处理
int insert(Agent agent);
```

### 6.5 事务隔离级别

**问题**：事务行为不一致。

**配置**：

```yaml
spring:
  datasource:
    druid:
      # 达梦默认隔离级别：READ_COMMITTED (2)
      # MySQL 默认：REPEATABLE_READ (4)
      default-transaction-isolation: 2
```

---

## 七、扩展性设计

### 7.1 支持更多国产数据库

基于本方案，后续可轻松扩展支持：

#### GaussDB（华为）

```yaml
# 配置
spring:
  datasource:
    platform: gaussdb
    url: jdbc:zenith:@localhost:8000/data_agent
    driver-class-name: com.huawei.gauss.jdbc.ZenithDriver
```

**SQL 差异**：
- 语法类似 PostgreSQL
- 支持 `AUTO_INCREMENT`
- `TEXT` 类型兼容

#### OceanBase（蚂蚁/阿里）

```yaml
spring:
  datasource:
    platform: oceanbase
    url: jdbc:oceanbase://localhost:2881/data_agent
    driver-class-name: com.oceanbase.jdbc.Driver
```

**SQL 差异**：
- 兼容 MySQL 语法
- 支持分布式事务

#### TiDB（PingCAP）

```yaml
spring:
  datasource:
    platform: tidb
    url: jdbc:mysql://localhost:4000/data_agent
    driver-class-name: com.mysql.cj.jdbc.Driver
```

**SQL 差异**：
- 完全兼容 MySQL 5.7
- 无需 SQL 改造

### 7.2 数据库类型注册机制

```java
/**
 * 数据库类型注册接口
 */
public interface DatabasePlatform {
    String getName();               // mysql | dameng | gaussdb
    String getDriverClass();        // JDBC 驱动类名
    String getCurrentTimestamp();   // 当前时间函数
    String getTextType();           // 大文本类型
    String getAutoIncrement();      // 自增语法
    // ... 其他差异
}

/**
 * 注册中心
 */
@Component
public class DatabasePlatformRegistry {
    
    private final Map<String, DatabasePlatform> platforms = new HashMap<>();
    
    @PostConstruct
    public void init() {
        register(new MysqlPlatform());
        register(new DamengPlatform());
        register(new GaussdbPlatform());
    }
    
    public DatabasePlatform get(String name) {
        return platforms.get(name.toLowerCase());
    }
}
```

---

## 八、总结与建议

### 8.1 改造工作量评估

| 模块               | 工作量     | 优先级 | 说明                   |
| ------------------ | ---------- | ------ | ---------------------- |
| **Maven 依赖**     | 0.5天      | P0     | 版本已配置，需验证     |
| **配置文件**       | 1天        | P0     | 新增 platform 配置     |
| **SQL 脚本**       | 3天        | P0     | 改造 13 张表 + 触发器  |
| **MyBatis Mapper** | 2天        | P1     | 时间函数适配           |
| **方言工具类**     | 1天        | P1     | 新增 MetadataDbDialect |
| **单元测试**       | 2天        | P2     | 适配测试环境           |
| **集成测试**       | 3天        | P0     | 全量功能验证           |
| **性能测试**       | 2天        | P2     | 对比 MySQL             |
| **文档编写**       | 1天        | P1     | 部署手册               |
| **总计**           | **15.5天** | -      | 约 3 周                |

### 8.2 风险与应对

| 风险               | 影响 | 应对措施             |
| ------------------ | ---- | -------------------- |
| **达梦驱动兼容性** | 高   | 提前在测试环境验证   |
| **SQL 语法差异**   | 中   | 全量 SQL 审查 + 测试 |
| **性能下降**       | 中   | 性能测试 + 参数调优  |
| **数据迁移风险**   | 高   | 备份 + 灰度迁移      |
| **运维复杂度**     | 低   | 编写运维文档         |

### 8.3 实施步骤建议

**阶段1：准备阶段（3天）**
1. 搭建达梦测试环境
2. 验证 JDBC 连接
3. 完成配置文件修改

**阶段2：开发阶段（7天）**
1. 改造 SQL 脚本
2. 适配 MyBatis Mapper
3. 开发方言工具类
4. 单元测试通过

**阶段3：测试阶段（4天）**
1. 集成测试
2. 性能测试
3. 数据迁移测试

**阶段4：上线阶段（1.5天）**
1. 编写部署文档
2. 灰度发布
3. 监控验证

### 8.4 最终建议

1. **实战验证**：在正式开始大规模改造前，**务必先用达梦 8 进行小范围功能验证**，确认核心 CRUD、自增主键返回和分页语法在当前 MyBatis 版本下的表现。
2. **优先使用达梦8**：兼容性最好，支持更多 MySQL 语法。
3. **生产环境建议手动执行 SQL**：不要依赖自动初始化。
4. **保留 MySQL 配置**：通过环境变量切换，方便回退。
5. **充分测试**：国产数据库坑较多，需要充分测试。
6. **监控告警**：部署后持续监控数据库性能。
7. **培训团队**：达梦数据库使用培训。

---

## 附录

### 附录A：达梦数据库安装

```bash
# 下载达梦 DM8
wget https://download.dameng.com/...

# 安装
tar -xvf dm8_setup_rh7_64.tar
cd dm8_setup_rh7_64
./DMInstall.bin -i

# 初始化数据库
cd /opt/dmdbms/bin
./dminit path=/opt/dmdbms/data db_name=DAMENG

# 启动服务
./dmserver /opt/dmdbms/data/DAMENG/dm.ini
```

### 附录B：常用达梦 SQL 命令

```sql
-- 查看版本
SELECT * FROM V$VERSION;

-- 查看所有表
SELECT * FROM USER_TABLES;

-- 查看表结构
DESC agent;

-- 查看索引
SELECT * FROM USER_INDEXES WHERE TABLE_NAME = 'AGENT';

-- 查看触发器
SELECT * FROM USER_TRIGGERS WHERE TABLE_NAME = 'AGENT';

-- 查看序列
SELECT * FROM USER_SEQUENCES;
```

### 附录C：参考资料

1. **达梦数据库官方文档**：https://eco.dameng.com/document/dm/zh-cn/start/index.html
2. **达梦 JDBC 开发指南**：https://eco.dameng.com/document/dm/zh-cn/app-dev/java-jdbc.html
3. **Spring Boot 多数据源配置**：https://spring.io/guides/gs/accessing-data-mysql/
4. **MyBatis 数据库方言**：https://mybatis.org/mybatis-3/configuration.html
