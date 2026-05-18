# 第五阶段：Agent 数据权限隔离与授权

> 目标：实现 Agent 资源的数据级别权限隔离，支持按用户/角色授权。

---

## 任务 5.1：数据权限拦截器

**新建文件**: `security/DataPermissionInterceptor.java`

**职责**: 在 MyBatis 查询 Agent 列表时，自动注入数据权限过滤 SQL 条件。

**实现方案**: 使用 MyBatis 拦截器（`Interceptor`），拦截 `AgentMapper` 的查询方法。

```java
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
@Component
public class DataPermissionInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 1. 获取当前用户信息
        // 2. 根据角色判断数据权限范围
        // 3. 在 SQL 的 WHERE 子句中注入过滤条件
        // 4. 使用参数化查询，禁止字符串拼接
    }
}
```

**数据权限规则**:

| 角色 | 过滤条件 |
|---|---|
| SUPER_ADMIN | 无过滤（返回所有） |
| TENANT_ADMIN | `WHERE tenant_id = #{currentTenantId}` |
| AUDITOR | 无过滤（只读） |
| OPS_ADMIN | 无过滤（只读） |
| NORMAL_USER | `WHERE creator_id = #{currentUserId} OR id IN (SELECT agent_id FROM sys_agent_auth WHERE ...)` |
| GUEST | `WHERE id IN (SELECT agent_id FROM sys_agent_auth WHERE auth_type = 1 AND auth_target_id = #{currentUserId})` |

**SQL 注入防护**:
- 所有过滤条件使用 `#{}` 参数占位符
- 禁止使用 `${}` 字符串拼接
- 使用 MyBatis 的 `BoundSql` 获取原始 SQL，通过 `StringBuilder` 拼接参数化条件

**单元测试** (`DataPermissionInterceptorTest.java`):
- 测试 SUPER_ADMIN 不注入过滤条件
- 测试 TENANT_ADMIN 注入 tenant_id 条件
- 测试 NORMAL_USER 注入 creator_id 和授权子查询条件
- 测试 GUEST 仅注入授权子查询条件
- 测试非 Agent 查询不注入条件

---

## 任务 5.2：Agent 授权 DTO

**新建文件**（均在 `dto/agent/` 包下）:

### AgentAuthGrantRequest.java
```java
@Data
public class AgentAuthGrantRequest {
    @NotNull private Long targetId;  // 用户 ID 或角色 ID
    @NotNull @InEnum(value = PermissionLevelEnum.class)
    private Integer permissionLevel;  // 1=只读, 2=编辑, 3=管理
}
```

### AgentAuthRevokeRequest.java
```java
@Data
public class AgentAuthRevokeRequest {
    @NotNull private Long authId;
}
```

### AgentAuthResponse.java
```java
@Data
@Builder
public class AgentAuthResponse {
    private Long id;
    private Long agentId;
    private Integer authType;
    private Long authTargetId;
    private String authTargetName;  // 用户名或角色名
    private Integer permissionLevel;
    private String createBy;
    private LocalDateTime createTime;
}
```

### PermissionLevelEnum.java
```java
public enum PermissionLevelEnum {
    READ_ONLY(1), EDIT(2), MANAGE(3);
    private final int value;
    PermissionLevelEnum(int value) { this.value = value; }
    public int getValue() { return value; }
}
```

---

## 任务 5.3：Agent 授权 Service

**新建文件**: `service/agent/AgentAuthService.java`

```java
public interface AgentAuthService {
    /** 授权 Agent 给用户 */
    void grantToUser(Long agentId, Long userId, Integer permissionLevel, Long operatorId);

    /** 授权 Agent 给角色 */
    void grantToRole(Long agentId, Long roleId, Integer permissionLevel, Long operatorId);

    /** 收回授权 */
    void revokeAuth(Long authId, Long operatorId);

    /** 查询 Agent 的授权列表 */
    List<AgentAuthResponse> listAgentAuths(Long agentId);

    /** 检查用户对 Agent 的访问权限 */
    boolean hasAccess(Long userId, Long agentId);

    /** 检查用户对 Agent 的编辑权限 */
    boolean canEdit(Long userId, Long agentId);

    /** 检查用户对 Agent 的删除权限（仅创建者） */
    boolean canDelete(Long userId, Long agentId);
}
```

**新建文件**: `service/agent/AgentAuthServiceImpl.java`

**关键实现逻辑**:

**grantToUser**:
1. 校验当前用户为 Agent 创建者或管理员
2. 校验目标用户存在
3. 查询是否已有授权记录（`selectByUniqueKey`）
   - 已存在 → 更新权限级别（upsert 语义）
   - 不存在 → 插入新授权记录
4. 记录操作日志

**grantToRole**:
1. 校验当前用户为 Agent 创建者或管理员
2. 校验目标角色存在
3. 查询是否已有授权记录
   - 已存在 → 更新权限级别
   - 不存在 → 插入新授权记录
4. 记录操作日志

**revokeAuth**:
1. 校验授权记录存在
2. 校验当前用户为 Agent 创建者或管理员
3. 删除授权记录
4. 记录操作日志

**hasAccess**:
```java
public boolean hasAccess(Long userId, Long agentId) {
    // 1. 查询 Agent
    // 2. 如果是创建者 → true
    // 3. 查询用户角色
    // 4. 如果有 SUPER_ADMIN/TENANT_ADMIN/AUDITOR/OPS_ADMIN 角色 → true
    // 5. 查询用户级授权
    // 6. 查询角色级授权（用户角色与授权角色交集）
    // 7. 有任何授权记录 → true
    // 8. 否则 → false
}
```

