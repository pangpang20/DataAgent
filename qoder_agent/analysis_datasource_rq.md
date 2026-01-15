# DataAgent 用户分析数据源国产信创数据库支持需求文档

## 文档概述

本文档详细阐述了在 DataAgent 项目的**用户分析数据源**模块中，增加对国产信创数据库（达梦 DAMENG、华为 GaussDB、华为数据仓库 DWS）支持的技术需求与实现方案。项目已具备达梦数据库的基础支持，本次主要完善达梦支持并新增 GaussDB 和 DWS 支持。

**注意：本文档仅作为开发指导，不涉及实际代码修改。**

---

## 一、背景与目标

### 1.1 业务场景说明

DataAgent 支持两类数据库使用场景：

| 场景类型             | 用途                            | 数据库要求       | 当前支持                                            |
| -------------------- | ------------------------------- | ---------------- | --------------------------------------------------- |
| **元数据存储数据库** | 存储 Agent 配置、会话、知识库等 | 单一数据库       | MySQL、PostgreSQL、H2                               |
| **用户分析数据源**   | 用户配置的待分析业务数据库      | 支持多数据库类型 | MySQL、PostgreSQL、H2、SQL Server、**达梦**（部分） |

**本次需求范围**：**用户分析数据源**，支持用户将国产数据库配置为分析数据源。

### 1.2 设计目标

1. **完善达梦支持**：项目已有达梦基础代码，需完善并验证
2. **新增 GaussDB 支持**：华为 GaussDB（兼容 PostgreSQL）
3. **新增 DWS 支持**：华为数据仓库服务（兼容 PostgreSQL）
4. **统一架构**：复用现有数据源管理架构
5. **前端支持**：数据源配置界面增加国产数据库选项
6. **易于扩展**：为未来支持更多国产数据库（如 OceanBase、TiDB）预留接口

### 1.3 项目架构现状分析

**✅ 优秀的插件化架构**

项目采用了优秀的插件化设计，各组件自动注册，扩展新数据库非常简单：

```
核心架构组件：
├── BizDataSourceTypeEnum        # 数据源类型枚举（已包含 DAMENG）
├── DatasourceTypeHandler        # 数据源类型处理器（接口）
├── DatasourceTypeHandlerRegistry # 自动注册所有 Handler
├── DBConnectionPool             # 连接池（接口）
├── DBConnectionPoolFactory      # 自动注册所有连接池
├── Ddl                          # DDL 操作接口
├── DdlFactory                   # 自动注册所有 DDL 实现
├── Accessor                     # 数据访问器（接口）
└── AccessorFactory              # 自动注册所有 Accessor
```

**扩展新数据库只需 4 步**：
1. 在 `BizDataSourceTypeEnum` 添加枚举
2. 实现 `DatasourceTypeHandler` 接口
3. 实现 `DBConnectionPool` 接口
4. 实现 `Ddl` 和 `Accessor` 接口

所有实现类添加 `@Service` 或 `@Component` 注解后会自动注册，无需手动配置！

---

## 二、达梦数据库（DAMENG）支持完善

### 2.1 现状分析

**✅ 已完成的支持**

项目已具备达梦数据库的核心支持代码：

| 组件           | 文件路径                       | 状态     |
| -------------- | ------------------------------ | -------- |
| **枚举定义**   | `BizDataSourceTypeEnum.DAMENG` | ✅ 已完成 |
| **方言枚举**   | `DatabaseDialectEnum.DAMENG`   | ✅ 已完成 |
| **类型处理器** | `DamengDatasourceTypeHandler`  | ✅ 已完成 |
| **连接池**     | `DamengJdbcConnectionPool`     | ✅ 已完成 |
| **DDL 实现**   | `DamengJdbcDdl`                | ✅ 已完成 |
| **访问器**     | `DamengDBAccessor`             | ✅ 已完成 |
| **Maven 依赖** | `DmJdbcDriver18` (8.1.3.140)   | ✅ 已配置 |

**❌ 缺失的支持**

| 缺失项                 | 影响                      | 优先级 |
| ---------------------- | ------------------------- | ------ |
| **前端数据源类型选项** | 用户无法在界面选择达梦    | P0     |
| **JDBC 驱动验证**      | 需确保 Maven 仓库包含驱动 | P0     |
| **连接测试验证**       | 需验证连接功能正常        | P1     |
| **DDL 功能测试**       | 需验证表查询、字段查询等  | P1     |

### 2.2 达梦数据库技术参数

#### JDBC 连接配置

```yaml
# 达梦数据库连接参数
type: dameng
host: 127.0.0.1
port: 5236                    # 默认端口
databaseName: SYSDBA           # Schema 名称
username: SYSDBA
password: SYSDBA
driver: dm.jdbc.driver.DmDriver
connectionUrl: jdbc:dm://127.0.0.1:5236
```

#### 常用 JDBC URL 参数

```
jdbc:dm://host:port?参数1=值1&参数2=值2

常用参数：
- characterEncoding=UTF-8    # 字符编码
- useUnicode=true            # 启用 Unicode
- loginTimeout=30            # 登录超时（秒）
- socketTimeout=0            # Socket 超时
```

#### 数据类型映射

| 达梦类型       | Java 类型       | 说明         |
| -------------- | --------------- | ------------ |
| `INT`          | `Integer`       | 32位整数     |
| `BIGINT`       | `Long`          | 64位整数     |
| `VARCHAR2(n)`  | `String`        | 可变字符串   |
| `CLOB`         | `String`        | 大文本       |
| `TIMESTAMP`    | `LocalDateTime` | 时间戳       |
| `DECIMAL(m,n)` | `BigDecimal`    | 精确小数     |
| `DOUBLE`       | `Double`        | 浮点数       |
| `BLOB`         | `byte[]`        | 二进制大对象 |

### 2.3 需要完善的代码

#### 2.3.1 前端数据源类型选项

**文件**：`data-agent-frontend/src/components/agent/DataSourceConfig.vue`

**当前代码（第 269-270 行）**：
```vue
<el-option key="mysql" label="MySQL" value="mysql" />
<el-option key="postgresql" label="PostgreSQL" value="postgresql" />
```

**修改为**：
```vue
<el-option key="mysql" label="MySQL" value="mysql" />
<el-option key="postgresql" label="PostgreSQL" value="postgresql" />
<el-option key="dameng" label="达梦数据库 (DM)" value="dameng" />
<el-option key="gaussdb" label="华为 GaussDB" value="gaussdb" />
<el-option key="dws" label="华为数据仓库 (DWS)" value="dws" />
```

**说明**：
- 两处需要修改（新增数据源对话框和编辑数据源对话框）
- 位置：第 269-270 行和第 388-389 行
- 添加图标建议：可在 label 中添加国产数据库图标标识

