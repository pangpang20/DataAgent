# 第二阶段：RBAC 实体与数据层

> 目标：创建 RBAC 相关的 10 张数据库表、Entity 类和 MyBatis Mapper。

---

## 任务 2.1：MySQL 建表脚本

**新建文件**: `sql/migration/mysql/V3_rbac_tables.sql`

创建以下 10 张表（参考 spec 6.1-6.10 表结构定义）：

| 表名 | 说明 | 关键索引 |
|---|---|---|
| `sys_user` | 用户表 | `UNIQUE(username)`, `INDEX(status)`, `INDEX(tenant_id)` |
| `sys_role` | 角色表 | `UNIQUE(role_code)`, `INDEX(tenant_id)` |
| `sys_menu` | 菜单表 | `INDEX(parent_id)`, `INDEX(menu_type)` |
| `sys_permission` | 权限表 | `UNIQUE(permission_code)`, `INDEX(module)` |
| `sys_user_role` | 用户角色关联 | `UNIQUE(user_id, role_id)` |
| `sys_role_menu` | 角色菜单关联 | `UNIQUE(role_id, menu_id)` |
| `sys_role_permission` | 角色权限关联 | `UNIQUE(role_id, permission_id)` |
| `sys_agent_auth` | Agent 授权 | `UNIQUE(agent_id, auth_type, auth_target_id)` |
| `sys_login_log` | 登录日志 | `INDEX(username)`, `INDEX(login_time)` |
| `sys_operation_log` | 操作日志 | `INDEX(user_id)`, `INDEX(operate_time)`, `INDEX(module)` |

**关键约束**:
- 所有表使用 `InnoDB` 引擎，`utf8mb4` 字符集
- 逻辑删除字段 `deleted` 使用 `TINYINT DEFAULT 0`
- 时间字段使用 `DATETIME`，默认 `CURRENT_TIMESTAMP`
- 自增主键使用 `BIGINT AUTO_INCREMENT`

**验证**: 在 MySQL 中执行脚本，确认所有表和索引正确创建。

---

## 任务 2.2：Agent 表字段迁移

**新建文件**: `sql/migration/mysql/V3_rename_admin_id_to_creator_id.sql`

```sql
-- 将 agent 表的 admin_id 重命名为 creator_id
ALTER TABLE `agent` CHANGE COLUMN `admin_id` `creator_id` BIGINT NULL COMMENT '创建者ID';
-- 添加索引
ALTER TABLE `agent` ADD INDEX `idx_creator_id` (`creator_id`);
```

**注意**: 脚本需支持幂等执行（使用 `ALTER TABLE ... CHANGE COLUMN`，不检查是否已重命名）。

**达梦版本**: `sql/migration/dameng/V3_rename_admin_id_to_creator_id.sql`
**PostgreSQL 版本**: `sql/migration/postgresql/V3_rename_admin_id_to_creator_id.sql`

---

## 任务 2.3：初始化数据脚本

**新建文件**: `sql/migration/mysql/V3_rbac_init_data.sql`

**内容**:
1. 插入 6 个预置角色（SUPER_ADMIN, TENANT_ADMIN, AUDITOR, OPS_ADMIN, NORMAL_USER, GUEST），`is_system = 1`
2. 插入权限标识数据（约 40 条，覆盖所有模块的 CRUD 操作）
3. 插入菜单数据（目录 + 菜单 + 按钮，参考 spec 6.3 菜单树结构）
4. 为 SUPER_ADMIN 角色分配所有菜单和权限
5. 创建默认超级管理员用户（username: `admin`，BCrypt 加密的默认密码 `Admin@123456`）
6. 将 admin 用户绑定 SUPER_ADMIN 角色

**验证**: 执行后查询各表数据，确认预置数据完整。

---

## 任务 2.4：Entity 类

**新建文件**（均在 `entity/` 包下）:

