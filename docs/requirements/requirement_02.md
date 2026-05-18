# 用户认证与权限管理系统需求说明（Requirement_02）

| 属性 | 值 |
|---|---|
| 文档编号 | REQ-02 |
| 版本 | 1.0.0 |
| 创建日期 | 2026-05-18 |
| 状态 | 草稿 |
| 优先级 | P0（高） |
| 适用系统 | Audaque DataAgent |
| 权限模型 | RBAC（Role-Based Access Control） |

---

## 1. 概述

### 1.1 背景

当前 DataAgent 系统**不存在任何用户认证与权限管理机制**。所有 `/api/*` 接口完全开放，无登录校验、无会话管理、无角色权限控制。`userId` 由客户端自行传入且服务端不做验证，存在严重的安全风险。

本需求旨在为系统构建完整的 RBAC 权限管理体系，覆盖用户认证、用户管理、角色管理、菜单权限、数据权限隔离等核心能力，同时适配 MySQL、PostgreSQL、达梦三种元数据库。

### 1.2 目标

| 目标 | 描述 |
|---|---|
| 统一认证 | 基于 JWT Token 的无状态认证，支持登录/登出/Token 刷新 |
| RBAC 权限 | 用户 → 角色 → 权限 → 菜单的完整 RBAC 模型 |
| 动态菜单 | 登录后根据角色动态加载前端菜单与按钮权限 |
| 数据隔离 | Agent 资源按创建者/授权关系实现数据权限隔离 |
| 多库适配 | 完整支持 MySQL、PostgreSQL、达梦（DM）三种元数据库 |
| 安全合规 | 防暴力破解、防越权、操作审计、密码加密存储 |

### 1.3 术语定义

| 术语 | 说明 |
|---|---|
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| JWT | JSON Web Token，无状态令牌认证机制 |
| Token | 认证令牌，包含用户身份与权限信息 |
| Menu | 系统功能菜单，包含目录、菜单、按钮三种类型 |
| Permission | 权限标识，用于后端接口级别的权限校验（如 `agent:create`） |
| 数据权限 | 控制用户可访问的数据范围（如仅自己创建的 Agent） |

### 1.4 现状分析

| 现状项 | 说明 |
|---|---|
| Spring Security | 未引入，项目无任何安全框架 |
| 用户体系 | 不存在，无 User 实体、无用户表 |
| JWT/Token | 无相关依赖与实现 |
| RBAC | 无角色、权限、菜单相关表与实体 |
| 现有认证 | 仅 `/nl2sql/stream` 有 API Key 校验，其余接口全部开放 |
| CORS 策略 | 允许所有来源（`*`），无认证拦截 |

---

## 2. 系统角色定义

系统预置以下六种基础角色：

| 角色编码 | 角色名称 | 说明 | 默认状态 |
|---|---|---|---|
| `SUPER_ADMIN` | 超级管理员 | 拥有系统所有权限，不可删除 | 启用 |
| `TENANT_ADMIN` | 租户管理员 | 管理租户内所有用户与资源 | 启用 |
| `AUDITOR` | 审核员 | 审核 Agent、知识库等资源 | 启用 |
| `OPS_ADMIN` | 运维人员 | 管理数据源、模型配置等运维资源 | 启用 |
| `NORMAL_USER` | 普通用户 | 创建和管理自己的 Agent | 启用 |
| `GUEST` | 访客用户 | 只读访问，不可创建资源 | 启用 |

### 2.1 角色默认权限矩阵

| 功能模块 | 超级管理员 | 租户管理员 | 审核员 | 运维人员 | 普通用户 | 访客 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| 用户管理 | CRUD + 重置密码 | CRUD（租户内） | 只读 | — | 仅自己 | — |
| 角色管理 | CRUD + 授权 | CRUD（租户内） | 只读 | — | — | — |
| 菜单管理 | CRUD | 只读 | 只读 | — | — | — |
| Agent 管理 | 全部 | 全部（租户内） | 审核 | 只读 | 自己/已授权 | 只读 |
| 数据源管理 | 全部 | 全部（租户内） | 只读 | 全部 | — | — |
| 知识库管理 | 全部 | 全部（租户内） | 审核 | 只读 | 自己/已授权 | 只读 |
| 模型配置 | 全部 | 全部（租户内） | — | 全部 | — | — |
| 系统设置 | 全部 | 部分 | — | 部分 | — | — |
| 操作日志 | 全部 | 全部（租户内） | 全部 | 只读 | — | — |

---

## 3. 登录认证需求（Login）

### 3.1 功能需求列表

| 编号 | 功能项 | 描述 | 优先级 |
|---|---|---|---|
| L-001 | 用户名密码登录 | 用户通过用户名 + 密码进行身份认证 | P0 |
| L-002 | JWT Token 签发 | 登录成功后签发 Access Token + Refresh Token | P0 |
| L-003 | Token 鉴权 | 所有 `/api/*` 接口（除白名单外）需校验 Token 有效性 | P0 |
| L-004 | Token 过期机制 | Access Token 有效期 2 小时，Refresh Token 有效期 7 天 | P0 |
| L-005 | Token 刷新 | Access Token 过期后可通过 Refresh Token 无感刷新 | P0 |
| L-006 | 用户退出 | 调用退出接口后 Token 失效（加入黑名单） | P0 |
| L-007 | 登录失败限制 | 连续 5 次密码错误锁定账号 30 分钟 | P0 |
| L-008 | 验证码机制 | 登录失败 3 次后需输入图形验证码 | P1 |
| L-009 | 用户状态校验 | 禁用状态用户禁止登录，返回明确提示 | P0 |
| L-010 | 登录日志记录 | 记录每次登录的 IP、时间、设备、结果（成功/失败） | P0 |
| L-011 | 记住我 | 勾选后 Refresh Token 延长至 30 天 | P2 |
| L-012 | 密码找回 | 通过邮箱验证码重置密码 | P2 |
| L-013 | OAuth2/SSO 集成 | 支持对接外部 OAuth2 认证源（如企业微信、钉钉、LDAP） | P3 |
| L-014 | 动态菜单加载 | 登录成功后根据用户角色返回菜单树与权限列表 | P0 |

