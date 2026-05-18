# 任务依赖关系矩阵

## 阶段依赖

```
Phase 1 (安全基础设施)
  ├── 1.1 Maven 依赖
  ├── 1.2 JWT 配置属性 ← 1.1
  ├── 1.3 Redis 配置 ← 1.1
  ├── 1.4 JWT Token 工具类 ← 1.2
  ├── 1.5 Token 黑名单服务 ← 1.3, 1.4
  ├── 1.6 JWT 认证过滤器 ← 1.4, 1.5
  ├── 1.7 认证失败处理器
  ├── 1.8 权限不足处理器
  ├── 1.9 Security 配置 ← 1.6, 1.7, 1.8
  ├── 1.10 UserDetailsService ← Phase 2 完成后
  └── 1.11 密码强度校验器

Phase 2 (RBAC 实体与数据层)
  ├── 2.1 MySQL 建表脚本
  ├── 2.2 Agent 表字段迁移 ← 2.1
  ├── 2.3 初始化数据脚本 ← 2.1
  ├── 2.4 Entity 类 ← 2.1
  ├── 2.5 Mapper 接口 ← 2.4
  └── 2.6 Mapper XML ← 2.5

Phase 3 (认证服务) ← Phase 1, Phase 2
  ├── 3.1 DTO 定义
  ├── 3.2 认证 Service 接口 ← 3.1
  ├── 3.3 认证 Service 实现 ← 1.4, 1.5, 2.5, 3.2
  ├── 3.4 认证 Controller ← 3.3
  ├── 3.5 Security 工具类 ← 1.4
  ├── 3.6 验证码生成 ← 1.3
  └── 3.7-3.8 全局异常处理 + ApiResponse 适配

Phase 4 (用户/角色/菜单 CRUD) ← Phase 2, Phase 3
  ├── 4.1-4.3 用户管理 ← 2.5, 3.5, 1.11
  ├── 4.4-4.6 角色管理 ← 2.5, 3.5
  └── 4.7-4.10 菜单管理 ← 2.5, 3.5

Phase 5 (Agent 数据权限) ← Phase 2, Phase 3, Phase 4
  ├── 5.1 数据权限拦截器 ← 2.5
  ├── 5.2-5.4 Agent 授权 ← 2.5, 3.5
  ├── 5.5 Agent Controller 改造 ← 5.4
  ├── 5.6 Agent 实体重命名 ← 2.2
  └── 5.7 ChatController 改造 ← 3.5

Phase 6 (现有系统改造) ← Phase 3, Phase 5
  ├── 6.1 CORS 收紧
  ├── 6.2-6.6 Controller 权限注解 ← 3.5
  ├── 6.7 Nl2sql 双重鉴权 ← 1.4
  ├── 6.8 异常处理 ← 3.7
  └── 6.9 MyBatis 拦截器注册 ← 5.1

Phase 7 (审计日志) ← Phase 3
  ├── 7.1-7.3 日志注解与切面
  ├── 7.4 为 Service 添加日志注解 ← 7.3
  ├── 7.5 日志查询接口 ← 2.5
  └── 7.6 启用异步支持

Phase 8 (多数据库脚本) ← Phase 2
  ├── 8.1 MySQL 脚本 ← 2.1, 2.2, 2.3
  ├── 8.2 PostgreSQL 脚本 ← 8.1
  ├── 8.3 达梦脚本 ← 8.1
  ├── 8.4 脚本校验 ← 8.1, 8.2, 8.3
  └── 8.5 MyBatis 兼容性检查

Phase 9 (测试) ← 所有阶段
  ├── 9.1 单元测试 ← Phase 1-5
  ├── 9.2 集成测试 ← Phase 1-7
  ├── 9.3 测试配置 ← 8.1 (H2 脚本)
  ├── 9.4 测试辅助类
  └── 9.5 测试执行与覆盖率
```

## 关键路径

```
1.1 → 1.2 → 1.4 → 1.6 → 1.9 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
                                                                      ↓
                                                                  Phase 9
```

**预估工期**: 4-6 周（1 人全职）

| 阶段 | 预估工期 | 关键依赖 |
|---|---|---|
| Phase 1 | 3-4 天 | 无 |
| Phase 2 | 2-3 天 | Phase 1.1 |
| Phase 3 | 3-4 天 | Phase 1, Phase 2 |
| Phase 4 | 4-5 天 | Phase 2, Phase 3 |
| Phase 5 | 3-4 天 | Phase 2, Phase 3, Phase 4 |
| Phase 6 | 2-3 天 | Phase 3, Phase 5 |
| Phase 7 | 2-3 天 | Phase 3 |
| Phase 8 | 2-3 天 | Phase 2 |
| Phase 9 | 4-5 天 | 所有阶段 |

## 可并行的任务

| 并行组 | 任务 |
|---|---|
| A | Phase 1 (安全基础设施) + Phase 2 (实体与数据层) |
| B | Phase 4 (CRUD) + Phase 7 (审计日志) + Phase 8 (多数据库脚本) |
| C | Phase 6 (现有系统改造) 的 6.1 (CORS) 可独立进行 |