**canEdit**:
```java
public boolean canEdit(Long userId, Long agentId) {
    // 1. 如果是创建者 → true
    // 2. 查询用户角色
    // 3. 如果有 SUPER_ADMIN/TENANT_ADMIN → true
    // 4. 查询用户级授权，permissionLevel >= 2 → true
    // 5. 查询角色级授权，permissionLevel >= 2 → true
    // 6. 否则 → false
}
```

**canDelete**:
```java
public boolean canDelete(Long userId, Long agentId) {
    // 1. 如果是创建者 → true
    // 2. 如果有 SUPER_ADMIN 角色 → true
    // 3. 否则 → false
}
```

---

## 任务 5.4：Agent 授权 Controller

**新建文件**: `controller/AgentAuthController.java`

```java
@RestController
@RequestMapping("/api/agent/{agentId}/auth")
public class AgentAuthController {

    @GetMapping
    @PreAuthorize("hasAuthority('agent:view')")
    public ApiResponse<List<AgentAuthResponse>> listAuths(@PathVariable Long agentId) { ... }

    @PostMapping("/user")
    @PreAuthorize("hasAuthority('agent:authorize')")
    public ApiResponse<Void> grantToUser(@PathVariable Long agentId,
                                          @RequestBody @Valid AgentAuthGrantRequest request) { ... }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority('agent:authorize')")
    public ApiResponse<Void> grantToRole(@PathVariable Long agentId,
                                          @RequestBody @Valid AgentAuthGrantRequest request) { ... }

    @DeleteMapping("/{authId}")
    @PreAuthorize("hasAuthority('agent:authorize')")
    public ApiResponse<Void> revokeAuth(@PathVariable Long agentId,
                                         @PathVariable Long authId) { ... }
}
```

---

## 任务 5.5：现有 Agent Controller 改造

**修改文件**: `controller/AgentController.java`

**改造内容**:

1. 所有方法添加 `@PreAuthorize` 注解
2. 创建 Agent 时自动设置 `creatorId` = 当前用户 ID
3. 删除 Agent 时校验 `canDelete` 权限
4. 编辑 Agent 时校验 `canEdit` 权限
5. 查询 Agent 列表时由数据权限拦截器自动过滤

```java
// 创建 Agent — 自动设置 creatorId
@PostMapping
@PreAuthorize("hasAuthority('agent:create')")
public ApiResponse<Agent> createAgent(@RequestBody Agent agent) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    agent.setCreatorId(currentUserId);
    return ApiResponse.success(agentService.createAgent(agent));
}

// 删除 Agent — 校验删除权限
@DeleteMapping("/{id}")
@PreAuthorize("hasAuthority('agent:delete')")
public ApiResponse<Void> deleteAgent(@PathVariable Long id) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    if (!agentAuthService.canDelete(currentUserId, id.longValue())) {
        throw new BizException(403002, "无权操作他人的资源");
    }
    agentService.deleteAgent(id);
    return ApiResponse.success(null);
}

// 编辑 Agent — 校验编辑权限
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('agent:edit')")
public ApiResponse<Agent> updateAgent(@PathVariable Long id, @RequestBody Agent agent) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    if (!agentAuthService.canEdit(currentUserId, id.longValue())) {
        throw new BizException(403002, "无权操作他人的资源");
    }
    return ApiResponse.success(agentService.updateAgent(id, agent));
}
```

**注意**: Agent 实体的 `adminId` 已在第二阶段重命名为 `creatorId`，需同步更新所有引用。

---

## 任务 5.6：Agent 实体重命名

**修改文件**:

| 文件 | 改动 |
|---|---|
| `entity/Agent.java` | `adminId` → `creatorId` |
| `mapper/AgentMapper.java` | 更新所有引用 `admin_id` 的方法 |
| `mapper/AgentMapper.xml` | 更新 SQL 中的 `admin_id` → `creator_id` |
| `service/agent/AgentServiceImpl.java` | 更新 `adminId` 引用 |
| `controller/AgentController.java` | 更新 `adminId` 引用 |
| 所有引用 `Agent.getAdminId()` 的地方 | 改为 `Agent.getCreatorId()` |

**搜索命令**: `grep -r "adminId\|admin_id" --include="*.java" --include="*.xml"` 找到所有需要修改的位置。

---

## 任务 5.7：ChatController userId 改造

**修改文件**: `controller/ChatController.java`

**改造内容**:
- 所有获取 `userId` 的地方，从请求体参数改为从 JWT Token 中获取
- 使用 `SecurityUtils.getCurrentUserId()` 替代请求体中的 userId

```java
// 改造前
@PostMapping("/agent/{id}/sessions")
public ApiResponse<ChatSession> createSession(@PathVariable Integer id,
                                                @RequestBody Map<String, Object> body) {
    Long userId = Long.valueOf(body.get("userId").toString());
    // ...
}

// 改造后
@PostMapping("/agent/{id}/sessions")
public ApiResponse<ChatSession> createSession(@PathVariable Integer id) {
    Long userId = SecurityUtils.getCurrentUserId();
    // ...
}
```

**搜索所有 `userId` 引用**: `grep -r "userId" --include="*.java" controller/ChatController.java` 确保所有位置都已改造。