### 3.2 登录流程

```
用户输入用户名 + 密码
  → 后端校验用户是否存在
    → 校验用户状态（是否禁用）
      → 校验密码（BCrypt 比对）
        → 失败：记录失败次数，达到阈值锁定账号
        → 成功：清零失败次数
          → 签发 Access Token + Refresh Token
          → 记录登录日志
          → 返回用户信息 + 菜单树 + 权限列表
```

### 3.3 Token 设计

| 属性 | Access Token | Refresh Token |
|---|---|---|
| 有效期 | 2 小时 | 7 天 |
| 存储位置 | 响应体 / 前端 localStorage | 响应体 / HttpOnly Cookie |
| 用途 | 接口鉴权 | 刷新 Access Token |
| 刷新方式 | — | 调用 `/api/auth/refresh` |
| 失效方式 | 过期 / 主动退出 | 过期 / 主动退出 |

**Token Payload 结构：**

```json
{
  "sub": "userId",
  "username": "admin",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["agent:create", "agent:delete", "..."],
  "iat": 1716000000,
  "exp": 1716007200
}
```

### 3.4 接口白名单（无需 Token）

以下接口不需要 Token 鉴权：

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/refresh` | POST | 刷新 Token |
| `/api/auth/captcha` | GET | 获取图形验证码 |
| `/api/auth/reset-password` | POST | 密码找回 |
| `/api/auth/oauth2/**` | GET | OAuth2 回调 |
| `/nl2sql/stream` | GET | Widget API（已有 API Key 鉴权） |
| `/actuator/health` | GET | 健康检查 |

---

## 4. 用户管理需求（User Management）

### 4.1 功能需求列表

| 编号 | 功能项 | 描述 | 权限要求 | 优先级 |
|---|---|---|---|---|
| U-001 | 用户创建 | 管理员创建新用户，设置用户名、密码、角色 | `user:create` | P0 |
| U-002 | 用户编辑 | 管理员编辑用户基本信息 | `user:edit` | P0 |
| U-003 | 用户删除 | 逻辑删除用户（标记 `deleted=1`） | `user:delete` | P0 |
| U-004 | 用户查询 | 分页查询用户列表，支持按用户名/状态/角色筛选 | `user:list` | P0 |
| U-005 | 用户详情 | 查看单个用户详细信息（含角色列表） | `user:view` | P0 |
| U-006 | 用户启用/禁用 | 切换用户状态 | `user:toggle` | P0 |
| U-007 | 用户角色分配 | 为用户分配一个或多个角色 | `user:assign-role` | P0 |
| U-008 | 管理员重置密码 | 管理员重置指定用户的密码（无需旧密码） | `user:reset-password` | P0 |
| U-009 | 修改个人信息 | 用户修改自己的昵称、邮箱、手机号 | 登录用户 | P0 |
| U-010 | 修改个人密码 | 用户修改自己的密码（需校验旧密码） | 登录用户 | P0 |
| U-011 | 查看个人信息 | 用户查看自己的详细信息 | 登录用户 | P0 |

### 4.2 权限分层

| 操作 | 超级管理员 | 租户管理员 | 普通用户 |
|---|:---:|:---:|:---:|
| 创建用户 | 全局 | 租户内 | — |
| 编辑用户 | 全局 | 租户内 | 仅自己 |
| 删除用户 | 全局 | 租户内 | — |
| 重置密码 | 全局 | 租户内 | — |
| 分配角色 | 全局 | 租户内 | — |
| 启用/禁用 | 全局 | 租户内 | — |
| 查看列表 | 全局 | 租户内 | — |
| 修改自己信息 | 自己 | 自己 | 自己 |
| 修改自己密码 | 自己 | 自己 | 自己 |

### 4.3 密码安全要求

| 要求项 | 规则 |
|---|---|
| 加密算法 | BCrypt（推荐）或 Argon2 |
| 明文存储 | **严格禁止** |
| 密码强度 | 最少 8 位，包含大小写字母 + 数字 + 特殊字符 |
| 旧密码校验 | 用户自行修改密码时必须验证旧密码 |
| 管理员重置 | 管理员重置密码无需旧密码，重置后强制用户首次登录修改 |
| 密码历史 | 最近 5 次使用过的密码不可重复使用 |
| 传输安全 | 登录接口强制 HTTPS，密码不明文传输 |

---

## 5. 角色管理需求（Role Management）

### 5.1 功能需求列表

| 编号 | 功能项 | 描述 | 权限要求 | 优先级 |
|---|---|---|---|---|
| R-001 | 角色创建 | 创建新角色，设置角色编码、名称、描述 | `role:create` | P0 |
| R-002 | 角色编辑 | 编辑角色基本信息 | `role:edit` | P0 |
| R-003 | 角色删除 | 逻辑删除角色（预置角色不可删除） | `role:delete` | P0 |
| R-004 | 角色查询 | 分页查询角色列表 | `role:list` | P0 |
| R-005 | 角色详情 | 查看角色详情（含权限列表、菜单列表、用户数量） | `role:view` | P0 |
| R-006 | 角色启用/禁用 | 切换角色状态 | `role:toggle` | P0 |
| R-007 | 角色菜单授权 | 为角色分配可访问的菜单 | `role:assign-menu` | P0 |
| R-008 | 角色权限授权 | 为角色分配操作权限（按钮/API 级别） | `role:assign-permission` | P0 |
| R-009 | 角色用户绑定 | 查看并管理角色下的用户列表 | `role:manage-user` | P0 |

### 5.2 约束规则

- 预置角色（六种系统角色）不可删除，可修改名称和描述
- 角色删除前需校验是否仍有用户绑定，若有则提示确认
- 角色禁用后，拥有该角色的用户立即失去对应权限
- 一个用户可拥有多个角色，权限取并集

---

## 6. 菜单与权限管理需求（Menu & Permission）

### 6.1 菜单设计

菜单采用树形结构，支持三级嵌套。菜单类型分为三种：

| 菜单类型 | type 值 | 说明 | 示例 |
|---|---|---|---|
| 目录 | `0` | 仅作为分组容器，不对应页面 | 系统管理 |
| 菜单 | `1` | 对应一个可访问的页面路由 | 用户管理页面 |
| 按钮 | `2` | 页面内的操作权限标识 | 新增用户按钮 |

### 6.2 功能需求列表

| 编号 | 功能项 | 描述 | 权限要求 | 优先级 |
|---|---|---|---|---|
| M-001 | 菜单创建 | 创建目录/菜单/按钮 | `menu:create` | P0 |
| M-002 | 菜单编辑 | 编辑菜单信息 | `menu:edit` | P0 |
| M-003 | 菜单删除 | 删除菜单（校验是否有子菜单） | `menu:delete` | P0 |
| M-004 | 菜单树查询 | 返回完整的菜单树结构 | `menu:list` | P0 |
| M-005 | 权限标识管理 | 管理权限标识（如 `agent:create`） | `permission:manage` | P0 |

### 6.3 菜单树结构示例

```
├── 首页 (directory)
│   └── 工作台 (menu: /dashboard)
├── Agent 管理 (directory)
│   ├── Agent 列表 (menu: /agent/list)
│   │   ├── Agent 查看 (button: agent:view)
│   │   ├── Agent 创建 (button: agent:create)
│   │   ├── Agent 编辑 (button: agent:edit)
│   │   ├── Agent 删除 (button: agent:delete)
│   │   └── Agent 授权 (button: agent:authorize)
│   └── Agent 审核 (menu: /agent/audit) [审核员可见]
├── 知识库管理 (directory)
│   ├── 业务知识 (menu: /knowledge/business)
│   └── Agent 知识 (menu: /knowledge/agent)
├── 数据源管理 (directory)
│   └── 数据源列表 (menu: /datasource/list)
├── 模型管理 (directory)
│   └── 模型配置 (menu: /model/config)
├── 系统管理 (directory) [管理员可见]
│   ├── 用户管理 (menu: /system/user)
│   │   ├── 用户创建 (button: user:create)
│   │   ├── 用户编辑 (button: user:edit)
│   │   ├── 用户删除 (button: user:delete)
│   │   └── 重置密码 (button: user:reset-password)
│   ├── 角色管理 (menu: /system/role)
│   │   ├── 角色创建 (button: role:create)
│   │   ├── 角色编辑 (button: role:edit)
│   │   ├── 角色删除 (button: role:delete)
│   │   └── 角色授权 (button: role:assign-menu)
│   ├── 菜单管理 (menu: /system/menu)
│   └── 操作日志 (menu: /system/log)
└── 个人中心 (directory)
    ├── 个人信息 (menu: /profile/info)
    └── 修改密码 (menu: /profile/password)
```

### 6.4 权限标识命名规范

权限标识格式：`{模块}:{操作}`

| 模块 | 操作类型 | 示例 |
|---|---|---|
| agent | view, create, edit, delete, authorize | `agent:create` |
| user | view, create, edit, delete, toggle, reset-password, assign-role | `user:edit` |
| role | view, create, edit, delete, toggle, assign-menu, assign-permission | `role:delete` |
| menu | view, create, edit, delete | `menu:create` |
| datasource | view, create, edit, delete, test | `datasource:edit` |
| knowledge | view, create, edit, delete | `knowledge:delete` |
| model | view, create, edit, delete | `model:edit` |
| system | log:view, config:edit | `system:log:view` |

### 6.5 前后端权限校验机制

| 层级 | 实现方式 | 说明 |
|---|---|---|
| 前端菜单 | 登录后获取菜单树，动态渲染侧边栏 | 不同角色看到不同菜单 |
| 前端按钮 | 通过 `v-permission="agent:create"` 指令控制按钮显示 | 按钮级别权限 |
| 后端接口 | Spring Security `@PreAuthorize` 注解 | 接口级别权限校验 |
| 数据层 | MyBatis 拦截器 / 数据权限注解 | 查询时自动注入数据过滤条件 |

---

## 7. Agent 数据权限隔离需求

### 7.1 权限规则

Agent 资源实现数据级别的权限隔离：

| 角色 | 可查看 | 可编辑 | 可删除 | 可授权 |
|---|---|---|---|---|
| 超级管理员 | 所有 Agent | 所有 Agent | 所有 Agent | 所有 Agent |
| 租户管理员 | 租户内所有 Agent | 租户内所有 Agent | 租户内所有 Agent | 租户内所有 Agent |
| 普通用户 | 自己创建 + 被授权的 Agent | 自己创建 + 被授权（编辑权限）的 Agent | 仅自己创建的 Agent | 仅自己创建的 Agent |
| 审核员 | 所有 Agent（只读） | — | — | — |
| 运维人员 | 所有 Agent（只读） | — | — | — |
| 访客用户 | 被授权的 Agent（只读） | — | — | — |

### 7.2 Agent 授权机制

| 编号 | 功能项 | 描述 | 优先级 |
|---|---|---|---|
| A-001 | 授权给用户 | Agent 创建者/管理员可将 Agent 授权给指定用户 | P0 |
| A-002 | 授权给角色 | Agent 创建者/管理员可将 Agent 授权给指定角色 | P0 |
| A-003 | 权限粒度 | 授权时可指定权限：只读 / 编辑 / 管理 | P0 |
| A-004 | 收回授权 | Agent 创建者/管理员可收回已授予的权限 | P0 |
| A-005 | 授权记录查询 | 查询 Agent 的授权历史与当前授权列表 | P1 |

### 7.3 数据权限实现要求

| 要求 | 说明 |
|---|---|
| 数据层过滤 | 在 MyBatis Mapper 层自动注入数据权限过滤 SQL |
| API 层校验 | Controller 层校验当前用户对目标资源的访问权限 |
| 防止越权 | 修改/删除操作必须校验资源归属（`creator_id = currentUserId` 或有授权记录） |
| SQL 注入防护 | 数据权限条件使用参数化查询，禁止字符串拼接 |

---

## 8. 安全要求

### 8.1 认证安全

| 编号 | 安全项 | 要求 | 优先级 |
|---|---|---|---|
| S-001 | JWT 签名 | 使用 HMAC-SHA256 或 RSA256 签名，密钥长度 >= 256 位 | P0 |
| S-002 | Token 过期 | Access Token 2 小时，Refresh Token 7 天 | P0 |
| S-003 | Token 黑名单 | 用户退出后 Token 加入 Redis 黑名单，过期自动清除 | P0 |
| S-004 | 密码加密 | BCrypt（cost factor >= 10）或 Argon2id | P0 |
| S-005 | 禁止明文 | 数据库中严禁存储明文密码 | P0 |

### 8.2 防攻击策略

| 编号 | 安全项 | 要求 | 优先级 |
|---|---|---|---|
| S-006 | 防暴力破解 | 连续 5 次登录失败锁定账号 30 分钟 | P0 |
| S-007 | 防水平越权 | 用户 A 不可访问/修改用户 B 的资源（校验 creator_id） | P0 |
| S-008 | 防垂直越权 | 普通用户不可调用管理员接口（校验角色/权限） | P0 |
| S-009 | 接口幂等 | 关键操作（创建/删除）需保证幂等性 | P1 |
| S-010 | 请求频率限制 | 对敏感接口（登录、密码重置）进行限流 | P1 |
| S-011 | CORS 收紧 | 限制允许的来源域名，不再使用 `*` | P0 |
| S-012 | SQL 注入防护 | 所有查询使用参数化查询 | P0 |

### 8.3 审计日志

| 编号 | 审计项 | 记录内容 | 优先级 |
|---|---|---|---|
| S-013 | 登录日志 | 用户名、IP、时间、设备、结果（成功/失败/锁定） | P0 |
| S-014 | 操作日志 | 操作人、操作类型、目标资源、请求参数、响应结果、IP | P0 |
| S-015 | 权限变更日志 | 角色变更、权限变更、用户授权变更的前后值 | P0 |
| S-016 | 敏感操作日志 | 密码重置、用户删除、角色删除等敏感操作单独记录 | P0 |

---

## 9. 多数据库适配需求

本系统元数据库支持 MySQL、PostgreSQL、达梦（DM）三种数据库。所有建表 SQL、数据类型、语法差异均需分别提供适配脚本。

### 9.1 数据库差异对照

| 功能点 | MySQL | PostgreSQL | 达梦（DM） |
|---|---|---|---|
| 自增主键 | `AUTO_INCREMENT` | `BIGSERIAL` / `BIGINT GENERATED ALWAYS AS IDENTITY` | `IDENTITY(1,1)` |
| 逻辑删除 | `TINYINT DEFAULT 0` | `SMALLINT DEFAULT 0` | `TINYINT DEFAULT 0` |
| 时间类型 | `DATETIME` | `TIMESTAMP` | `TIMESTAMP` |
| JSON 类型 | `JSON` | `JSONB` | `TEXT`（JSON 字符串） |
| 字符集 | `utf8mb4` | `UTF8` | `UTF8` |
| 布尔类型 | `TINYINT(1)` | `BOOLEAN` | `TINYINT` |
| 位运算 | 支持 | 支持 | 支持 |
| 唯一索引 | `UNIQUE INDEX` | `UNIQUE INDEX` | `UNIQUE INDEX` |

### 9.2 子需求拆分

为确保三种数据库均可正常运行，拆分为以下三个子需求：

#### 子需求 1：MySQL 适配（SUB-REQ-02-MYSQL）

- 提供完整的 MySQL 建表 SQL（InnoDB 引擎，utf8mb4 字符集）
- 使用 `AUTO_INCREMENT` 自增主键
- 使用 `TINYINT` 表示布尔/状态字段
- 使用 `DATETIME` 表示时间字段
- 使用 `JSON` 类型存储扩展字段
- SQL 脚本路径：`data-agent-management/src/main/resources/sql/migration/mysql/`

#### 子需求 2：PostgreSQL 适配（SUB-REQ-02-PGSQL）

- 提供完整的 PostgreSQL 建表 SQL
- 使用 `BIGSERIAL` 或 `BIGINT GENERATED ALWAYS AS IDENTITY` 自增主键
- 使用 `BOOLEAN` 表示布尔字段
- 使用 `TIMESTAMP` 表示时间字段
- 使用 `JSONB` 类型存储扩展字段
- SQL 脚本路径：`data-agent-management/src/main/resources/sql/migration/postgresql/`

#### 子需求 3：达梦（DM）适配（SUB-REQ-02-DM）

- 提供完整的达梦建表 SQL
- 使用 `IDENTITY(1,1)` 自增主键
- 使用 `TINYINT` 表示布尔/状态字段
- 使用 `TIMESTAMP` 表示时间字段
- 使用 `TEXT` 存储 JSON 字符串（达梦无原生 JSON 类型）
- 达梦特有的语法兼容处理
- SQL 脚本路径：`data-agent-management/src/main/resources/sql/migration/dameng/`

---

## 10. 数据库设计

### 10.1 ER 关系图

```
sys_user (用户表)
    │
    │ 1:N
    ▼
sys_user_role (用户角色关联表)
    │
    │ N:1
    ▼
sys_role (角色表)
    │
    ├── 1:N ──▶ sys_role_menu (角色菜单关联表) ──▶ sys_menu (菜单表)
    │
    └── 1:N ──▶ sys_role_permission (角色权限关联表) ──▶ sys_permission (权限表)

sys_agent_auth (Agent 授权关联表)
    ├── N:1 ──▶ agent (Agent 表，已有)
    ├── N:1 ──▶ sys_user (被授权用户)
    └── N:1 ──▶ sys_role (被授权角色)

sys_login_log (登录日志表)
sys_operation_log (操作日志表)
```

### 10.2 表结构定义

#### 10.2.1 sys_user（用户表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 用户 ID |
| `username` | VARCHAR(50) | 否 | — | 用户名（唯一） |
| `password` | VARCHAR(200) | 否 | — | 密码（BCrypt 加密） |
| `nickname` | VARCHAR(50) | 是 | NULL | 昵称 |
| `email` | VARCHAR(100) | 是 | NULL | 邮箱 |
| `phone` | VARCHAR(20) | 是 | NULL | 手机号 |
| `avatar` | VARCHAR(500) | 是 | NULL | 头像 URL |
| `status` | TINYINT | 否 | 1 | 状态：1-启用，0-禁用 |
| `login_fail_count` | INT | 否 | 0 | 连续登录失败次数 |
| `lock_time` | DATETIME/TIMESTAMP | 是 | NULL | 账号锁定截止时间 |
| `last_login_time` | DATETIME/TIMESTAMP | 是 | NULL | 最后登录时间 |
| `last_login_ip` | VARCHAR(50) | 是 | NULL | 最后登录 IP |
| `tenant_id` | BIGINT | 是 | NULL | 所属租户 ID |
| `deleted` | TINYINT | 否 | 0 | 逻辑删除：0-正常，1-已删除 |
| `create_by` | VARCHAR(50) | 是 | NULL | 创建人 |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_by` | VARCHAR(50) | 是 | NULL | 更新人 |
| `update_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `username` | 用户名唯一 |
| 普通索引 | `status` | 按状态查询 |
| 普通索引 | `tenant_id` | 租户隔离查询 |

---

#### 10.2.2 sys_role（角色表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 角色 ID |
| `role_code` | VARCHAR(50) | 否 | — | 角色编码（唯一） |
| `role_name` | VARCHAR(50) | 否 | — | 角色名称 |
| `description` | VARCHAR(200) | 是 | NULL | 角色描述 |
| `status` | TINYINT | 否 | 1 | 状态：1-启用，0-禁用 |
| `sort_order` | INT | 否 | 0 | 排序序号 |
| `is_system` | TINYINT | 否 | 0 | 是否系统预置角色：1-是，0-否 |
| `tenant_id` | BIGINT | 是 | NULL | 所属租户 ID |
| `deleted` | TINYINT | 否 | 0 | 逻辑删除 |
| `create_by` | VARCHAR(50) | 是 | NULL | 创建人 |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_by` | VARCHAR(50) | 是 | NULL | 更新人 |
| `update_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `role_code` | 角色编码唯一 |
| 普通索引 | `tenant_id` | 租户隔离查询 |

---

#### 10.2.3 sys_menu（菜单表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 菜单 ID |
| `parent_id` | BIGINT | 否 | 0 | 父菜单 ID（0 表示根节点） |
| `menu_name` | VARCHAR(50) | 否 | — | 菜单名称 |
| `menu_type` | TINYINT | 否 | — | 类型：0-目录，1-菜单，2-按钮 |
| `path` | VARCHAR(200) | 是 | NULL | 路由地址 |
| `component` | VARCHAR(200) | 是 | NULL | 前端组件路径 |
| `permission` | VARCHAR(100) | 是 | NULL | 权限标识（如 `agent:create`） |
| `icon` | VARCHAR(100) | 是 | NULL | 菜单图标 |
| `sort_order` | INT | 否 | 0 | 排序序号 |
| `visible` | TINYINT | 否 | 1 | 是否可见：1-是，0-否 |
| `status` | TINYINT | 否 | 1 | 状态：1-启用，0-禁用 |
| `deleted` | TINYINT | 否 | 0 | 逻辑删除 |
| `create_by` | VARCHAR(50) | 是 | NULL | 创建人 |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_by` | VARCHAR(50) | 是 | NULL | 更新人 |
| `update_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 普通索引 | `parent_id` | 树形查询 |
| 普通索引 | `menu_type` | 按类型查询 |

---

#### 10.2.4 sys_permission（权限表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 权限 ID |
| `permission_code` | VARCHAR(100) | 否 | — | 权限编码（唯一，如 `agent:create`） |
| `permission_name` | VARCHAR(50) | 否 | — | 权限名称 |
| `description` | VARCHAR(200) | 是 | NULL | 权限描述 |
| `module` | VARCHAR(50) | 是 | NULL | 所属模块 |
| `status` | TINYINT | 否 | 1 | 状态：1-启用，0-禁用 |
| `deleted` | TINYINT | 否 | 0 | 逻辑删除 |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `permission_code` | 权限编码唯一 |
| 普通索引 | `module` | 按模块查询 |

---

#### 10.2.5 sys_user_role（用户角色关联表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 主键 |
| `user_id` | BIGINT | 否 | — | 用户 ID |
| `role_id` | BIGINT | 否 | — | 角色 ID |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `(user_id, role_id)` | 防止重复分配 |
| 普通索引 | `user_id` | 按用户查角色 |
| 普通索引 | `role_id` | 按角色查用户 |

---

#### 10.2.6 sys_role_menu（角色菜单关联表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 主键 |
| `role_id` | BIGINT | 否 | — | 角色 ID |
| `menu_id` | BIGINT | 否 | — | 菜单 ID |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `(role_id, menu_id)` | 防止重复授权 |
| 普通索引 | `role_id` | 按角色查菜单 |

---

#### 10.2.7 sys_role_permission（角色权限关联表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 主键 |
| `role_id` | BIGINT | 否 | — | 角色 ID |
| `permission_id` | BIGINT | 否 | — | 权限 ID |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `(role_id, permission_id)` | 防止重复授权 |
| 普通索引 | `role_id` | 按角色查权限 |

---

#### 10.2.8 sys_agent_auth（Agent 授权关联表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 主键 |
| `agent_id` | BIGINT | 否 | — | Agent ID |
| `auth_type` | TINYINT | 否 | — | 授权类型：1-用户，2-角色 |
| `auth_target_id` | BIGINT | 否 | — | 被授权用户 ID 或角色 ID |
| `permission_level` | TINYINT | 否 | 1 | 权限级别：1-只读，2-编辑，3-管理 |
| `create_by` | VARCHAR(50) | 是 | NULL | 授权人 |
| `create_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 唯一索引 | `(agent_id, auth_type, auth_target_id)` | 防止重复授权 |
| 普通索引 | `agent_id` | 按 Agent 查授权 |
| 普通索引 | `auth_target_id` | 按用户/角色查授权 |

---

#### 10.2.9 sys_login_log（登录日志表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 主键 |
| `username` | VARCHAR(50) | 否 | — | 登录用户名 |
| `ip` | VARCHAR(50) | 是 | NULL | 登录 IP |
| `user_agent` | VARCHAR(500) | 是 | NULL | 浏览器/设备信息 |
| `status` | TINYINT | 否 | — | 结果：1-成功，0-失败 |
| `fail_reason` | VARCHAR(200) | 是 | NULL | 失败原因 |
| `login_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 登录时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 普通索引 | `username` | 按用户查日志 |
| 普通索引 | `login_time` | 按时间范围查询 |

---

#### 10.2.10 sys_operation_log（操作日志表）

| 字段名 | 数据类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | 否 | 自增主键 | 主键 |
| `user_id` | BIGINT | 是 | NULL | 操作人 ID |
| `username` | VARCHAR(50) | 是 | NULL | 操作人用户名 |
| `operation` | VARCHAR(50) | 否 | — | 操作类型（CREATE/UPDATE/DELETE/LOGIN 等） |
| `module` | VARCHAR(50) | 是 | NULL | 操作模块 |
| `method` | VARCHAR(10) | 否 | — | HTTP 方法 |
| `url` | VARCHAR(500) | 否 | — | 请求 URL |
| `params` | TEXT | 是 | NULL | 请求参数（脱敏） |
| `result` | TINYINT | 是 | NULL | 结果：1-成功，0-失败 |
| `error_msg` | TEXT | 是 | NULL | 错误信息 |
| `ip` | VARCHAR(50) | 是 | NULL | 操作 IP |
| `duration` | INT | 是 | NULL | 耗时（毫秒） |
| `operate_time` | DATETIME/TIMESTAMP | 否 | CURRENT_TIMESTAMP | 操作时间 |

**约束与索引：**

| 类型 | 字段 | 说明 |
|---|---|---|
| 主键 | `id` | 聚簇索引 |
| 普通索引 | `user_id` | 按用户查日志 |
| 普通索引 | `operate_time` | 按时间范围查询 |
| 普通索引 | `module` | 按模块查询 |

---

## 11. 接口设计

### 11.1 统一响应结构

**成功响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1716000000
}
```

**失败响应：**

```json
{
  "code": 401001,
  "message": "用户名或密码错误",
  "data": null,
  "timestamp": 1716000000
}
```

### 11.2 错误码设计

| 错误码 | HTTP 状态码 | 说明 |
|---|---|---|
| `200` | 200 | 成功 |
| `400` | 400 | 请求参数错误 |
| `401001` | 401 | 用户名或密码错误 |
| `401002` | 401 | Token 已过期 |
| `401003` | 401 | Token 无效 |
| `401004` | 401 | 账号已被禁用 |
| `401005` | 401 | 账号已被锁定 |
| `403001` | 403 | 无权限访问该资源 |
| `403002` | 403 | 无权操作他人的资源 |
| `404` | 404 | 资源不存在 |
| `409` | 409 | 数据冲突（如用户名已存在） |
| `429` | 429 | 请求过于频繁 |
| `500` | 500 | 服务器内部错误 |

### 11.3 接口清单

#### 11.3.1 认证接口（Auth）

| 接口 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 用户登录 | POST | `/api/auth/login` | 用户名密码登录，返回 Token | 白名单 |
| 用户退出 | POST | `/api/auth/logout` | Token 加入黑名单 | 登录用户 |
| 刷新 Token | POST | `/api/auth/refresh` | 通过 Refresh Token 获取新 Token | 白名单 |
| 获取验证码 | GET | `/api/auth/captcha` | 获取图形验证码 | 白名单 |
| 获取当前用户 | GET | `/api/auth/current-user` | 返回当前登录用户信息 + 菜单 + 权限 | 登录用户 |
| 密码找回 | POST | `/api/auth/reset-password` | 通过邮箱验证码重置密码 | 白名单 |

**登录接口详细设计：**

```
POST /api/auth/login
Content-Type: application/json

请求体：
{
  "username": "admin",
  "password": "encrypted_password",
  "captchaCode": "ABCD",
  "captchaKey": "uuid-xxx"
}

成功响应：
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 7200,
    "userInfo": {
      "id": 1,
      "username": "admin",
      "nickname": "超级管理员",
      "avatar": "https://...",
      "roles": ["SUPER_ADMIN"]
    },
    "menus": [ ... ],
    "permissions": ["agent:create", "agent:edit", "..."]
  }
}
```

#### 11.3.2 用户管理接口（User）

| 接口 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 用户列表 | GET | `/api/system/user` | 分页查询用户 | `user:list` |
| 用户详情 | GET | `/api/system/user/{id}` | 查看用户详情 | `user:view` |
| 创建用户 | POST | `/api/system/user` | 创建新用户 | `user:create` |
| 编辑用户 | PUT | `/api/system/user/{id}` | 编辑用户信息 | `user:edit` |
| 删除用户 | DELETE | `/api/system/user/{id}` | 逻辑删除用户 | `user:delete` |
| 启用/禁用 | PUT | `/api/system/user/{id}/status` | 切换用户状态 | `user:toggle` |
| 重置密码 | PUT | `/api/system/user/{id}/reset-password` | 管理员重置密码 | `user:reset-password` |
| 分配角色 | PUT | `/api/system/user/{id}/roles` | 为用户分配角色 | `user:assign-role` |
| 修改个人信息 | PUT | `/api/profile/info` | 用户修改自己的信息 | 登录用户 |
| 修改个人密码 | PUT | `/api/profile/password` | 用户修改自己的密码 | 登录用户 |

#### 11.3.3 角色管理接口（Role）

| 接口 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 角色列表 | GET | `/api/system/role` | 分页查询角色 | `role:list` |
| 角色详情 | GET | `/api/system/role/{id}` | 查看角色详情 | `role:view` |
| 创建角色 | POST | `/api/system/role` | 创建新角色 | `role:create` |
| 编辑角色 | PUT | `/api/system/role/{id}` | 编辑角色信息 | `role:edit` |
| 删除角色 | DELETE | `/api/system/role/{id}` | 逻辑删除角色 | `role:delete` |
| 启用/禁用 | PUT | `/api/system/role/{id}/status` | 切换角色状态 | `role:toggle` |
| 菜单授权 | PUT | `/api/system/role/{id}/menus` | 为角色分配菜单 | `role:assign-menu` |
| 权限授权 | PUT | `/api/system/role/{id}/permissions` | 为角色分配权限 | `role:assign-permission` |

#### 11.3.4 菜单管理接口（Menu）

| 接口 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 菜单树 | GET | `/api/system/menu/tree` | 返回完整菜单树 | `menu:list` |
| 菜单详情 | GET | `/api/system/menu/{id}` | 查看菜单详情 | `menu:view` |
| 创建菜单 | POST | `/api/system/menu` | 创建新菜单 | `menu:create` |
| 编辑菜单 | PUT | `/api/system/menu/{id}` | 编辑菜单 | `menu:edit` |
| 删除菜单 | DELETE | `/api/system/menu/{id}` | 删除菜单 | `menu:delete` |

#### 11.3.5 Agent 授权接口（Agent Auth）

| 接口 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 查看授权列表 | GET | `/api/agent/{id}/auth` | 查看 Agent 的授权记录 | `agent:view` |
| 授权给用户 | POST | `/api/agent/{id}/auth/user` | 将 Agent 授权给用户 | `agent:authorize` |
| 授权给角色 | POST | `/api/agent/{id}/auth/role` | 将 Agent 授权给角色 | `agent:authorize` |
| 收回授权 | DELETE | `/api/agent/{id}/auth/{authId}` | 收回授权 | `agent:authorize` |

---

## 12. 现有系统改造影响

### 12.1 需要改造的现有代码

| 文件/模块 | 当前状态 | 改造内容 |
|---|---|---|
| `WebConfig.java` | CORS 允许 `*` | 收紧 CORS 策略，限制允许来源 |
| `ChatController.java` | `userId` 从请求体获取 | 改为从 JWT Token 中获取当前用户 ID |
| `AgentController.java` | 无权限校验 | 添加 `@PreAuthorize` 注解 |
| `DatasourceController.java` | 无权限校验 | 添加 `@PreAuthorize` 注解 |
| `KnowledgeController.java` | 无权限校验 | 添加 `@PreAuthorize` 注解 |
| `ModelConfigController.java` | 无权限校验 | 添加 `@PreAuthorize` 注解 |
| `Nl2sqlController.java` | API Key 校验 | 保留 API Key 校验，新增 JWT 校验分支 |
| `Agent.java` 实体 | 有 `adminId` 字段 | 重命名为 `creatorId`，关联 sys_user |
| `Datasource.java` 实体 | 有 `creatorId` 字段 | 关联 sys_user，实现数据权限过滤 |
| `ChatSession.java` 实体 | 有 `userId` 字段 | 关联 sys_user |

### 12.2 新增依赖

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
</dependency>

<!-- Redis（Token 黑名单 + 验证码缓存） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Captcha（图形验证码） -->
<dependency>
    <groupId>com.github.whvcse</groupId>
    <artifactId>easy-captcha</artifactId>
    <version>1.6.2</version>
</dependency>
```

### 12.3 新增包结构

```
com.audaque.cloud.ai.dataagent
├── config
│   └── SecurityConfig.java              # Spring Security 配置
├── security
│   ├── JwtTokenProvider.java            # JWT Token 生成/解析/验证
│   ├── JwtAuthenticationFilter.java     # JWT 认证过滤器
│   ├── JwtAuthenticationEntryPoint.java # 认证失败处理器
│   ├── UserDetailsServiceImpl.java      # 用户详情加载
│   ├── PasswordValidator.java           # 密码强度校验
│   └── DataPermissionInterceptor.java   # 数据权限拦截器
├── controller
│   └── AuthController.java              # 认证接口
├── entity
│   ├── SysUser.java
│   ├── SysRole.java
│   ├── SysMenu.java
│   ├── SysPermission.java
│   ├── SysUserRole.java
│   ├── SysRoleMenu.java
│   ├── SysRolePermission.java
│   ├── SysAgentAuth.java
│   ├── SysLoginLog.java
│   └── SysOperationLog.java
├── mapper
│   ├── SysUserMapper.java
│   ├── SysRoleMapper.java
│   ├── SysMenuMapper.java
│   ├── SysPermissionMapper.java
│   └── ...
├── service
│   ├── auth/
│   │   ├── AuthService.java
│   │   └── AuthServiceImpl.java
│   ├── user/
│   │   ├── UserService.java
│   │   └── UserServiceImpl.java
│   ├── role/
│   │   ├── RoleService.java
│   │   └── RoleServiceImpl.java
│   └── menu/
│       ├── MenuService.java
│       └── MenuServiceImpl.java
└── dto
    ├── LoginRequest.java
    ├── LoginResponse.java
    ├── UserInfoResponse.java
    ├── UserCreateRequest.java
    ├── RoleCreateRequest.java
    ├── MenuCreateRequest.java
    └── AgentAuthRequest.java
```

---

## 13. 验收标准

### 13.1 功能验收

| 编号 | 验收项 | 验收标准 |
|---|---|---|
| AC-001 | 登录认证 | 用户名密码登录成功返回 JWT Token，失败返回错误提示 |
| AC-002 | Token 鉴权 | 无 Token 或无效 Token 访问 `/api/*` 返回 401 |
| AC-003 | Token 刷新 | Access Token 过期后通过 Refresh Token 成功刷新 |
| AC-004 | 用户退出 | 退出后原 Token 立即失效 |
| AC-005 | 登录锁定 | 连续 5 次密码错误后账号锁定 30 分钟 |
| AC-006 | 用户 CRUD | 管理员可正常创建、编辑、删除、查询用户 |
| AC-007 | 密码加密 | 数据库中密码为 BCrypt 加密格式，不可逆 |
| AC-008 | 角色管理 | 可创建角色、分配菜单权限、分配操作权限 |
| AC-009 | 动态菜单 | 不同角色登录后看到不同菜单 |
| AC-010 | 按钮权限 | 前端按钮根据权限标识动态显示/隐藏 |
| AC-011 | 接口权限 | 无权限用户调用受限接口返回 403 |
| AC-012 | Agent 数据隔离 | 普通用户仅能查看自己创建或被授权的 Agent |
| AC-013 | Agent 授权 | 创建者可将 Agent 授权给其他用户/角色 |
| AC-014 | 操作日志 | 所有写操作记录到操作日志表 |
| AC-015 | 登录日志 | 每次登录尝试记录到登录日志表 |
| AC-016 | MySQL 适配 | 使用 MySQL 元数据库时功能正常 |
| AC-017 | PostgreSQL 适配 | 使用 PostgreSQL 元数据库时功能正常 |
| AC-018 | 达梦适配 | 使用达梦元数据库时功能正常 |

### 13.2 安全验收

| 编号 | 验收项 | 验收标准 |
|---|---|---|
| SC-001 | 水平越权 | 用户 A 无法访问用户 B 的 Agent（返回 403） |
| SC-002 | 垂直越权 | 普通用户无法调用管理员接口（返回 403） |
| SC-003 | 密码安全 | 数据库无明文密码，修改密码需校验旧密码 |
| SC-004 | Token 安全 | 退出后 Token 失效，过期 Token 无法使用 |
| SC-005 | CORS 安全 | 不再允许所有来源的跨域请求 |

---

## 14. 实施建议

### 14.1 分阶段实施

| 阶段 | 内容 | 周期 | 优先级 |
|---|---|---|---|
| 第一阶段 | 数据库建表 + 用户认证（登录/退出/JWT） + Spring Security 基础框架 | 1-2 周 | P0 |
| 第二阶段 | 用户管理 CRUD + 角色管理 CRUD + 菜单管理 | 1-2 周 | P0 |
| 第三阶段 | 前端动态菜单 + 按钮权限 + 接口权限注解 | 1 周 | P0 |
| 第四阶段 | Agent 数据权限隔离 + 授权机制 | 1 周 | P0 |
| 第五阶段 | 审计日志 + 登录日志 + 操作日志 | 3-5 天 | P1 |
| 第六阶段 | PostgreSQL 适配 + 达梦适配 | 1 周 | P1 |
| 第七阶段 | OAuth2/SSO + 验证码 + 密码找回 + 记住我 | 1 周 | P2 |

### 14.2 风险与注意事项

| 风险项 | 说明 | 应对措施 |
|---|---|---|
| 现有接口改造 | 所有 Controller 需添加权限注解，可能影响现有功能 | 分批改造，灰度上线 |
| 前端改造量 | 动态菜单、权限指令、登录页面均需前端配合 | 前后端并行开发 |
| 数据库迁移 | 新增 10 张表，需编写三种数据库的建表脚本 | 参考现有迁移脚本规范 |
| Redis 依赖 | Token 黑名单需要 Redis 支持 | 本地开发可用 embedded-redis |
| 测试覆盖 | 权限逻辑复杂，需充分测试各角色组合 | 编写自动化测试用例 |