#### 2.3.2 前端默认端口配置

**建议新增**：根据数据库类型自动填充默认端口

```typescript
// 在 DataSourceConfig.vue 的 <script> 部分添加
const defaultPorts = {
  mysql: 3306,
  postgresql: 5432,
  dameng: 5236,
  gaussdb: 8000,      // GaussDB 默认端口
  dws: 8000,          // DWS 默认端口
  sqlserver: 1433,
  h2: 9092
}

// 监听数据库类型变化，自动设置端口
watch(() => editingDatasource.type, (newType) => {
  if (newType && defaultPorts[newType] && !editingDatasource.port) {
    editingDatasource.port = defaultPorts[newType]
  }
})
```

#### 2.3.3 连接测试提示优化

**建议**：不同数据库类型显示不同的连接提示

```vue
<el-alert 
  v-if="editingDatasource.type === 'dameng'"
  title="达梦数据库配置提示"
  type="info"
  :closable="false"
  style="margin-bottom: 15px"
>
  <template #default>
    <ul style="margin: 0; padding-left: 20px">
      <li>默认端口：5236</li>
      <li>默认用户名：SYSDBA</li>
      <li>Schema 名称区分大小写</li>
      <li>JDBC URL 示例：jdbc:dm://127.0.0.1:5236</li>
    </ul>
  </template>
</el-alert>
```

---

## 三、华为 GaussDB 支持

### 3.1 GaussDB 技术参数

#### 3.1.1 基本信息

| 项目          | 值                      |
| ------------- | ----------------------- |
| **产品名称**  | 华为云数据库 GaussDB    |
| **兼容性**    | PostgreSQL 生态         |
| **默认端口**  | 8000                    |
| **JDBC 驱动** | `org.postgresql.Driver` |
| **方言**      | PostgreSQL              |

#### 3.1.2 JDBC 连接配置

```yaml
# GaussDB 连接参数
type: gaussdb
host: 127.0.0.1
port: 8000
databaseName: postgres
username: gaussdb_user
password: ********
driver: org.postgresql.Driver
connectionUrl: jdbc:postgresql://127.0.0.1:8000/postgres?currentSchema=public
```

#### 3.1.3 JDBC URL 参数

```
jdbc:postgresql://host:port/database?参数1=值1&参数2=值2

GaussDB 特殊参数：
- currentSchema=public           # 默认 Schema
- ApplicationName=DataAgent      # 应用标识
- prepareThreshold=5             # 预编译阈值
- stringtype=unspecified         # 字符串类型处理
- ssl=false                      # SSL 连接（生产建议 true）
- tcpKeepAlive=true             # TCP 保活
```

### 3.2 实现方案

#### 3.2.1 枚举定义

**文件**：`com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum`

**新增枚举**：
```java
/**
 * 华为 GaussDB（兼容 PostgreSQL）
 */
GAUSSDB(7, "gaussdb", DatabaseDialectEnum.POSTGRESQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),
```

**说明**：
- GaussDB 兼容 PostgreSQL，使用 PostgreSQL 方言
- 使用 PostgreSQL JDBC 驱动（项目已有依赖）
- 序号 7（DAMENG 是 5，SQL_SERVER 是 6）

#### 3.2.2 类型处理器

**文件**：`com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl.GaussDbDatasourceTypeHandler`

**新建文件**：
```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;

/**
 * 华为 GaussDB 数据源类型处理器
 * 兼容 PostgreSQL 协议
 */
@Component
public class GaussDbDatasourceTypeHandler implements DatasourceTypeHandler {

    @Override
    public String typeName() {
        return BizDataSourceTypeEnum.GAUSSDB.getTypeName();
    }

    @Override
    public String dialectType() {
        // GaussDB 使用 PostgreSQL 方言
        return "postgresql";
    }

    @Override
    public String buildConnectionUrl(Datasource datasource) {
        if (!hasRequiredConnectionFields(datasource)) {
            return datasource.getConnectionUrl();
        }
        
        // GaussDB 连接 URL 格式
        return String.format(
            "jdbc:postgresql://%s:%d/%s?currentSchema=public&ApplicationName=DataAgent",
            datasource.getHost(),
            datasource.getPort(),
            datasource.getDatabaseName()
        );
    }

    @Override
    public String normalizeTestUrl(Datasource datasource, String url) {
        // 添加测试连接所需参数
        String updated = url;
        if (!updated.toLowerCase().contains("connecttimeout=")) {
            updated = appendParam(updated, "connectTimeout", "10");
        }
        if (!updated.toLowerCase().contains("sockettimeout=")) {
            updated = appendParam(updated, "socketTimeout", "30");
        }
        return updated;
    }

    private String appendParam(String url, String key, String value) {
        return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
    }
}
```

**说明**：
- 复用 PostgreSQL JDBC 驱动和方言
- `dialectType()` 返回 "postgresql"
- URL 格式兼容 PostgreSQL
- 添加 GaussDB 推荐的连接参数

#### 3.2.3 连接池实现

**文件**：`com.alibaba.cloud.ai.dataagent.connector.impls.gaussdb.GaussDbJdbcConnectionPool`

**新建文件**：
```java
/*
 * Copyright 2024-2026 the original author or authors.
 */
package com.alibaba.cloud.ai.dataagent.connector.impls.gaussdb;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;

/**
 * 华为 GaussDB JDBC 连接池
 * 使用 PostgreSQL JDBC 驱动
 */
@Service("gaussDbJdbcConnectionPool")
public class GaussDbJdbcConnectionPool extends AbstractDBConnectionPool {

    private static final String DRIVER = "org.postgresql.Driver";

    @Override
    public String getDriver() {
        return DRIVER;
    }

    @Override
    public ErrorCodeEnum errorMapping(String sqlState) {
        // 复用 PostgreSQL 错误码映射
        ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
        if (ret != null && ret != OTHERS) {
            return ret;
        }
        return OTHERS;
    }

    @Override
    public boolean supportedDataSourceType(String type) {
        return BizDataSourceTypeEnum.GAUSSDB.getTypeName().equals(type);
    }

    @Override
    public String getConnectionPoolType() {
        return BizDataSourceTypeEnum.GAUSSDB.getTypeName();
    }
}
```

#### 3.2.4 DDL 和 Accessor 实现

**方案1：直接复用 PostgreSQL 实现（推荐）**

由于 GaussDB 完全兼容 PostgreSQL，可以直接复用现有 `PostgreJdbcDdl` 和 `PostgreDBAccessor`。

**修改**：在 `BizDataSourceTypeEnum` 中指定 GaussDB 使用 PostgreSQL 方言即可。

