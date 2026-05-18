# 第九阶段：测试策略

> 目标：制定全面的单元测试和集成测试计划，确保 RBAC 功能的正确性和安全性。

---

## 测试架构

```
src/test/java/com/audaque/cloud/ai/dataagent/
├── security/
│   ├── JwtTokenProviderTest.java          # JWT 工具类单元测试
│   ├── JwtAuthenticationFilterTest.java   # 认证过滤器单元测试
│   ├── PasswordValidatorTest.java         # 密码校验器单元测试
│   ├── RedisTokenBlacklistServiceImplTest.java  # Token 黑名单单元测试
│   ├── CaptchaServiceImplTest.java        # 验证码服务单元测试
│   └── DataPermissionInterceptorTest.java # 数据权限拦截器单元测试
├── service/
│   ├── auth/
│   │   └── AuthServiceImplTest.java       # 认证服务单元测试
│   ├── user/
│   │   └── UserServiceImplTest.java       # 用户管理单元测试
│   ├── role/
│   │   └── RoleServiceImplTest.java       # 角色管理单元测试
│   ├── menu/
│   │   └── MenuServiceImplTest.java       # 菜单管理单元测试
│   └── agent/
│       └── AgentAuthServiceImplTest.java  # Agent 授权单元测试
├── mapper/
│   ├── SysUserMapperTest.java             # 用户 Mapper 测试
│   ├── SysRoleMapperTest.java             # 角色 Mapper 测试
│   ├── SysMenuMapperTest.java             # 菜单 Mapper 测试
│   ├── SysPermissionMapperTest.java       # 权限 Mapper 测试
│   ├── SysUserRoleMapperTest.java         # 用户角色关联 Mapper 测试
│   ├── SysRoleMenuMapperTest.java         # 角色菜单关联 Mapper 测试
│   ├── SysRolePermissionMapperTest.java   # 角色权限关联 Mapper 测试
│   ├── SysAgentAuthMapperTest.java        # Agent 授权 Mapper 测试
│   ├── SysLoginLogMapperTest.java         # 登录日志 Mapper 测试
│   └── SysOperationLogMapperTest.java     # 操作日志 Mapper 测试
├── controller/
│   ├── AuthControllerIntegrationTest.java # 认证接口集成测试
│   ├── UserControllerIntegrationTest.java # 用户管理接口集成测试
│   ├── RoleControllerIntegrationTest.java # 角色管理接口集成测试
│   └── AgentAuthIntegrationTest.java      # Agent 授权集成测试
└── config/
    └── SecurityConfigIntegrationTest.java # Security 配置集成测试
```

---

## 一、单元测试

### 1.1 JWT 工具类测试

**测试类**: `security/JwtTokenProviderTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testGenerateAccessToken` | 生成 Access Token | Token 非空，解析后 sub/username/roles/permissions 正确 |
| `testGenerateRefreshToken` | 生成 Refresh Token | Token 非空，解析后 sub/username 正确，无 roles/permissions |
| `testValidateToken_ValidToken` | 校验有效 Token | 返回 true |
| `testValidateToken_ExpiredToken` | 校验过期 Token | 返回 false |
| `testValidateToken_TamperedToken` | 校验篡改 Token | 返回 false |
| `testValidateToken_NullToken` | 校验 null Token | 返回 false |
| `testValidateToken_EmptyToken` | 校验空字符串 Token | 返回 false |
| `testGetUserIdFromToken` | 提取 userId | 返回正确的 userId |
| `testGetRolesFromToken` | 提取角色列表 | 返回正确的角色列表 |
| `testGetPermissionsFromToken` | 提取权限列表 | 返回正确的权限列表 |
| `testIsTokenExpired_NotExpired` | 未过期 Token | 返回 false |
| `testIsTokenExpired_Expired` | 已过期 Token | 返回 true |
| `testRememberMe_LongExpiration` | 记住我模式 | Refresh Token 有效期为 30 天 |

**实现方式**: 纯单元测试，不依赖 Spring 上下文。使用 `@ExtendWith(MockitoExtension.class)`。

