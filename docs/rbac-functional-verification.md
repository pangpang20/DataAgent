# RBAC 权限模块 — 功能验证指南

## 概述

本文档提供 RBAC 权限模块的功能验证步骤，包括认证、授权、用户管理、角色管理等核心功能的测试方法。

## 前置条件

- 数据库已执行增量部署（参见 `rbac-database-deployment.md`）
- 应用已启动（默认端口 8065）
- Redis 服务已启动
- 已安装 curl 或 Postman 等 API 测试工具

## 环境变量

```bash
export BASE_URL=http://localhost:8065
```

## 1. 认证功能验证

### 1.1 获取验证码

```bash
curl -X GET "$BASE_URL/api/auth/captcha" | jq
```

**预期结果:**
```json
{
  "code": 200,
  "message": "获取验证码成功",
  "data": {
    "captchaKey": "xxx",
    "captchaImage": "data:image/png;base64,..."
  }
}
```

### 1.2 用户登录

```bash
curl -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin@123456",
    "rememberMe": false
  }' | jq
```

**预期结果:**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "userInfo": {
      "userId": 1,
      "username": "admin",
      "nickname": "超级管理员",
      "roles": ["SUPER_ADMIN"],
      "permissions": ["*"]
    }
  }
}
```

**保存 Token 供后续测试使用:**
```bash
export TOKEN="eyJhbGciOiJIUzI1NiJ9..."
```

### 1.3 获取当前用户信息

```bash
curl -X GET "$BASE_URL/api/auth/userinfo" \
  -H "Authorization: Bearer $TOKEN" | jq
```

**预期结果:** 返回用户详细信息，包含菜单树。

### 1.4 刷新 Token

```bash
curl -X POST "$BASE_URL/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }' | jq
```

### 1.5 退出登录

```bash
curl -X POST "$BASE_URL/api/auth/logout" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 1.6 登录失败锁定测试

连续 5 次使用错误密码登录同一账号：

```bash
for i in {1..6}; do
  echo "Attempt $i:"
  curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong","rememberMe":false}' | jq '.code, .message'
done
```

**预期结果:** 第 6 次返回 `401004` 错误，提示账号已锁定。

## 2. 用户管理验证

### 2.1 创建用户

```bash
curl -X POST "$BASE_URL/api/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Test@123456",
    "nickname": "测试用户",
    "email": "test@example.com",
    "phone": "13800138000",
    "status": 1
  }' | jq
```

### 2.2 查询用户列表

```bash
curl -X POST "$BASE_URL/api/users/page" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pageNum": 1,
    "pageSize": 10
  }' | jq
```

### 2.3 分配角色

```bash
curl -X POST "$BASE_URL/api/users/2/roles" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "roleIds": [5]
  }' | jq
```

### 2.4 重置密码

```bash
curl -X POST "$BASE_URL/api/users/2/reset-password" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## 3. 角色管理验证

### 3.1 创建角色

```bash
curl -X POST "$BASE_URL/api/roles" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "roleName": "数据分析师",
    "roleKey": "DATA_ANALYST",
    "description": "负责数据分析工作",
    "status": 1
  }' | jq
```

### 3.2 查询角色列表

```bash
curl -X GET "$BASE_URL/api/roles" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 3.3 分配权限

```bash
curl -X POST "$BASE_URL/api/roles/7/permissions" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "permissionIds": [1, 2, 3, 4, 5]
  }' | jq
```

## 4. 菜单管理验证

### 4.1 查询菜单树

```bash
curl -X GET "$BASE_URL/api/menus/tree" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 4.2 创建菜单

```bash
curl -X POST "$BASE_URL/api/menus" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "menuName": "系统管理",
    "menuType": "DIRECTORY",
    "path": "/system",
    "icon": "setting",
    "sortOrder": 100,
    "visible": 1
  }' | jq
```

## 5. Agent 数据权限验证

### 5.1 授权用户访问 Agent

```bash
curl -X POST "$BASE_URL/api/agent/auth/grant" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": 1,
    "userId": 2,
    "permissionLevel": "READ"
  }' | jq
```

### 5.2 查询 Agent 授权列表

```bash
curl -X GET "$BASE_URL/api/agent/auth/list/1" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 5.3 更新权限级别

```bash
curl -X PUT "$BASE_URL/api/agent/auth/update" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": 1,
    "userId": 2,
    "permissionLevel": "WRITE"
  }' | jq
```

### 5.4 撤销授权

```bash
curl -X DELETE "$BASE_URL/api/agent/auth/revoke/1/2" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## 6. 权限控制验证

### 6.1 未认证访问测试

```bash
curl -s -X GET "$BASE_URL/api/agent/list" | jq '.code'
```

**预期结果:** 返回 `401003`（未认证）。

### 6.2 无权限访问测试

使用普通用户 Token 访问需要管理员权限的接口：

```bash
# 使用普通用户登录
USER_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test@123456","rememberMe":false}' \
  | jq -r '.data.accessToken')

# 尝试访问用户管理接口
curl -s -X GET "$BASE_URL/api/users" \
  -H "Authorization: Bearer $USER_TOKEN" | jq '.code'
```

**预期结果:** 返回 `403001`（权限不足）。

## 7. 审计日志验证

### 7.1 查询登录日志

```bash
curl -X POST "$BASE_URL/api/log/login/page" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pageNum": 1,
    "pageSize": 10
  }' | jq
```

**预期结果:** 包含之前的登录记录。

### 7.2 查询操作日志

```bash
curl -X POST "$BASE_URL/api/log/operation/page" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pageNum": 1,
    "pageSize": 10
  }' | jq
```

**预期结果:** 包含用户创建、角色分配等操作记录。

## 8. 修改密码验证

```bash
curl -X POST "$BASE_URL/api/auth/change-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "oldPassword": "Admin@123456",
    "newPassword": "NewAdmin@123456"
  }' | jq
```

**预期结果:** 返回成功，之后使用新密码登录。

## 验证清单

| 序号 | 验证项 | 预期结果 | 实际结果 |
|---|---|---|---|
| 1 | 获取验证码 | 返回验证码图片 | |
| 2 | 用户登录 | 返回 Token | |
| 3 | 获取用户信息 | 返回用户详情和菜单 | |
| 4 | 刷新 Token | 返回新 Token | |
| 5 | 退出登录 | Token 失效 | |
| 6 | 登录锁定 | 5 次失败后锁定 | |
| 7 | 创建用户 | 用户创建成功 | |
| 8 | 分配角色 | 角色分配成功 | |
| 9 | 创建角色 | 角色创建成功 | |
| 10 | 分配权限 | 权限分配成功 | |
| 11 | Agent 授权 | 授权成功 | |
| 12 | 未认证访问 | 返回 401 | |
| 13 | 无权限访问 | 返回 403 | |
| 14 | 登录日志 | 记录存在 | |
| 15 | 操作日志 | 记录存在 | |
| 16 | 修改密码 | 密码修改成功 | |

## 常见问题

### Q1: 登录返回 401001 错误

检查用户名和密码是否正确，确认数据库中 `sys_user` 表有对应记录。

### Q2: 返回 403001 错误

检查用户是否分配了对应权限，确认 `sys_role_permission` 表有对应记录。

### Q3: 验证码不生效

检查 Redis 是否正常运行，确认 `application.yml` 中 Redis 配置正确。

### Q4: Token 刷新失败

检查 Refresh Token 是否已过期（默认 7 天），或是否已被加入黑名单。