```java
// 无需新建 GaussDbJdbcDdl 和 GaussDbDBAccessor
// DDL 和 Accessor 工厂会根据方言自动选择 PostgreSQL 实现
```

**方案2：创建独立实现（可选）**

如果 GaussDB 有特殊 DDL 需求，可创建独立实现：

```java
// 文件：GaussDbJdbcDdl.java
@Service
public class GaussDbJdbcDdl extends PostgreJdbcDdl {
    
    @Override
    public BizDataSourceTypeEnum getDataSourceType() {
        return BizDataSourceTypeEnum.GAUSSDB;
    }
    
    // 重写需要特殊处理的方法
    @Override
    public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
        // GaussDB 特殊处理（如有需要）
        return super.showTables(connection, schema, tablePattern);
    }
}

// 文件：GaussDbDBAccessor.java
@Service
public class GaussDbDBAccessor extends PostgreDBAccessor {
    
    protected GaussDbDBAccessor(DdlFactory ddlFactory, DBConnectionPoolFactory poolFactory) {
        super(ddlFactory, poolFactory);
    }
    
    @Override
    public boolean supportedDataSourceType(String type) {
        return BizDataSourceTypeEnum.GAUSSDB.getTypeName().equals(type);
    }
    
    @Override
    public String getAccessorType() {
        return BizDataSourceTypeEnum.GAUSSDB.getProtocol() + "@" + 
               BizDataSourceTypeEnum.GAUSSDB.getDialect();
    }
}
```

**建议**：先采用方案1（复用），后续根据测试结果决定是否需要方案2。

---

## 四、华为数据仓库（DWS）支持

### 4.1 DWS 技术参数

#### 4.1.1 基本信息

| 项目          | 值                           |
| ------------- | ---------------------------- |
| **产品名称**  | 华为云数据仓库服务 DWS       |
| **兼容性**    | PostgreSQL 生态 + 分析型扩展 |
| **默认端口**  | 8000                         |
| **JDBC 驱动** | `org.postgresql.Driver`      |
| **方言**      | PostgreSQL（扩展）           |

#### 4.1.2 JDBC 连接配置

```yaml
# DWS 连接参数
type: dws
host: 127.0.0.1
port: 8000
databaseName: gaussdb
username: dbadmin
password: ********
driver: org.postgresql.Driver
connectionUrl: jdbc:postgresql://127.0.0.1:8000/gaussdb?currentSchema=public
```

#### 4.1.3 JDBC URL 参数

```
jdbc:postgresql://host:port/database?参数1=值1&参数2=值2

DWS 特殊参数：
- currentSchema=public           # 默认 Schema
- ApplicationName=DataAgent      # 应用标识
- prepareThreshold=5             # 预编译阈值
- stringtype=unspecified         # 字符串类型处理
- ssl=true                       # DWS 建议启用 SSL
- sslfactory=org.postgresql.ssl.NonValidatingFactory  # SSL 工厂
- targetServerType=master        # 连接主节点
```

### 4.2 实现方案

#### 4.2.1 枚举定义

**文件**：`com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum`

**新增枚举**：
```java
/**
 * 华为数据仓库服务 DWS（兼容 PostgreSQL）
 */
DWS(8, "dws", DatabaseDialectEnum.POSTGRESQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),
```

#### 4.2.2 类型处理器

**文件**：`com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl.DwsDatasourceTypeHandler`

**新建文件**：
```java
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;

/**
 * 华为 DWS 数据源类型处理器
 * 兼容 PostgreSQL 协议，增加数据仓库特性
 */
@Component
public class DwsDatasourceTypeHandler implements DatasourceTypeHandler {

    @Override
    public String typeName() {
        return BizDataSourceTypeEnum.DWS.getTypeName();
    }

    @Override
    public String dialectType() {
        // DWS 使用 PostgreSQL 方言
        return "postgresql";
    }

    @Override
    public String buildConnectionUrl(Datasource datasource) {
        if (!hasRequiredConnectionFields(datasource)) {
            return datasource.getConnectionUrl();
        }
        
        // DWS 连接 URL 格式（推荐启用 SSL）
        return String.format(
            "jdbc:postgresql://%s:%d/%s?currentSchema=public&ApplicationName=DataAgent&targetServerType=master",
            datasource.getHost(),
            datasource.getPort(),
            datasource.getDatabaseName()
        );
    }

    @Override
    public String normalizeTestUrl(Datasource datasource, String url) {
        String updated = url;
        // DWS 测试连接参数
        if (!updated.toLowerCase().contains("connecttimeout=")) {
            updated = appendParam(updated, "connectTimeout", "10");
        }
        if (!updated.toLowerCase().contains("sockettimeout=")) {
            updated = appendParam(updated, "socketTimeout", "30");
        }
        // DWS 推荐参数
        if (!updated.toLowerCase().contains("targetservertype=")) {
            updated = appendParam(updated, "targetServerType", "master");
        }
        return updated;
    }

    private String appendParam(String url, String key, String value) {
        return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
    }
}
```

#### 4.2.3 连接池实现

**文件**：`com.alibaba.cloud.ai.dataagent.connector.impls.dws.DwsJdbcConnectionPool`

**新建文件**：
```java
package com.alibaba.cloud.ai.dataagent.connector.impls.dws;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;

/**
 * 华为 DWS JDBC 连接池
 * 使用 PostgreSQL JDBC 驱动
 */
@Service("dwsJdbcConnectionPool")
public class DwsJdbcConnectionPool extends AbstractDBConnectionPool {

    private static final String DRIVER = "org.postgresql.Driver";

    @Override
    public String getDriver() {
        return DRIVER;
    }

    @Override
    public ErrorCodeEnum errorMapping(String sqlState) {
        // 复用 PostgreSQL 错误码映射
        ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
        if (ret != null && ret != OTHERS) {
            return ret;
        }
        return OTHERS;
    }

    @Override
    public boolean supportedDataSourceType(String type) {
        return BizDataSourceTypeEnum.DWS.getTypeName().equals(type);
    }

    @Override
    public String getConnectionPoolType() {
        return BizDataSourceTypeEnum.DWS.getTypeName();
    }
}
```

#### 4.2.4 DDL 和 Accessor 实现

**方案**：与 GaussDB 类似，DWS 也兼容 PostgreSQL，建议直接复用 PostgreSQL 实现。

```java
// 无需创建 DwsJdbcDdl 和 DwsDBAccessor
// 通过方言自动路由到 PostgreSQL 实现
```

**如需特殊处理**：参考 GaussDB 的方案2创建独立实现。

### 4.3 DWS 特殊考虑

#### 4.3.1 数据仓库特性

DWS 作为数据仓库，与 OLTP 数据库有以下差异：

