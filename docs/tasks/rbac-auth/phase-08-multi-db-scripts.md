# 第八阶段：多数据库适配脚本

> 目标：为 MySQL、PostgreSQL、达梦三种数据库分别提供完整的建表和初始化脚本。

---

## 任务 8.1：MySQL 脚本

### 建表脚本

**文件**: `sql/migration/mysql/V3_rbac_tables.sql`

（已在第二阶段任务 2.1 中定义，此处为最终版本）

### 初始化数据脚本

**文件**: `sql/migration/mysql/V3_rbac_init_data.sql`

（已在第二阶段任务 2.3 中定义）

### Agent 表字段迁移

**文件**: `sql/migration/mysql/V3_rename_admin_id_to_creator_id.sql`

（已在第二阶段任务 2.2 中定义）

### H2 测试脚本

**文件**: `sql/h2/schema_rbac.sql`

内容与 MySQL 脚本基本一致，但需要适配 H2 语法差异：
- H2 在 `MODE=MySQL` 下大部分语法兼容
- `AUTO_INCREMENT` 可直接使用
- `DATETIME` 使用 `TIMESTAMP`
- `JSON` 类型使用 `TEXT`（H2 不支持原生 JSON）

**文件**: `sql/h2/data_rbac.sql`

内容与 MySQL 初始化数据脚本一致。

---

## 任务 8.2：PostgreSQL 脚本

### 建表脚本

**文件**: `sql/migration/postgresql/V3_rbac_tables.sql`

**语法差异对照**:

| MySQL | PostgreSQL | 说明 |
|---|---|---|
| `BIGINT AUTO_INCREMENT` | `BIGSERIAL` | 自增主键 |
| `TINYINT` | `SMALLINT` | 布尔/状态字段 |
| `DATETIME` | `TIMESTAMP` | 时间类型 |
| `JSON` | `JSONB` | JSON 类型 |
| `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | （不需要） | PostgreSQL 无需指定引擎 |
| `CURRENT_TIMESTAMP` | `CURRENT_TIMESTAMP` | 相同 |
| `ON UPDATE CURRENT_TIMESTAMP` | 使用触发器实现 | PostgreSQL 不支持列级 ON UPDATE |

**PostgreSQL ON UPDATE 触发器**:

```sql
-- 创建通用的 update_time 触发器函数
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为每个需要 update_time 的表创建触发器
CREATE TRIGGER trg_sys_user_update_time
    BEFORE UPDATE ON sys_user
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- 其他表类似...
```

### 初始化数据脚本

**文件**: `sql/migration/postgresql/V3_rbac_init_data.sql`

**语法差异**:
- `INSERT INTO ... VALUES` 语法相同
- 序列值需要在插入后手动设置：`SELECT setval('sys_user_id_seq', (SELECT MAX(id) FROM sys_user));`

### Agent 表字段迁移

**文件**: `sql/migration/postgresql/V3_rename_admin_id_to_creator_id.sql`

```sql
-- PostgreSQL 使用 RENAME COLUMN
ALTER TABLE agent RENAME COLUMN admin_id TO creator_id;
CREATE INDEX idx_agent_creator_id ON agent(creator_id);
```

---

## 任务 8.3：达梦（DM）脚本

### 建表脚本

**文件**: `sql/migration/dameng/V3_rbac_tables.sql`

**语法差异对照**:

| MySQL | 达梦 | 说明 |
|---|---|---|
| `BIGINT AUTO_INCREMENT` | `BIGINT IDENTITY(1,1)` | 自增主键 |
| `TINYINT` | `TINYINT` | 相同 |
| `DATETIME` | `TIMESTAMP` | 时间类型 |
| `JSON` | `TEXT` | 达梦无原生 JSON 类型 |
| `ENGINE=InnoDB` | （不需要） | 达梦无需指定引擎 |
| `ON UPDATE CURRENT_TIMESTAMP` | 使用触发器实现 | 与 PostgreSQL 相同 |

### 初始化数据脚本

**文件**: `sql/migration/dameng/V3_rbac_init_data.sql`

**语法差异**:
- 达梦的 `INSERT` 语法与 MySQL 基本兼容
- 字符串使用单引号
- `NOW()` 函数可用

### Agent 表字段迁移

**文件**: `sql/migration/dameng/V3_rename_admin_id_to_creator_id.sql`

```sql
-- 达梦使用 RENAME COLUMN
ALTER TABLE agent RENAME COLUMN admin_id TO creator_id;
CREATE INDEX idx_agent_creator_id ON agent(creator_id);
```

---

## 任务 8.4：SQL 脚本校验

**校验清单**:

| 校验项 | MySQL | PostgreSQL | 达梦 |
|---|---|---|---|
| 10 张 RBAC 表创建成功 | | | |
| 所有索引创建成功 | | | |
| 唯一约束生效 | | | |
| 预置角色数据插入 | | | |
| 权限标识数据插入 | | | |
| 菜单数据插入 | | | |
| 默认管理员创建 | | | |
| admin 用户绑定 SUPER_ADMIN | | | |
| Agent 表字段重命名 | | | |
| 触发器（PG/DM 的 update_time） | N/A | | |

**验证方法**:
1. 在每种数据库中执行脚本
2. 查询 `information_schema.tables` 确认表存在
3. 查询 `information_schema.indexes` 确认索引存在
4. `SELECT COUNT(*) FROM sys_role` 确认预置角色数量（6 条）
5. `SELECT COUNT(*) FROM sys_permission` 确认权限标识数量
6. `SELECT COUNT(*) FROM sys_menu` 确认菜单数量
7. `SELECT * FROM sys_user WHERE username = 'admin'` 确认管理员创建
8. `SELECT * FROM sys_user_role` 确认角色绑定

---

## 任务 8.5：MyBatis 兼容性检查

**检查点**:

1. **布尔字段映射**: Entity 中使用 `Integer`（0/1），不需要特殊处理
2. **时间字段映射**: Entity 中使用 `LocalDateTime`，MyBatis 自动映射 `DATETIME`/`TIMESTAMP`
3. **JSON 字段映射**: 如有 JSON 字段，需使用自定义 TypeHandler
   - MySQL: `JSON` 类型 → Java `String`
   - PostgreSQL: `JSONB` 类型 → Java `String`
   - 达梦: `TEXT` 类型 → Java `String`
   - 建议统一使用 `VARCHAR`/`TEXT` 存储 JSON 字符串，避免类型差异

4. **分页查询**: 使用 MyBatis-Plus 的 `IPage`，自动适配三种数据库的分页语法
   - MySQL: `LIMIT ?, ?`
   - PostgreSQL: `LIMIT ? OFFSET ?`
   - 达梦: `LIMIT ?, ?`（兼容 MySQL 语法）

5. **批量插入**: 使用 MyBatis 的 `<foreach>` 标签，三种数据库均支持

**修改文件**: 检查所有 Mapper XML 文件，确保：
- 没有使用数据库特有函数（如 MySQL 的 `GROUP_CONCAT`）
- 没有使用数据库特有语法
- 时间比较使用标准 SQL 函数