---

### 1.2 密码校验器测试

**测试类**: `security/PasswordValidatorTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testValidPassword` | 合法密码 `Admin@123` | valid=true |
| `testTooShort` | 长度 7 位 `Adm@1a` | valid=false, message 包含"长度" |
| `testNoUpperCase` | 无大写 `admin@123` | valid=false, message 包含"大写" |
| `testNoLowerCase` | 无小写 `ADMIN@123` | valid=false, message 包含"小写" |
| `testNoDigit` | 无数字 `Admin@abc` | valid=false, message 包含"数字" |
| `testNoSpecialChar` | 无特殊字符 `Admin123` | valid=false, message 包含"特殊字符" |
| `testNullPassword` | null | valid=false |
| `testEmptyPassword` | 空字符串 | valid=false |
| `testExactly8Chars` | 恰好 8 位 `Admin@1a` | valid=true |
| `testVeryLongPassword` | 200 位密码 | valid=true |

---

### 1.3 Token 黑名单测试

**测试类**: `security/RedisTokenBlacklistServiceImplTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testAddToBlacklist` | 添加 Token 到黑名单 | RedisTemplate.set 被调用，key 和 TTL 正确 |
| `testIsBlacklisted_True` | 查询已加入黑名单的 Token | 返回 true |
| `testIsBlacklisted_False` | 查询未加入黑名单的 Token | 返回 false |
| `testIsBlacklisted_RedisDown` | Redis 不可用时查询 | 返回 false（降级），日志有 WARN |

**实现方式**: Mock `RedisTemplate`，验证调用参数。

---

### 1.4 认证过滤器测试

**测试类**: `security/JwtAuthenticationFilterTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testWhitelistPath_NoToken` | 白名单路径无 Token | 放行，SecurityContext 无 Authentication |
| `testValidToken_SetAuth` | 有效 Token 访问受保护路径 | SecurityContext 有 Authentication |
| `testInvalidToken_NoAuth` | 无效 Token | SecurityContext 无 Authentication |
| `testExpiredToken_NoAuth` | 过期 Token | SecurityContext 无 Authentication |
| `testBlacklistedToken_NoAuth` | 黑名单中的 Token | SecurityContext 无 Authentication |
| `testNoAuthHeader` | 无 Authorization 头 | SecurityContext 无 Authentication |
| `testMalformedAuthHeader` | Authorization 头格式错误 | SecurityContext 无 Authentication |

**实现方式**: Mock `JwtTokenProvider`、`TokenBlacklistService`、`FilterChain`。

---

### 1.5 认证服务测试

**测试类**: `service/auth/AuthServiceImplTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testLogin_Success` | 正确用户名密码 | 返回 LoginResponse，包含 accessToken/refreshToken/menus/permissions |
| `testLogin_UserNotFound` | 用户名不存在 | 抛出 BizException(401001) |
| `testLogin_UserDisabled` | 用户已禁用 | 抛出 BizException(401004) |
| `testLogin_AccountLocked` | 账号已锁定 | 抛出 BizException(401005) |
| `testLogin_WrongPassword` | 密码错误 | 抛出 BizException(401001)，失败次数 +1 |
| `testLogin_LockAfter5Failures` | 连续 5 次密码错误 | 账号被锁定 30 分钟 |
| `testLogin_RequireCaptcha_After3Failures` | 3 次失败后需验证码 | 无验证码时返回 400 |
| `testLogin_WrongCaptcha` | 验证码错误 | 返回 400 |
| `testLogin_ClearFailCount_Success` | 登录成功后清零 | 失败计数清零 |
| `testLogout` | 用户退出 | Token 加入黑名单 |
| `testRefreshToken_Success` | 有效 Refresh Token | 返回新的 accessToken |
| `testRefreshToken_Expired` | 过期 Refresh Token | 抛出 BizException(401002) |
| `testRefreshToken_Blacklisted` | 黑名单中的 Refresh Token | 抛出 BizException(401003) |
| `testGetCurrentUser` | 获取当前用户信息 | 返回用户信息+菜单+权限 |