| 特性         | OLTP（MySQL/PostgreSQL） | OLAP（DWS）        |
| ------------ | ------------------------ | ------------------ |
| **查询类型** | 简单查询、事务处理       | 复杂分析查询、聚合 |
| **响应时间** | 毫秒级                   | 秒级-分钟级        |
| **并发模型** | 高并发小事务             | 低并发大查询       |
| **索引策略** | B-Tree 索引              | 列存储、分布键     |

**建议**：
1. **查询超时**：DWS 查询可能较慢，建议增加超时时间
2. **分页限制**：大表扫描时注意 LIMIT 限制
3. **连接池配置**：DWS 连接开销较大，适当增加连接池大小

#### 4.3.2 查询优化建议

```java
// 在 DwsJdbcConnectionPool 中可以添加特殊配置
@Override
public Connection getConnection(DbConfigBO config) throws SQLException {
    Connection conn = super.getConnection(config);
    
    // DWS 特殊配置
    Statement stmt = conn.createStatement();
    stmt.execute("SET statement_timeout = '300000'");  // 5分钟超时
    stmt.execute("SET work_mem = '256MB'");            // 增加工作内存
    stmt.close();
    
    return conn;
}
```

---

## 五、前端界面修改详细方案

### 5.1 数据源类型选择器修改

#### 5.1.1 修改位置

**文件**：`data-agent-frontend/src/components/agent/DataSourceConfig.vue`

**需要修改的位置**：
1. **新增数据源对话框**：第 269-270 行（`<el-select>` 组件）
2. **编辑数据源对话框**：第 388-389 行（`<el-select>` 组件）

#### 5.1.2 完整修改代码

**位置1：新增数据源对话框（第 265-272 行）**

**原代码**：
```vue
<el-select v-model="datasource.type" placeholder="请选择数据源类型" size="large">
  <el-option key="mysql" label="MySQL" value="mysql" />
  <el-option key="postgresql" label="PostgreSQL" value="postgresql" />
</el-select>
```

**修改为**：
```vue
<el-select 
  v-model="datasource.type" 
  placeholder="请选择数据源类型" 
  size="large"
  @change="handleDatabaseTypeChange('new')"
>
  <!-- 国际主流数据库 -->
  <el-option-group label="国际主流数据库">
    <el-option key="mysql" label="MySQL" value="mysql">
      <span style="float: left">MySQL</span>
      <span style="float: right; color: #8492a6; font-size: 12px">端口: 3306</span>
    </el-option>
    <el-option key="postgresql" label="PostgreSQL" value="postgresql">
      <span style="float: left">PostgreSQL</span>
      <span style="float: right; color: #8492a6; font-size: 12px">端口: 5432</span>
    </el-option>
    <el-option key="sqlserver" label="SQL Server" value="sqlserver">
      <span style="float: left">SQL Server</span>
      <span style="float: right; color: #8492a6; font-size: 12px">端口: 1433</span>
    </el-option>
  </el-option-group>
  
  <!-- 国产信创数据库 -->
  <el-option-group label="国产信创数据库">
    <el-option key="dameng" label="达梦数据库 (DM)" value="dameng">
      <span style="float: left">
        <el-tag size="small" type="success" effect="plain" style="margin-right: 8px">
          国产
        </el-tag>
        达梦数据库 (DM)
      </span>
      <span style="float: right; color: #8492a6; font-size: 12px">端口: 5236</span>
    </el-option>
    <el-option key="gaussdb" label="华为 GaussDB" value="gaussdb">
      <span style="float: left">
        <el-tag size="small" type="success" effect="plain" style="margin-right: 8px">
          国产
        </el-tag>
        华为 GaussDB
      </span>
      <span style="float: right; color: #8492a6; font-size: 12px">端口: 8000</span>
    </el-option>
    <el-option key="dws" label="华为数据仓库 (DWS)" value="dws">
      <span style="float: left">
        <el-tag size="small" type="success" effect="plain" style="margin-right: 8px">
          国产
        </el-tag>
        华为数据仓库 (DWS)
      </span>
      <span style="float: right; color: #8492a6; font-size: 12px">端口: 8000</span>
    </el-option>
  </el-option-group>
  
  <!-- 测试数据库 -->
  <el-option-group label="测试/开发">
    <el-option key="h2" label="H2 Database" value="h2">
      <span style="float: left">H2 Database</span>
      <span style="float: right; color: #8492a6; font-size: 12px">内存数据库</span>
    </el-option>
  </el-option-group>
</el-select>
```

**位置2：编辑数据源对话框（第 384-391 行）**

使用相同的修改方案，注意修改 `@change` 事件处理函数参数为 `'edit'`。

### 5.2 自动填充默认端口

#### 5.2.1 定义默认端口映射

在 `<script setup>` 部分添加：

```typescript
// 数据库默认端口映射
const defaultPorts: Record<string, number> = {
  mysql: 3306,
  postgresql: 5432,
  dameng: 5236,
  gaussdb: 8000,
  dws: 8000,
  sqlserver: 1433,
  h2: 9092
}

// 数据库类型中文名称
const databaseTypeNames: Record<string, string> = {
  mysql: 'MySQL',
  postgresql: 'PostgreSQL',
  dameng: '达梦数据库',
  gaussdb: '华为 GaussDB',
  dws: '华为数据仓库',
  sqlserver: 'SQL Server',
  h2: 'H2 Database'
}
```

#### 5.2.2 类型变化处理函数

```typescript
/**
 * 处理数据库类型变化
 */
const handleDatabaseTypeChange = (dialogType: 'new' | 'edit') => {
  const ds = dialogType === 'new' ? datasource.value : editingDatasource.value
  
  if (ds.type && defaultPorts[ds.type]) {
    // 自动填充默认端口（仅当端口为空时）
    if (!ds.port || ds.port === 0) {
      ds.port = defaultPorts[ds.type]
    }
    
    // 清空连接URL（让后端自动生成）
    ds.connectionUrl = ''
    
    // 显示提示信息
    ElMessage.info({
      message: `已自动填充 ${databaseTypeNames[ds.type]} 默认端口: ${defaultPorts[ds.type]}`,
      duration: 2000
    })
  }
}
```

### 5.3 数据库连接提示信息

#### 5.3.1 达梦数据库提示

在新增/编辑数据源对话框中，根据选择的数据库类型显示提示信息：