| 文件 | 说明 | 关键字段 |
|---|---|---|
| `SysUser.java` | 用户实体 | id, username, password, nickname, email, phone, avatar, status, loginFailCount, lockTime, lastLoginTime, lastLoginIp, tenantId, deleted, createBy, createTime, updateBy, updateTime |
| `SysRole.java` | 角色实体 | id, roleCode, roleName, description, status, sortOrder, isSystem, tenantId, deleted, createBy, createTime, updateBy, updateTime |
| `SysMenu.java` | 菜单实体 | id, parentId, menuName, menuType, path, component, permission, icon, sortOrder, visible, status, deleted, createBy, createTime, updateBy, updateTime |
| `SysPermission.java` | 权限实体 | id, permissionCode, permissionName, description, module, status, deleted, createTime, updateTime |
| `SysUserRole.java` | 用户角色关联 | id, userId, roleId, createTime |
| `SysRoleMenu.java` | 角色菜单关联 | id, roleId, menuId, createTime |
| `SysRolePermission.java` | 角色权限关联 | id, roleId, permissionId, createTime |
| `SysAgentAuth.java` | Agent 授权 | id, agentId, authType, authTargetId, permissionLevel, createBy, createTime, updateTime |
| `SysLoginLog.java` | 登录日志 | id, username, ip, userAgent, status, failReason, loginTime |
| `SysOperationLog.java` | 操作日志 | id, userId, username, operation, module, method, url, params, result, errorMsg, ip, duration, operateTime |

**编码规范**:
- 使用 Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- 布尔/状态字段使用 `Integer`（0/1），不用 `Boolean`
- 时间字段使用 `LocalDateTime`
- 逻辑删除字段 `deleted` 使用 `Integer`

**修改文件**: `entity/Agent.java`
- 将 `adminId` 字段重命名为 `creatorId`
- 更新所有引用 `adminId` 的 getter/setter

---

## 任务 2.5：Mapper 接口

**新建文件**（均在 `mapper/` 包下）:

### SysUserMapper.java

```java
@Mapper
public interface SysUserMapper {
    SysUser selectByUsername(@Param("username") String username);
    SysUser selectById(@Param("id") Long id);
    int insert(SysUser user);
    int updateById(SysUser user);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int updatePassword(@Param("id") Long id, @Param("password") String password);
    int updateLoginInfo(@Param("id") Long id, @Param("lastLoginTime") LocalDateTime lastLoginTime, @Param("lastLoginIp") String lastLoginIp);
    int updateLoginFailCount(@Param("id") Long id, @Param("count") Integer count);
    int lockUser(@Param("id") Long id, @Param("lockTime") LocalDateTime lockTime);
    int unlockUser(@Param("id") Long id);
    int logicalDelete(@Param("id") Long id);
    IPage<SysUser> selectPage(IPage<SysUser> page, @Param("query") UserQueryDTO query);
    List<SysUser> selectByRoleId(@Param("roleId") Long roleId);
}
```

### SysRoleMapper.java

```java
@Mapper
public interface SysRoleMapper {
    SysRole selectById(@Param("id") Long id);
    SysRole selectByRoleCode(@Param("roleCode") String roleCode);
    int insert(SysRole role);
    int updateById(SysRole role);
    int logicalDelete(@Param("id") Long id);
    IPage<SysRole> selectPage(IPage<SysRole> page, @Param("query") RoleQueryDTO query);
    List<SysRole> selectByUserId(@Param("userId") Long userId);
    List<SysRole> selectAll();
}
```

### SysMenuMapper.java

```java
@Mapper
public interface SysMenuMapper {
    SysMenu selectById(@Param("id") Long id);
    int insert(SysMenu menu);
    int updateById(SysMenu menu);
    int logicalDelete(@Param("id") Long id);
    List<SysMenu> selectAll();
    List<SysMenu> selectByRoleId(@Param("roleId") Long roleId);
    List<SysMenu> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
    int countByParentId(@Param("parentId") Long parentId);
}
```

### SysPermissionMapper.java

```java
@Mapper
public interface SysPermissionMapper {
    SysPermission selectById(@Param("id") Long id);
    SysPermission selectByCode(@Param("permissionCode") String permissionCode);
    int insert(SysPermission permission);
    int updateById(SysPermission permission);
    int logicalDelete(@Param("id") Long id);
    List<SysPermission> selectAll();
    List<SysPermission> selectByRoleId(@Param("roleId") Long roleId);
    List<SysPermission> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
}
```

### SysUserRoleMapper.java

```java
@Mapper
public interface SysUserRoleMapper {
    int insertBatch(@Param("list") List<SysUserRole> list);
    int deleteByUserId(@Param("userId") Long userId);
    int deleteByRoleId(@Param("roleId") Long roleId);
    List<SysUserRole> selectByUserId(@Param("userId") Long userId);
    List<SysUserRole> selectByRoleId(@Param("roleId") Long roleId);
    int countByRoleId(@Param("roleId") Long roleId);
}
```

### SysRoleMenuMapper.java

```java
@Mapper
public interface SysRoleMenuMapper {
    int insertBatch(@Param("list") List<SysRoleMenu> list);
    int deleteByRoleId(@Param("roleId") Long roleId);
    List<SysRoleMenu> selectByRoleId(@Param("roleId") Long roleId);
}
```

