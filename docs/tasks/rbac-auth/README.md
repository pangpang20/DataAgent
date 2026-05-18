# RBAC 权限管理系统实现方案

| 属性 | 值 |
|---|---|
| 需求文档 | `docs/requirements/requirement_02.md` |
| 规格设计 | `docs/architecture/spec_02.md` |
| 创建日期 | 2026-05-18 |
| 状态 | 待实施 |

## 文档索引

| 文档 | 说明 |
|---|---|
| [phase-01-security-infrastructure.md](phase-01-security-infrastructure.md) | 第一阶段：安全基础设施（Spring Security + JWT + Redis） |
| [phase-02-rbac-entities.md](phase-02-rbac-entities.md) | 第二阶段：RBAC 实体与数据层（10 张表 + Entity + Mapper） |
| [phase-03-auth-service.md](phase-03-auth-service.md) | 第三阶段：认证服务（登录/退出/Token 刷新/验证码） |
| [phase-04-user-role-menu-crud.md](phase-04-user-role-menu-crud.md) | 第四阶段：用户/角色/菜单 CRUD 服务 |
| [phase-05-data-permission.md](phase-05-data-permission.md) | 第五阶段：Agent 数据权限隔离与授权 |
| [phase-06-existing-system-refactor.md](phase-06-existing-system-refactor.md) | 第六阶段：现有系统改造（Controller 注解 + CORS + userId 重构） |
| [phase-07-audit-logging.md](phase-07-audit-logging.md) | 第七阶段：审计日志（登录日志 + 操作日志） |
| [phase-08-multi-db-scripts.md](phase-08-multi-db-scripts.md) | 第八阶段：多数据库适配脚本（MySQL/PostgreSQL/达梦） |
| [phase-09-testing-strategy.md](phase-09-testing-strategy.md) | 第九阶段：测试策略（单元测试 + 集成测试） |
| [dependency-matrix.md](dependency-matrix.md) | 任务依赖关系矩阵 |