```vue
<!-- 达梦数据库配置提示 -->
<el-alert 
  v-if="datasource.type === 'dameng' || editingDatasource.type === 'dameng'"
  title="达梦数据库配置提示"
  type="info"
  :closable="false"
  style="margin-bottom: 20px"
>
  <template #default>
    <div style="line-height: 1.8">
      <p style="margin: 0 0 8px 0; font-weight: 600">连接配置说明：</p>
      <ul style="margin: 0; padding-left: 20px">
        <li><strong>默认端口：</strong>5236</li>
        <li><strong>默认用户：</strong>SYSDBA / SYSDBA</li>
        <li><strong>Schema：</strong>区分大小写，建议全大写</li>
        <li><strong>JDBC URL：</strong>jdbc:dm://127.0.0.1:5236</li>
        <li><strong>驱动类：</strong>dm.jdbc.driver.DmDriver</li>
      </ul>
      <p style="margin: 12px 0 0 0; padding: 8px; background: #fff3e0; border-radius: 4px">
        <el-icon color="#ff9800"><Warning /></el-icon>
        <span style="color: #ff9800; margin-left: 4px">
          注意：数据库名称（Schema）默认为用户名，如 SYSDBA
        </span>
      </p>
    </div>
  </template>
</el-alert>

<!-- GaussDB 配置提示 -->
<el-alert 
  v-if="datasource.type === 'gaussdb' || editingDatasource.type === 'gaussdb'"
  title="华为 GaussDB 配置提示"
  type="info"
  :closable="false"
  style="margin-bottom: 20px"
>
  <template #default>
    <div style="line-height: 1.8">
      <p style="margin: 0 0 8px 0; font-weight: 600">连接配置说明：</p>
      <ul style="margin: 0; padding-left: 20px">
        <li><strong>默认端口：</strong>8000</li>
        <li><strong>兼容性：</strong>PostgreSQL 生态</li>
        <li><strong>JDBC URL：</strong>jdbc:postgresql://host:8000/dbname</li>
        <li><strong>驱动类：</strong>org.postgresql.Driver</li>
        <li><strong>推荐参数：</strong>currentSchema=public</li>
      </ul>
      <p style="margin: 12px 0 0 0; padding: 8px; background: #e8f5e9; border-radius: 4px">
        <el-icon color="#4caf50"><CircleCheck /></el-icon>
        <span style="color: #4caf50; margin-left: 4px">
          GaussDB 完全兼容 PostgreSQL，可使用相同的 SQL 语法
        </span>
      </p>
    </div>
  </template>
</el-alert>

<!-- DWS 配置提示 -->
<el-alert 
  v-if="datasource.type === 'dws' || editingDatasource.type === 'dws'"
  title="华为数据仓库 DWS 配置提示"
  type="info"
  :closable="false"
  style="margin-bottom: 20px"
>
  <template #default>
    <div style="line-height: 1.8">
      <p style="margin: 0 0 8px 0; font-weight: 600">数据仓库配置说明：</p>
      <ul style="margin: 0; padding-left: 20px">
        <li><strong>默认端口：</strong>8000</li>
        <li><strong>产品类型：</strong>OLAP 分析型数据仓库</li>
        <li><strong>兼容性：</strong>PostgreSQL + 分析扩展</li>
        <li><strong>JDBC URL：</strong>jdbc:postgresql://host:8000/dbname</li>
        <li><strong>推荐参数：</strong>targetServerType=master</li>
      </ul>
      <p style="margin: 12px 0 0 0; padding: 8px; background: #fff3e0; border-radius: 4px">
        <el-icon color="#ff9800"><Warning /></el-icon>
        <span style="color: #ff9800; margin-left: 4px">
          注意：DWS 适合分析查询，响应时间可能较长，建议增加超时时间
        </span>
      </p>
    </div>
  </template>
</el-alert>
```

### 5.4 连接测试优化

#### 5.4.1 测试中提示信息优化

```typescript
const testConnection = async (row: Datasource) => {
  // 显示数据库类型特定的提示
  const dbTypeName = databaseTypeNames[row.type || 'unknown'] || row.type
  const message = row.type === 'dws' 
    ? `正在测试 ${dbTypeName} 连接，数据仓库连接可能需要较长时间...` 
    : `正在测试 ${dbTypeName} 连接...`
  
  const loading = ElLoading.service({
    lock: true,
    text: message,
    background: 'rgba(0, 0, 0, 0.7)'
  })

  try {
    const result = await testDatasourceConnection(row.id!)
    loading.close()
    
    if (result) {
      ElMessage.success(`${dbTypeName} 连接测试成功！`)
      // 刷新列表以更新状态
      await fetchAgentDatasource()
    } else {
      ElMessage.error({
        message: `${dbTypeName} 连接测试失败，请检查配置`,
        duration: 5000
      })
    }
  } catch (error: any) {
    loading.close()
    ElMessage.error({
      message: `${dbTypeName} 连接测试失败: ${error.message || '未知错误'}`,
      duration: 5000
    })
  }
}
```

---

## 六、Maven 依赖配置

### 6.1 当前依赖状态

**根 pom.xml**（`c:\data\code\DataAgent\pom.xml`）

```xml
<!-- 已配置的数据库驱动 -->
<properties>
    <postgresql.version>42.4.1</postgresql.version>
    <dameng.version>8.1.3.140</dameng.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- PostgreSQL（GaussDB 和 DWS 复用） -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgresql.version}</version>
        </dependency>
        
        <!-- 达梦数据库 -->
        <dependency>
            <groupId>com.dameng</groupId>
            <artifactId>DmJdbcDriver18</artifactId>
            <version>${dameng.version}</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 6.2 需要的修改

**无需新增依赖！**

- ✅ **达梦**：已配置 `DmJdbcDriver18`
- ✅ **GaussDB**：使用 PostgreSQL 驱动（已有）
- ✅ **DWS**：使用 PostgreSQL 驱动（已有）

**仅需确认**：
1. Maven 仓库包含达梦驱动（版本 8.1.3.140）
2. PostgreSQL 驱动版本支持 GaussDB/DWS（42.4.1 支持）

### 6.3 Maven 仓库配置

#### 6.3.1 达梦驱动安装

如果企业 Maven 仓库没有达梦驱动，需要手动安装：

```bash
# 下载达梦 JDBC 驱动
# 官网：https://eco.dameng.com/download/

# 手动安装到本地仓库
mvn install:install-file \
  -Dfile=DmJdbcDriver18-8.1.3.140.jar \
  -DgroupId=com.dameng \
  -DartifactId=DmJdbcDriver18 \
  -Dversion=8.1.3.140 \
  -Dpackaging=jar

# 或者部署到企业私服
mvn deploy:deploy-file \
  -Dfile=DmJdbcDriver18-8.1.3.140.jar \
  -DgroupId=com.dameng \
  -DartifactId=DmJdbcDriver18 \
  -Dversion=8.1.3.140 \
  -Dpackaging=jar \
  -Durl=http://your-nexus/repository/releases/ \
  -DrepositoryId=nexus-releases