**实现方式**: Mock 所有 Mapper 和外部依赖，使用 `@ExtendWith(MockitoExtension.class)`。

---

### 1.6 用户管理服务测试

**测试类**: `service/user/UserServiceImplTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testCreateUser_Success` | 创建合法用户 | 返回用户详情，密码已加密 |
| `testCreateUser_DuplicateUsername` | 用户名重复 | 抛出 BizException(409) |
| `testCreateUser_WeakPassword` | 密码强度不足 | 抛出 BizException(400) |
| `testDeleteUser_Success` | 删除用户 | 用户标记 deleted=1，角色关联清除 |
| `testDeleteUser_NotFound` | 删除不存在用户 | 抛出 BizException(404) |
| `testAssignRoles_Success` | 分配角色 | 旧关联删除，新关联插入 |
| `testResetPassword_Success` | 管理员重置密码 | 密码已加密更新 |
| `testChangePassword_Success` | 修改密码（旧密码正确） | 密码更新成功 |
| `testChangePassword_WrongOldPassword` | 旧密码错误 | 抛出 BizException |
| `testChangePassword_ReuseRecentPassword` | 新密码与历史重复 | 抛出 BizException(400) |
| `testUpdateProfile_Success` | 修改个人信息 | 信息更新成功 |
| `testUpdateProfile_OtherUser` | 修改他人信息 | 抛出 BizException(403) |

---

### 1.7 角色管理服务测试

**测试类**: `service/role/RoleServiceImplTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testCreateRole_Success` | 创建合法角色 | 返回角色详情 |
| `testCreateRole_DuplicateCode` | 角色编码重复 | 抛出 BizException(409) |
| `testDeleteRole_Success` | 删除非预置角色 | 角色标记 deleted=1 |
| `testDeleteRole_SystemRole` | 删除预置角色 | 抛出 BizException，提示不可删除 |
| `testDeleteRole_HasUsers` | 删除有用户绑定的角色 | 返回确认信息（force=false） |
| `testDeleteRole_ForceDelete` | 强制删除有用户绑定的角色 | 解除绑定并删除 |
| `testAssignMenus_Success` | 分配菜单 | 旧关联删除，新关联插入 |
| `testAssignPermissions_Success` | 分配权限 | 旧关联删除，新关联插入 |
| `testToggleStatus_Disable` | 禁用角色 | 状态更新为 0 |

---

### 1.8 菜单管理服务测试

**测试类**: `service/menu/MenuServiceImplTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testCreateMenu_Directory` | 创建目录（type=0） | 创建成功 |
| `testCreateMenu_Menu` | 创建菜单（type=1） | 创建成功，path 和 component 必填 |
| `testCreateMenu_Button` | 创建按钮（type=2） | 创建成功，permission 必填 |
| `testCreateMenu_ExceedDepth` | 超过三级深度 | 抛出 BizException(400) |
| `testCreateMenu_UnderButton` | 在按钮下创建子节点 | 抛出 BizException(400) |
| `testDeleteMenu_NoChildren` | 删除无子菜单的菜单 | 删除成功 |
| `testDeleteMenu_HasChildren` | 删除有子菜单的菜单 | 抛出 BizException(400) |
| `testGetMenuTree` | 获取菜单树 | 返回三级树形结构，按 sortOrder 排序 |

---

### 1.9 Agent 授权服务测试

