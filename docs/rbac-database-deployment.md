# RBAC 权限模块 — 数据库增量部署指南

## 概述

RBAC 权限模块新增 11 张数据库表，需要在现有数据库上执行增量 SQL 脚本。本文档说明三种数据库（MySQL、PostgreSQL、达梦）的部署步骤。

## 前置条件

- 数据库连接正常，且有 DDL 执行权限
- 已备份现有数据库（建议）
- 已确认当前数据库版本兼容性

## 新增表清单

| 表名 | 说明 |
|---|---|
| `sys_user` | 用户表 |
| `sys_role` | 角色表 |
| `sys_menu` | 菜单表 |
| `sys_permission` | 权限表 |
| `sys_user_role` | 用户角色关联表 |
| `sys_role_menu` | 角色菜单关联表 |
| `sys_role_permission` | 角色权限关联表 |
| `sys_agent_auth` | Agent 数据权限表 |
| `sys_login_log` | 登录日志表 |
| `sys_operation_log` | 操作日志表 |
| `sys_password_history` | 密码历史表 |

## 部署步骤

### Step 1: 执行建表脚本

根据实际数据库类型选择对应脚本：

**MySQL:**
```bash
mysql -u <user> -p <database> < data-agent-management/src/main/resources/sql/migration/mysql/V3_rbac_tables.sql
```

**PostgreSQL:**
```bash
psql -U <user> -d <database> -f data-agent-management/src/main/resources/sql/migration/postgresql/V3_rbac_tables.sql
```

**达梦:**
```bash
disql <user>/<password>@<host>:<port> -f data-agent-management/src/main/resources/sql/migration/dameng/V3_rbac_tables.sql
```

### Step 2: 执行字段重命名脚本

将 `agent` 表的 `admin_id` 字段重命名为 `creator_id`：

**MySQL:**
```bash
mysql -u <user> -p <database> < data-agent-management/src/main/resources/sql/migration/mysql/V3_rename_admin_id_to_creator_id.sql
```

**PostgreSQL:**
```bash
psql -U <user> -d <database> -f data-agent-management/src/main/resources/sql/migration/postgresql/V3_rename_admin_id_to_creator_id.sql
```

**达梦:**
```bash
disql <user>/<password>@<host>:<port> -f data-agent-management/src/main/resources/sql/migration/dameng/V3_rename_admin_id_to_creator_id.sql
```

### Step 3: 执行初始数据脚本

插入预置角色、权限、菜单和默认管理员账号：

**MySQL:**
```bash
mysql -u <user> -p <database> < data-agent-management/src/main/resources/sql/migration/mysql/V3_rbac_init_data.sql
```

**PostgreSQL:**
```bash
psql -U <user> -d <database> -f data-agent-management/src/main/resources/sql/migration/postgresql/V3_rbac_init_data.sql
```

**达梦:**
```bash
disql <user>/<password>@<host>:<port> -f data-agent-management/src/main/resources/sql/migration/dameng/V3_rbac_init_data.sql
```

### Step 4: 验证部署

执行以下 SQL 验证表和数据是否正确：

```sql
-- 检查表是否存在
SELECT table_name FROM information_schema.tables WHERE table_name LIKE 'sys_%';

-- 检查预置角色
SELECT * FROM sys_role;

-- 检查预置用户（默认密码: Admin@123456）
SELECT id, username, nickname, status FROM sys_user;

-- 检查权限数据
SELECT COUNT(*) FROM sys_permission;

-- 检查菜单数据
SELECT COUNT(*) FROM sys_menu;
```

## 预置数据说明

### 预置角色

| 角色标识 | 角色名称 | 说明 |
|---|---|---|
| `SUPER_ADMIN` | 超级管理员 | 拥有所有权限 |
| `TENANT_ADMIN` | 租户管理员 | 管理本租户用户和资源 |
| `AUDITOR` | 审计员 | 查看所有日志和操作记录 |
| `OPS_ADMIN` | 运维管理员 | 管理数据源和系统配置 |
| `NORMAL_USER` | 普通用户 | 使用 Agent 和查看数据 |
| `GUEST` | 访客 | 只读权限 |

### 默认管理员账号

| 字段 | 值 |
|---|---|
| 用户名 | `admin` |
| 密码 | `Admin@123456` |
| 昵称 | 超级管理员 |
| 状态 | 启用 |

**安全提示**: 首次登录后请立即修改默认密码。

## 回滚方案

如需回滚 RBAC 模块，执行以下 SQL：

```sql
-- 删除 RBAC 相关表（按依赖关系逆序）
DROP TABLE IF EXISTS sys_password_history;
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_agent_auth;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

-- 恢复 agent 表字段名（如需要）
ALTER TABLE agent CHANGE creator_id admin_id BIGINT;
```

## 应用配置

部署数据库后，需要在 `application.yml` 中配置以下新增项：

```yaml
dataagent:
  jwt:
    secret: your-jwt-secret-key-at-least-32-chars
    access-token-expiration: 7200
    refresh-token-expiration: 604800

spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
```