```

#### 6.3.2 华为云 Maven 仓库（可选）

华为云提供了公共 Maven 仓库，包含 GaussDB 相关依赖：

```xml
<!-- 添加华为云 Maven 仓库 -->
<repositories>
    <repository>
        <id>huaweicloud</id>
        <name>Huawei Cloud Maven</name>
        <url>https://repo.huaweicloud.com/repository/maven/</url>
    </repository>
</repositories>
```

---

## 七、测试验证方案

### 7.1 单元测试清单

#### 7.1.1 达梦数据库测试

**测试类**：`DamengDatasourceTest`

```java
@SpringBootTest
class DamengDatasourceTest {
    
    @Autowired
    private DatasourceService datasourceService;
    
    @Autowired
    private DBConnectionPoolFactory poolFactory;
    
    @Autowired
    private AccessorFactory accessorFactory;
    
    @Test
    void testDamengConnectionPool() {
        // 测试连接池注册
        assertTrue(poolFactory.isRegistered("dameng"));
        
        DBConnectionPool pool = poolFactory.getPoolByDbType("dameng");
        assertNotNull(pool);
        assertEquals("dm.jdbc.driver.DmDriver", pool.getDriver());
    }
    
    @Test
    void testDamengConnection() throws Exception {
        // 测试数据库连接
        Datasource datasource = new Datasource();
        datasource.setType("dameng");
        datasource.setHost("127.0.0.1");
        datasource.setPort(5236);
        datasource.setDatabaseName("SYSDBA");
        datasource.setUsername("SYSDBA");
        datasource.setPassword("SYSDBA");
        
        // 创建数据源
        Datasource created = datasourceService.createDatasource(datasource);
        assertNotNull(created.getId());
        
        // 测试连接
        boolean result = datasourceService.testConnection(created.getId());
        assertTrue(result);
    }
    
    @Test
    void testDamengDdl() throws Exception {
        // 测试表查询
        Datasource datasource = getDamengDatasource();
        List<String> tables = datasourceService.getDatasourceTables(datasource.getId());
        assertNotNull(tables);
        
        // 测试字段查询
        if (!tables.isEmpty()) {
            String tableName = tables.get(0);
            List<String> columns = datasourceService.getTableColumns(datasource.getId(), tableName);
            assertNotNull(columns);
        }
    }
}
```

#### 7.1.2 GaussDB 测试

```java
@SpringBootTest
class GaussDbDatasourceTest {
    
    @Test
    void testGaussDbConnectionPool() {
        assertTrue(poolFactory.isRegistered("gaussdb"));
        
        DBConnectionPool pool = poolFactory.getPoolByDbType("gaussdb");
        assertNotNull(pool);
        assertEquals("org.postgresql.Driver", pool.getDriver());
    }
    
    @Test
    void testGaussDbConnection() {
        Datasource datasource = new Datasource();
        datasource.setType("gaussdb");
        datasource.setHost("127.0.0.1");
        datasource.setPort(8000);
        datasource.setDatabaseName("postgres");
        datasource.setUsername("gaussdb_user");
        datasource.setPassword("password");
        
        Datasource created = datasourceService.createDatasource(datasource);
        boolean result = datasourceService.testConnection(created.getId());
        assertTrue(result);
    }
}
```

#### 7.1.3 DWS 测试

类似 GaussDB，测试连接和 DDL 功能。

### 7.2 集成测试

#### 7.2.1 端到端测试场景

**测试场景1：配置达梦数据源并分析**

1. 前端配置达梦数据源
2. 测试连接成功
3. 初始化数据源到 Agent
4. 选择数据表
5. 执行 NL2SQL 查询
6. 验证查询结果正确

**测试场景2：配置 GaussDB 数据源**

1. 前端配置 GaussDB 数据源
2. 测试连接成功
3. 查询表列表
4. 查询字段列表
5. 配置逻辑外键
6. 执行分析查询

**测试场景3：DWS 大数据量查询**

1. 配置 DWS 数据仓库
2. 连接超大表（百万级数据）
3. 执行聚合分析查询
4. 验证查询性能和结果

### 7.3 性能测试

#### 7.3.1 连接性能测试

| 数据库     | 连接建立时间 | 查询响应时间 | 并发连接数 |
| ---------- | ------------ | ------------ | ---------- |
| MySQL      | < 100ms      | < 50ms       | 100+       |
| PostgreSQL | < 100ms      | < 50ms       | 100+       |
| 达梦       | < 200ms      | < 100ms      | 50+        |
| GaussDB    | < 200ms      | < 100ms      | 50+        |
| DWS        | < 500ms      | 1-10s        | 20+        |

**测试方法**：
```java
@Test
void testConnectionPerformance() {
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < 100; i++) {
        datasourceService.testConnection(datasourceId);
    }
    
    long endTime = System.currentTimeMillis();
    long avgTime = (endTime - startTime) / 100;
    
    System.out.println("Average connection time: " + avgTime + "ms");
    assertTrue(avgTime < 200); // 平均连接时间小于 200ms
}
```

---

## 八、部署与运维

### 8.1 Docker 部署

#### 8.1.1 Docker Compose 配置

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
    networks:
      - dataagent-network
  
  # 华为 GaussDB（使用 PostgreSQL 镜像模拟）
  gaussdb:
    image: postgres:14
    container_name: dataagent-gaussdb
    ports:
      - "8000:5432"  # 映射到 8000 端口
    environment:
      - POSTGRES_USER=gaussdb_user
      - POSTGRES_PASSWORD=gaussdb_pass
      - POSTGRES_DB=gaussdb
    volumes:
      - gaussdb-data:/var/lib/postgresql/data
    networks:
      - dataagent-network
  
  # DataAgent 后端
  dataagent-backend:
    build: .
    ports:
      - "8065:8065"
    depends_on:
      - dameng-db
      - gaussdb
    networks:
      - dataagent-network

volumes:
  dameng-data:
  gaussdb-data:

networks:
  dataagent-network:
    driver: bridge
```

### 8.2 生产环境配置建议

#### 8.2.1 达梦数据库优化

```sql
-- 内存配置
ALTER SYSTEM SET MEMORY_POOL_SIZE = 2048;      -- 2GB 内存池
ALTER SYSTEM SET BUFFER_SIZE = 1024;           -- 1GB 缓冲区

-- 连接数配置
ALTER SYSTEM SET MAX_SESSIONS = 200;

-- 日志配置
ALTER SYSTEM SET REDO_LOG_SIZE = 256;          -- 256MB 日志
```

#### 8.2.2 GaussDB 优化

```sql
-- 连接池配置
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '2GB';
ALTER SYSTEM SET work_mem = '32MB';

-- 查询优化
ALTER SYSTEM SET effective_cache_size = '8GB';
ALTER SYSTEM SET random_page_cost = 1.1;
```

#### 8.2.3 DWS 优化