**测试类**: `service/agent/AgentAuthServiceImplTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testGrantToUser_Success` | 授权给用户 | 授权记录创建成功 |
| `testGrantToUser_UpdateExisting` | 重复授权（更新权限级别） | 权限级别更新 |
| `testGrantToRole_Success` | 授权给角色 | 授权记录创建成功 |
| `testRevokeAuth_Success` | 收回授权 | 授权记录删除 |
| `testHasAccess_Creator` | 创建者访问自己的 Agent | 返回 true |
| `testHasAccess_GrantedUser` | 被授权用户访问 | 返回 true |
| `testHasAccess_UnauthorizedUser` | 未授权用户访问 | 返回 false |
| `testHasAccess_SuperAdmin` | 超级管理员访问任意 Agent | 返回 true |
| `testCanEdit_ReadOnly` | 只读权限用户编辑 | 返回 false |
| `testCanEdit_EditPermission` | 编辑权限用户编辑 | 返回 true |
| `testCanDelete_Creator` | 创建者删除 | 返回 true |
| `testCanDelete_NonCreator` | 非创建者删除 | 返回 false |
| `testCanDelete_SuperAdmin` | 超级管理员删除 | 返回 true |

---

### 1.10 Mapper 测试

**测试基类**: `mapper/BaseMapperTest.java`

使用 H2 内存数据库（`application-h2.yml` profile），每个 Mapper 测试类独立运行。

**通用测试模式**:
```java
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
@Rollback
class SysUserMapperTest {
    @Autowired
    private SysUserMapper userMapper;

    @Test
    void testInsert() { ... }

    @Test
    void testSelectByUsername() { ... }

    @Test
    void testSelectByUsername_NotFound() { ... }

    @Test
    void testUpdate() { ... }

    @Test
    void testLogicalDelete() { ... }

    @Test
    void testSelectPage() { ... }
}
```

每个 Mapper 测试覆盖：
- 增删改查基本操作
- 唯一约束冲突
- 分页查询（含条件筛选）
- 关联查询（如按 userId 查角色列表）
- 边界条件（空列表、null 参数）

---

## 二、集成测试

### 2.1 Security 配置集成测试

**测试类**: `config/SecurityConfigIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class SecurityConfigIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testLoginEndpoint_NoToken_Accessible() {
        // POST /api/auth/login 无需 Token
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
            "/api/auth/login", new LoginRequest("admin", "password"), ApiResponse.class);
        // 应该返回 401001（用户名或密码错误），而不是 401（未认证）
        assertEquals(401001, response.getBody().getCode());
    }

    @Test
    void testProtectedEndpoint_NoToken_401() {
        // GET /api/agent/list 无 Token
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
            "/api/agent/list", ApiResponse.class);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void testProtectedEndpoint_ValidToken_200() {
        // 先登录获取 Token
        // 用 Token 访问 /api/agent/list
        // 应该返回 200
    }

    @Test
    void testProtectedEndpoint_ExpiredToken_401() {
        // 使用过期 Token 访问
        // 应该返回 401002
    }

    @Test
    void testHealthEndpoint_NoToken_Accessible() {
        // GET /actuator/health 无需 Token
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/actuator/health", String.class);
        assertEquals(200, response.getStatusCode().value());
    }
}
```

### 2.2 认证流程集成测试

**测试类**: `controller/AuthControllerIntegrationTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testFullLoginFlow` | 完整登录流程 | 登录 → 获取 Token → 访问接口 → 退出 → Token 失效 |
| `testLoginWithCaptcha` | 带验证码登录 | 获取验证码 → 输入正确验证码 → 登录成功 |
| `testLoginFailureLock` | 登录失败锁定 | 5 次失败 → 账号锁定 → 拒绝登录 |
| `testTokenRefresh` | Token 刷新 | Access Token 过期 → 用 Refresh Token 刷新 → 新 Token 可用 |
| `testConcurrentLogin` | 多设备登录 | 同一用户在两个设备登录，两个 Token 均有效 |

### 2.3 权限控制集成测试

**测试类**: `controller/PermissionIntegrationTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testSuperAdmin_AllAccess` | 超级管理员全权限 | 所有接口均可访问 |
| `testNormalUser_LimitedAccess` | 普通用户受限 | 只能访问自己的 Agent，不能访问管理员接口 |
| `testGuest_ReadOnly` | 访客只读 | 只能查看被授权的 Agent，不能创建/编辑/删除 |
| `testVerticalPrivilegeEscalation` | 垂直越权防护 | 普通用户调用管理员接口返回 403 |
| `testHorizontalPrivilegeEscalation` | 水平越权防护 | 用户 A 访问用户 B 的 Agent 返回 403 |