### SysRolePermissionMapper.java

```java
@Mapper
public interface SysRolePermissionMapper {
    int insertBatch(@Param("list") List<SysRolePermission> list);
    int deleteByRoleId(@Param("roleId") Long roleId);
    List<SysRolePermission> selectByRoleId(@Param("roleId") Long roleId);
}
```

### SysAgentAuthMapper.java

```java
@Mapper
public interface SysAgentAuthMapper {
    int insert(SysAgentAuth auth);
    int updateById(SysAgentAuth auth);
    int deleteById(@Param("id") Long id);
    int deleteByAgentId(@Param("agentId") Long agentId);
    SysAgentAuth selectByUniqueKey(@Param("agentId") Long agentId, @Param("authType") Integer authType, @Param("authTargetId") Long authTargetId);
    List<SysAgentAuth> selectByAgentId(@Param("agentId") Long agentId);
    List<SysAgentAuth> selectByAuthTargetId(@Param("authTargetId") Long authTargetId, @Param("authType") Integer authType);
}
```

### SysLoginLogMapper.java

```java
@Mapper
public interface SysLoginLogMapper {
    int insert(SysLoginLog log);
    IPage<SysLoginLog> selectPage(IPage<SysLoginLog> page, @Param("query") LoginLogQueryDTO query);
}
```

### SysOperationLogMapper.java

```java
@Mapper
public interface SysOperationLogMapper {
    int insert(SysOperationLog log);
    IPage<SysOperationLog> selectPage(IPage<SysOperationLog> page, @Param("query") OperationLogQueryDTO query);
}
```

---

## 任务 2.6：Mapper XML 文件

**新建文件**（均在 `resources/mapper/` 目录下）:

| 文件 | 说明 |
|---|---|
| `SysUserMapper.xml` | 用户表 SQL 映射 |
| `SysRoleMapper.xml` | 角色表 SQL 映射 |
| `SysMenuMapper.xml` | 菜单表 SQL 映射 |
| `SysPermissionMapper.xml` | 权限表 SQL 映射 |
| `SysUserRoleMapper.xml` | 用户角色关联 SQL 映射 |
| `SysRoleMenuMapper.xml` | 角色菜单关联 SQL 映射 |
| `SysRolePermissionMapper.xml` | 角色权限关联 SQL 映射 |
| `SysAgentAuthMapper.xml` | Agent 授权 SQL 映射 |
| `SysLoginLogMapper.xml` | 登录日志 SQL 映射 |
| `SysOperationLogMapper.xml` | 操作日志 SQL 映射 |

**关键实现**:
- `SysUserMapper.xml` 的 `selectPage` 使用动态 SQL（`<where>` + `<if>`），支持按 username、status、roleId 筛选
- 所有 `logicalDelete` 使用 `UPDATE ... SET deleted = 1, update_time = NOW() WHERE id = #{id}`
- `selectByUsername` 需加条件 `AND deleted = 0`
- `SysAgentAuthMapper.xml` 的 `selectByUniqueKey` 用于判重，支持 upsert 语义

**单元测试**（使用 H2 内存数据库，`application-h2.yml` profile）:

| 测试类 | 测试内容 |
|---|---|
| `SysUserMapperTest.java` | 用户 CRUD、按用户名查询、分页查询、更新登录信息、锁定/解锁 |
| `SysRoleMapperTest.java` | 角色 CRUD、按角色编码查询、分页查询、按用户 ID 查角色 |
| `SysMenuMapperTest.java` | 菜单 CRUD、按父 ID 查子菜单、按角色 ID 查菜单、统计子菜单数量 |
| `SysPermissionMapperTest.java` | 权限 CRUD、按权限编码查询、按角色 ID 查权限 |
| `SysUserRoleMapperTest.java` | 批量插入、按用户/角色删除、按用户/角色查询、统计角色下用户数 |
| `SysRoleMenuMapperTest.java` | 批量插入、按角色删除、按角色查询 |
| `SysRolePermissionMapperTest.java` | 批量插入、按角色删除、按角色查询 |
| `SysAgentAuthMapperTest.java` | 授权 CRUD、唯一键查询、按 Agent/用户/角色查询 |
| `SysLoginLogMapperTest.java` | 插入日志、分页查询 |
| `SysOperationLogMapperTest.java` | 插入日志、分页查询 |

**H2 兼容**: H2 使用 `MODE=MySQL`，建表脚本需要放在 `sql/h2/schema_rbac.sql` 中。