```sql
-- 数据仓库配置
ALTER SYSTEM SET max_connections = 100;        -- DWS 连接数较少
ALTER SYSTEM SET shared_buffers = '4GB';       -- 更大内存
ALTER SYSTEM SET work_mem = '128MB';           -- 更大工作内存
ALTER SYSTEM SET statement_timeout = '300000'; -- 5分钟超时
```

### 8.3 监控指标

#### 8.3.1 关键指标

| 指标类型 | 监控指标     | 告警阈值 |
| -------- | ------------ | -------- |
| **连接** | 活跃连接数   | > 80%    |
| **响应** | 平均查询时间 | > 5s     |
| **错误** | 连接失败率   | > 5%     |
| **性能** | CPU 使用率   | > 80%    |
| **性能** | 内存使用率   | > 85%    |

#### 8.3.2 监控实现

```java
// 在 DatasourceService 中添加监控
@Slf4j
public class DatasourceServiceImpl {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Override
    public boolean testConnection(Integer id) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            boolean result = realConnectionTest(datasource);
            
            // 记录指标
            sample.stop(Timer.builder("datasource.connection.test")
                .tag("type", datasource.getType())
                .tag("result", result ? "success" : "failed")
                .register(meterRegistry));
            
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("datasource.connection.test")
                .tag("type", datasource.getType())
                .tag("result", "error")
                .register(meterRegistry));
            throw e;
        }
    }
}
```

---

## 九、常见问题与解决方案

### 9.1 达梦数据库常见问题

#### 问题1：ClassNotFoundException: dm.jdbc.driver.DmDriver

**原因**：达梦 JDBC 驱动未正确引入

**解决方案**：
```bash
# 手动安装到本地仓库
mvn install:install-file \
  -Dfile=DmJdbcDriver18-8.1.3.140.jar \
  -DgroupId=com.dameng \
  -DartifactId=DmJdbcDriver18 \
  -Dversion=8.1.3.140 \
  -Dpackaging=jar
```

#### 问题2：连接超时

**原因**：网络问题或防火墙拦截

**解决方案**：
```bash
# 检查端口是否开放
telnet 127.0.0.1 5236

# 检查达梦服务状态
ps -ef | grep dmserver

# 查看达梦日志
tail -f /opt/dmdbms/log/dm.log
```

#### 问题3：Schema 不存在

**原因**：达梦 Schema 区分大小写

**解决方案**：
- Schema 名称统一使用大写（推荐）
- 在 JDBC URL 中明确指定：`jdbc:dm://host:5236?schema=SYSDBA`

### 9.2 GaussDB 常见问题

#### 问题1：SSL 连接失败

**原因**：GaussDB 默认要求 SSL 连接

**解决方案**：
```
# 方案1：禁用 SSL（测试环境）
jdbc:postgresql://host:8000/db?ssl=false

# 方案2：配置 SSL（生产环境）
jdbc:postgresql://host:8000/db?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory
```

#### 问题2：连接数限制

**原因**：GaussDB 连接数配置较低

**解决方案**：
```sql
-- 增加最大连接数
ALTER SYSTEM SET max_connections = 200;

-- 重启 GaussDB 生效
```

### 9.3 DWS 常见问题

#### 问题1：查询超时

**原因**：DWS 查询时间较长

**解决方案**：
```yaml
# 增加 JDBC 超时时间
spring:
  datasource:
    hikari:
      connection-timeout: 60000      # 60秒连接超时
      validation-timeout: 10000      # 10秒验证超时
```

```java
// 在连接池中设置
@Override
public Connection getConnection(DbConfigBO config) {
    Connection conn = super.getConnection(config);
    Statement stmt = conn.createStatement();
    stmt.execute("SET statement_timeout = '300000'");  // 5分钟
    stmt.close();
    return conn;
}
```

#### 问题2：大结果集内存溢出

**原因**：DWS 查询返回大量数据

**解决方案**：
```java
// 使用流式查询
Statement stmt = conn.createStatement(
    ResultSet.TYPE_FORWARD_ONLY,
    ResultSet.CONCUR_READ_ONLY
);
stmt.setFetchSize(1000);  // 分批获取

// 或者限制查询结果
String sql = "SELECT * FROM large_table LIMIT 10000";
```

---

## 十、扩展性设计

### 10.1 支持更多国产数据库

基于现有架构，可轻松扩展支持：

#### OceanBase（蚂蚁/阿里）

```java
// 枚举定义
OCEANBASE(9, "oceanbase", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

// 类型处理器
@Component
public class OceanBaseDatasourceTypeHandler implements DatasourceTypeHandler {
    @Override
    public String typeName() {
        return "oceanbase";
    }
    
    @Override
    public String dialectType() {
        return "mysql";  // 兼容 MySQL
    }
    
    @Override
    public String buildConnectionUrl(Datasource datasource) {
        return String.format(
            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8",
            datasource.getHost(), datasource.getPort(), datasource.getDatabaseName()
        );
    }
}

// 连接池（复用 MySQL 驱动）
@Service
public class OceanBaseJdbcConnectionPool extends AbstractDBConnectionPool {
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    
    @Override
    public String getDriver() {
        return DRIVER;
    }
    
    @Override
    public boolean supportedDataSourceType(String type) {
        return "oceanbase".equals(type);
    }
}
```

#### TiDB（PingCAP）

```java
// TiDB 完全兼容 MySQL，直接使用 MySQL 实现
TIDB(10, "tidb", DatabaseDialectEnum.MYSQL.getCode(), DbAccessTypeEnum.JDBC.getCode()),

// 类型处理器
@Component
public class TiDbDatasourceTypeHandler extends MysqlDatasourceTypeHandler {
    @Override
    public String typeName() {
        return "tidb";
    }
}
```

### 10.2 数据库类型注册机制

建议创建统一的数据库类型注册接口：

```java
/**
 * 数据库类型注册接口
 */
public interface DatabaseTypeRegistry {
    String getName();               // 数据库类型名称
    String getDisplayName();        // 显示名称（中文）
    int getDefaultPort();           // 默认端口
    String getDriverClass();        // JDBC 驱动类名
    String getDialect();            // SQL 方言
    String buildJdbcUrl(String host, int port, String database);  // 构建 JDBC URL
}

/**
 * 注册中心
 */
@Component
public class DatabaseTypeRegistryManager {
    
    private final Map<String, DatabaseTypeRegistry> registries = new HashMap<>();
    
    @PostConstruct
    public void init() {
        register(new MysqlDatabaseType());
        register(new PostgreSqlDatabaseType());
        register(new DamengDatabaseType());
        register(new GaussDbDatabaseType());
        register(new DwsDatabaseType());
    }
    
    public DatabaseTypeRegistry get(String type) {
        return registries.get(type.toLowerCase());
    }
    
    public List<DatabaseTypeRegistry> getAll() {
        return new ArrayList<>(registries.values());
    }
}
```