### 2.4 Agent 数据权限集成测试

**测试类**: `controller/AgentAuthIntegrationTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testCreatorSeeOwnAgents` | 创建者看到自己的 Agent | 列表只包含自己创建的 |
| `testGrantedUserSeeAgent` | 被授权用户看到 Agent | 列表包含被授权的 Agent |
| `testUnauthorizedUser_NotSee` | 未授权用户看不到 | 列表不包含未授权的 Agent |
| `testSuperAdmin_SeeAll` | 超级管理员看到所有 | 列表包含所有 Agent |
| `testGrantAndRevoke` | 授权和收回 | 授权后可见，收回后不可见 |
| `testPermissionLevel_Edit` | 编辑权限校验 | 只读用户编辑返回 403，编辑权限用户可编辑 |
| `testPermissionLevel_Delete` | 删除权限校验 | 非创建者删除返回 403 |

### 2.5 审计日志集成测试

**测试类**: `controller/AuditLogIntegrationTest.java`

| 测试方法 | 测试内容 | 预期结果 |
|---|---|---|
| `testLoginLog_Recorded` | 登录日志记录 | 登录成功/失败均有日志记录 |
| `testOperationLog_Recorded` | 操作日志记录 | 创建/编辑/删除操作有日志记录 |
| `testOperationLog_SensitiveMarked` | 敏感操作标记 | 密码重置/用户删除日志标记为敏感 |
| `testOperationLog_ParamsDesensitized` | 参数脱敏 | 日志中密码字段为 `***` |
| `testLogWriteFailure_NoImpact` | 日志写入失败不影响业务 | 日志表异常时业务操作正常完成 |

---

## 三、测试配置

### H2 测试配置

**修改文件**: `application-h2.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:data_agent_test;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always
      schema-locations:
        - classpath:sql/h2/schema.sql
        - classpath:sql/h2/schema_rbac.sql
      data-locations:
        - classpath:sql/h2/data.sql
        - classpath:sql/h2/data_rbac.sql

dataagent:
  jwt:
    secret: TestSecretKeyForUnitTestsMustBeLongEnough2026!!
  redis:
    enabled: false  # 测试环境不依赖 Redis

# 禁用 Security（部分测试需要）
spring.security.enabled: false
```

### 测试辅助类

**新建文件**: `test/TestSecurityUtils.java`

```java
public class TestSecurityUtils {
    /** 在测试中模拟登录用户 */
    public static void mockUser(Long userId, String username, List<String> roles, List<String> permissions) {
        DataAgentUserDetails userDetails = new DataAgentUserDetails();
        userDetails.setUserId(userId);
        userDetails.setUsername(username);
        userDetails.setRoles(roles);
        userDetails.setPermissions(permissions);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 清除 SecurityContext */
    public static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
```

---

## 四、测试执行命令

```bash
# 运行所有测试
./mvnw test -pl data-agent-management

# 运行单个测试类
./mvnw test -pl data-agent-management -Dtest=JwtTokenProviderTest

# 运行单个测试方法
./mvnw test -pl data-agent-management -Dtest=AuthServiceImplTest#testLogin_Success

# 运行所有 RBAC 相关测试
./mvnw test -pl data-agent-management -Dtest="security.*,service.auth.*,service.user.*,service.role.*,service.menu.*,service.agent.*,mapper.Sys*"

# 运行集成测试
./mvnw test -pl data-agent-management -Dtest="*IntegrationTest"
```

---

## 五、测试覆盖率目标

| 模块 | 目标覆盖率 |
|---|---|
| `security/` | >= 90% |
| `service/auth/` | >= 85% |
| `service/user/` | >= 85% |
| `service/role/` | >= 85% |
| `service/menu/` | >= 85% |
| `service/agent/AgentAuth*` | >= 85% |
| `mapper/` | >= 80% |
| `controller/` | >= 70%（集成测试覆盖） |
