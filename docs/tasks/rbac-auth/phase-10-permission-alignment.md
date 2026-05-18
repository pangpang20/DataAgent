# 第十阶段：权限标识对齐

> 目标：修复代码 `@PreAuthorize` 中使用的权限标识与数据库 `sys_permission.permission_key` 之间的不一致，确保 RBAC 权限控制真正生效。

---

## 问题背景

当前代码中 `@PreAuthorize("hasAuthority('xxx')")` 使用的权限标识与 `sys_permission` 表中预置的 `permission_key` 存在大量不一致。由于 Spring Security 通过精确匹配 `GrantedAuthority` 字符串来判断权限，这些不一致会导致：

1. **超级管理员也无法访问** — 即使 SUPER_ADMIN 拥有所有权限，但权限标识不匹配时仍会被拒绝
2. **普通角色完全不可用** — 非超级管理员角色的权限校验全部失败

---

## 不一致清单

### A. 代码使用但数据库缺失的权限

| 代码中的标识 | 涉及 Controller | 说明 |
|---|---|---|
| `agent:update` | AgentController | DB 有 `agent:edit`，需统一命名 |
| `agent:query` | AgentController, GraphController, SessionEventController | 查询 Agent 详情，DB 中无对应项 |
| `agent:datasource` | AgentDatasourceController | Agent 数据源管理，DB 中无对应项 |
| `knowledge:manage` | AgentKnowledgeController, BusinessKnowledgeController | 知识库管理，DB 中无对应项 |
| `file:upload` | FileUploadController | 文件上传，DB 中无对应项 |
| `semantic:model` | SemanticModelController | 语义模型管理，DB 中无对应项 |
| `prompt:config` | PromptConfigController | Prompt 配置，DB 中无对应项 |
| `system:log` | LogController | 日志查询，DB 中无对应项 |

### B. 数据库有但代码未使用的权限

| permission_key | 说明 | 建议 |
|---|---|---|
| `agent:edit` | 与代码 `agent:edit` 不匹配 | 重命名为 `agent:update` 或保留供前端使用 |
| `agent:detail` | 查看 Agent 详情 | 可保留供前端菜单控制 |
| `datasource:create/edit/delete/test` | 数据源 CRUD | 保留，后续 Controller 可补充注解 |
| `knowledge:list/create/edit/delete` | 知识库 CRUD | 保留，或合并为 `knowledge:manage` |
| `system:user:*` / `system:role:*` / `system:menu:*` / `system:model:*` | 通配符权限 | 保留供前端菜单控制，代码使用细粒度标识 |
| `audit:login-log:list` / `audit:operation-log:list` | 审计日志 | 保留供前端菜单控制 |

---

## 任务 10.1：同步数据库权限标识

**修改文件**: 三库迁移脚本 + 全量 data.sql

**操作内容**:

1. **重命名** `agent:edit` → `agent:update`（与代码一致）
2. **新增** 以下权限到 `sys_permission` 表：

```sql
-- 新增缺失的权限标识
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES
('查询Agent', 'agent:query', '查询Agent详情', 'agent'),
('Agent数据源管理', 'agent:datasource', '管理Agent数据源关联', 'agent'),
('知识库管理', 'knowledge:manage', '知识库增删改查', 'knowledge'),
('文件上传', 'file:upload', '上传文件', 'file'),
('语义模型管理', 'semantic:model', '管理语义模型', 'semantic'),
('Prompt配置', 'prompt:config', '管理Prompt配置', 'prompt'),
('日志查询', 'system:log', '查询系统日志', 'system');
```

3. **将新增权限关联到 SUPER_ADMIN**：

```sql
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_key = 'SUPER_ADMIN'
AND p.permission_key IN ('agent:update', 'agent:query', 'agent:datasource', 'knowledge:manage', 'file:upload', 'semantic:model', 'prompt:config', 'system:log');
```

4. 同步更新以下文件：
   - `sql/migration/mysql/V3_rbac_init_data.sql`
   - `sql/migration/postgresql/V3_rbac_init_data.sql`
   - `sql/migration/dameng/V3_rbac_init_data.sql`
   - `sql/mysql/data.sql`
   - `sql/dameng/data.sql`

---

## 任务 10.2：为现有 Controller 补充权限注解

**修改文件**: 以下 Controller 的 `@PreAuthorize` 注解需与权限表一致

| Controller | 当前注解 | 是否匹配 | 操作 |
|---|---|---|---|
| AgentController | `agent:list/create/update/delete/publish/apikey/query` | 部分匹配 | 确认 `agent:update` 替代 `agent:edit` |
| AgentDatasourceController | `agent:datasource` | 缺失 | 无需改代码，补数据 |
| AgentKnowledgeController | `knowledge:manage` | 缺失 | 无需改代码，补数据 |
| BusinessKnowledgeController | `knowledge:manage` | 缺失 | 无需改代码，补数据 |
| DatasourceController | `datasource:list` | 匹配 | 可选：补充 `datasource:create/edit/delete` 注解 |
| FileUploadController | `file:upload` | 缺失 | 无需改代码，补数据 |
| SemanticModelController | `semantic:model` | 缺失 | 无需改代码，补数据 |
| PromptConfigController | `prompt:config` | 缺失 | 无需改代码，补数据 |
| LogController | `system:log` | 缺失 | 无需改代码，补数据 |
| MenuController | `system:menu:*` | 匹配 | OK |
| RoleController | `system:role:create/edit/delete/list` | 匹配 | OK |
| UserController | `system:user:create/edit/delete/list/reset-password/assign-role` | 匹配 | OK |
| GraphController | `agent:query` | 缺失 | 无需改代码，补数据 |
| SessionEventController | `agent:query` | 缺失 | 无需改代码，补数据 |

---

## 任务 10.3：DatasourceController 权限细化

**修改文件**: `controller/DatasourceController.java`

当前 DatasourceController 在类级别使用 `@PreAuthorize("hasAuthority('datasource:list')")`，所有方法共用同一个权限。建议细化：

```java
// 类级别去掉 @PreAuthorize，改为方法级别
@GetMapping      → @PreAuthorize("hasAuthority('datasource:list')")
@PostMapping     → @PreAuthorize("hasAuthority('datasource:create')")
@PutMapping      → @PreAuthorize("hasAuthority('datasource:edit')")
@DeleteMapping   → @PreAuthorize("hasAuthority('datasource:delete')")
/test            → @PreAuthorize("hasAuthority('datasource:test')")
```

---

## 任务 10.4：验证与测试

1. 执行更新后的 SQL 脚本
2. 使用 admin 登录，验证所有接口可正常访问
3. 创建测试用户，分配特定角色，验证权限隔离：
   - 只有 `agent:list` 权限的用户 → 能查看列表，不能创建/删除
   - 只有 `datasource:list` 权限的用户 → 能查看数据源，不能修改

---

## 影响范围

| 维度 | 影响 |
|---|---|
| 数据库 | 新增 7 条权限记录，修改 1 条 |
| 后端代码 | DatasourceController 可选细化，其余无需改动 |
| 前端 | 无影响（前端菜单权限与后端独立） |
| 已有用户 | SUPER_ADMIN 自动获得新权限，其他角色需手动分配 |