---

## 十一、总结与建议

### 11.1 改造工作量评估

| 模块             | 工作量   | 优先级 | 说明                |
| ---------------- | -------- | ------ | ------------------- |
| **达梦完善**     | 0.5天    | P0     | 前端选项 + 测试验证 |
| **GaussDB 支持** | 2天      | P0     | 全栈开发 + 测试     |
| **DWS 支持**     | 2天      | P1     | 全栈开发 + 测试     |
| **前端界面**     | 1天      | P0     | UI 优化 + 提示信息  |
| **单元测试**     | 1天      | P1     | 各数据库测试用例    |
| **集成测试**     | 2天      | P1     | 端到端功能验证      |
| **性能测试**     | 1天      | P2     | 连接性能测试        |
| **文档编写**     | 0.5天    | P1     | 使用文档            |
| **总计**         | **10天** | -      | 约 2 周             |

### 11.2 风险评估

| 风险                 | 影响 | 概率 | 应对措施                |
| -------------------- | ---- | ---- | ----------------------- |
| **达梦驱动兼容性**   | 高   | 中   | 提前测试验证            |
| **GaussDB SQL 差异** | 中   | 低   | 兼容 PostgreSQL，风险低 |
| **DWS 性能问题**     | 中   | 中   | 优化连接池配置          |
| **前端兼容性**       | 低   | 低   | 现有架构支持良好        |
| **测试环境搭建**     | 中   | 中   | Docker 快速部署         |

### 11.3 实施步骤建议

**阶段1：准备阶段（2天）**
1. 搭建达梦、GaussDB 测试环境
2. 验证 JDBC 驱动
3. 完成枚举定义

**阶段2：后端开发（4天）**
1. 实现 GaussDB 和 DWS 类型处理器
2. 实现连接池
3. 单元测试通过

**阶段3：前端开发（2天）**
1. 修改数据源类型选择器
2. 添加配置提示信息
3. 优化用户体验

**阶段4：测试阶段（2天）**
1. 集成测试
2. 性能测试
3. Bug 修复

### 11.4 最终建议

1. **优先支持达梦和 GaussDB**：这两个数据库应用最广泛
2. **DWS 作为可选项**：按需支持，主要面向数据仓库场景
3. **充分利用现有架构**：项目架构设计优秀，扩展成本低
4. **完善测试用例**：国产数据库坑较多，需要充分测试
5. **文档先行**：编写详细的配置文档，降低用户使用门槛
6. **监控告警**：部署后持续监控连接状态和性能指标
7. **渐进式上线**：先灰度部署，收集反馈后全面推广

### 11.5 成功标准

**功能完整性**：
- ✅ 用户可在前端选择达梦、GaussDB、DWS
- ✅ 连接测试成功率 > 95%
- ✅ 表查询、字段查询功能正常
- ✅ NL2SQL 查询功能正常
- ✅ 支持逻辑外键配置

**性能指标**：
- ✅ 连接建立时间 < 500ms
- ✅ 简单查询响应时间 < 1s
- ✅ 复杂查询响应时间 < 10s（DWS）
- ✅ 并发连接数 > 20

**用户体验**：
- ✅ 配置步骤清晰简单
- ✅ 错误提示信息明确
- ✅ 文档完善易懂

---

## 附录

### 附录A：数据库对比表

| 特性           | 达梦 DM                 | 华为 GaussDB          | 华为 DWS              |
| -------------- | ----------------------- | --------------------- | --------------------- |
| **产品类型**   | OLTP                    | OLTP                  | OLAP                  |
| **兼容性**     | Oracle/MySQL            | PostgreSQL            | PostgreSQL            |
| **默认端口**   | 5236                    | 8000                  | 8000                  |
| **JDBC 驱动**  | dm.jdbc.driver.DmDriver | org.postgresql.Driver | org.postgresql.Driver |
| **SQL 方言**   | Dameng                  | PostgreSQL            | PostgreSQL            |
| **适用场景**   | 事务处理                | 事务处理              | 数据分析              |
| **响应时间**   | 毫秒级                  | 毫秒级                | 秒级                  |
| **推荐连接数** | 50-100                  | 50-100                | 20-50                 |
| **特殊配置**   | Schema 大小写           | SSL 连接              | 查询超时              |

### 附录B：JDBC URL 示例

```bash
# 达梦数据库
jdbc:dm://127.0.0.1:5236?characterEncoding=UTF-8&useUnicode=true

# 华为 GaussDB
jdbc:postgresql://127.0.0.1:8000/gaussdb?currentSchema=public&ApplicationName=DataAgent

# 华为 DWS
jdbc:postgresql://127.0.0.1:8000/gaussdb?currentSchema=public&targetServerType=master&ssl=false

# 完整参数示例（DWS）
jdbc:postgresql://host:8000/db?currentSchema=public&ApplicationName=DataAgent&targetServerType=master&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory&connectTimeout=10&socketTimeout=300&prepareThreshold=5
```

### 附录C：Docker 快速部署

```bash
# 达梦数据库
docker run -d --name dameng \
  -p 5236:5236 \
  -e SYSDBA_PWD=SYSDBA \
  dameng/dm8:latest

# PostgreSQL（模拟 GaussDB）
docker run -d --name gaussdb \
  -p 8000:5432 \
  -e POSTGRES_USER=gaussdb_user \
  -e POSTGRES_PASSWORD=gaussdb_pass \
  -e POSTGRES_DB=gaussdb \
  postgres:14

# PostgreSQL（模拟 DWS）
docker run -d --name dws \
  -p 8001:5432 \
  -e POSTGRES_USER=dws_user \
  -e POSTGRES_PASSWORD=dws_pass \
  -e POSTGRES_DB=dws \
  postgres:14
```

### 附录D：参考资料

1. **达梦数据库官方文档**：https://eco.dameng.com/document/
2. **达梦 JDBC 开发指南**：https://eco.dameng.com/document/dm/zh-cn/app-dev/java-jdbc.html
3. **华为 GaussDB 文档**：https://support.huaweicloud.com/gaussdb/
4. **华为 DWS 文档**：https://support.huaweicloud.com/dws/
5. **PostgreSQL JDBC 文档**：https://jdbc.postgresql.org/documentation/
6. **Spring Boot 多数据源**：https://spring.io/guides/gs/accessing-data-mysql/

---

## 文档变更记录

| 版本 | 日期       | 修订内容 | 作者         |
| ---- | ---------- | -------- | ------------ |
| v1.0 | 2026-01-15 | 初始版本 | AI Assistant |

---

**文档结束**
    