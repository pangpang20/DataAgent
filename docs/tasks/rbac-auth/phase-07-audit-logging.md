# 第七阶段：审计日志

> 目标：实现完整的登录日志、操作日志、权限变更日志记录。

---

## 任务 7.1：操作日志注解

**新建文件**: `annotation/OperationLog.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /** 操作类型：CREATE/UPDATE/DELETE/LOGIN 等 */
    String operation();

    /** 操作模块：user/role/menu/agent/datasource 等 */
    String module() default "";

    /** 是否敏感操作：密码重置、用户删除、角色删除等 */
    boolean sensitive() default false;

    /** 操作描述（用于日志展示） */
    String description() default "";
}
```

---

## 任务 7.2：操作日志切面

**新建文件**: `aspect/OperationLogAspect.java`

```java
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Autowired
    private SysOperationLogMapper operationLogMapper;

    @Autowired
    private AsyncLogService asyncLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        int logResult = 1;  // 成功
        String errorMsg = null;

        try {
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            logResult = 0;  // 失败
            errorMsg = e.getMessage();
            throw e;
        } finally {
            // 异步记录操作日志
            long duration = System.currentTimeMillis() - startTime;
            asyncLogService.logOperation(point, operationLog, logResult, errorMsg, duration);
        }
    }
}
```

---

## 任务 7.3：异步日志服务

**新建文件**: `service/log/AsyncLogService.java`

```java
@Service
@Slf4j
public class AsyncLogService {

    @Autowired
    private SysOperationLogMapper operationLogMapper;

    @Autowired
    private SysLoginLogMapper loginLogMapper;

    /** 异步记录操作日志 */
    @Async
    public void logOperation(ProceedingJoinPoint point, OperationLog annotation,
                              int result, String errorMsg, long duration) {
        try {
            SysOperationLog log = new SysOperationLog();
            // 获取当前用户信息
            try {
                log.setUserId(SecurityUtils.getCurrentUserId());
                log.setUsername(SecurityUtils.getCurrentUsername());
            } catch (Exception e) {
                // 未登录场景（如登录接口本身）
            }

            log.setOperation(annotation.operation());
            log.setModule(annotation.module());
            log.setResult(result);
            log.setErrorMsg(errorMsg);
            log.setDuration((int) duration);
            log.setOperateTime(LocalDateTime.now());

            // 从 HttpServletRequest 获取信息
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                log.setMethod(request.getMethod());
                log.setUrl(request.getRequestURI());
                log.setIp(getClientIp(request));
                log.setParams(serializeParams(point, annotation.sensitive()));
            }

            operationLogMapper.insert(log);
        } catch (Exception e) {
            // 日志写入失败不影响业务
            log.error("操作日志写入失败", e);
        }
    }

    /** 异步记录登录日志 */
    @Async
    public void logLogin(String username, String ip, String userAgent,
                          int status, String failReason) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUsername(username);
            loginLog.setIp(ip);
            loginLog.setUserAgent(userAgent);
            loginLog.setStatus(status);
            loginLog.setFailReason(failReason);
            loginLog.setLoginTime(LocalDateTime.now());

            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("登录日志写入失败", e);
        }
    }

    /** 参数序列化（敏感字段脱敏） */
    private String serializeParams(ProceedingJoinPoint point, boolean sensitive) {
        Object[] args = point.getArgs();
        // 过滤掉 HttpServletRequest, HttpServletResponse 等不可序列化对象
        // 敏感字段（password, oldPassword, newPassword）替换为 "***"
        // 使用 JSON 序列化
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
```

**关键细节**:
- 使用 `@Async` 异步执行，不阻塞业务线程
- 参数脱敏：`password`、`oldPassword`、`newPassword`、`apiKey` 等字段替换为 `***`
- 日志写入失败不影响业务操作，仅记录到应用日志
- 需要在启动类添加 `@EnableAsync`

---

## 任务 7.4：为现有 Service 添加日志注解

在需要记录操作日志的 Service 方法上添加 `@OperationLog` 注解：

### 用户管理日志

```java
// UserServiceImpl.java
@OperationLog(operation = "CREATE", module = "user", description = "创建用户")
public UserDetailResponse createUser(UserCreateRequest request, Long operatorId) { ... }

@OperationLog(operation = "UPDATE", module = "user", description = "编辑用户")
public UserDetailResponse updateUser(Long userId, UserUpdateRequest request, Long operatorId) { ... }

@OperationLog(operation = "DELETE", module = "user", sensitive = true, description = "删除用户")
public void deleteUser(Long userId, Long operatorId) { ... }

@OperationLog(operation = "UPDATE", module = "user", description = "切换用户状态")
public void toggleUserStatus(Long userId, Integer status, Long operatorId) { ... }

@OperationLog(operation = "UPDATE", module = "user", sensitive = true, description = "重置密码")
public void resetPassword(Long userId, String newPassword, Long operatorId) { ... }

@OperationLog(operation = "UPDATE", module = "user", description = "分配角色")
public void assignRoles(Long userId, List<Long> roleIds, Long operatorId) { ... }
```

### 角色管理日志

```java
// RoleServiceImpl.java
@OperationLog(operation = "CREATE", module = "role", description = "创建角色")
public RoleDetailResponse createRole(RoleCreateRequest request, Long operatorId) { ... }

@OperationLog(operation = "DELETE", module = "role", sensitive = true, description = "删除角色")
public void deleteRole(Long roleId, Long operatorId) { ... }

@OperationLog(operation = "UPDATE", module = "role", description = "菜单授权")
public void assignMenus(Long roleId, List<Long> menuIds, Long operatorId) { ... }

@OperationLog(operation = "UPDATE", module = "role", description = "权限授权")
public void assignPermissions(Long roleId, List<Long> permissionIds, Long operatorId) { ... }
```

### 菜单管理日志

```java
// MenuServiceImpl.java
@OperationLog(operation = "CREATE", module = "menu", description = "创建菜单")
public MenuTreeDTO createMenu(MenuCreateRequest request, Long operatorId) { ... }

@OperationLog(operation = "DELETE", module = "menu", description = "删除菜单")
public void deleteMenu(Long menuId, Long operatorId) { ... }
```

### Agent 授权日志

```java
// AgentAuthServiceImpl.java
@OperationLog(operation = "CREATE", module = "agent", description = "授权Agent给用户")
public void grantToUser(Long agentId, Long userId, Integer permissionLevel, Long operatorId) { ... }

@OperationLog(operation = "DELETE", module = "agent", description = "收回Agent授权")
public void revokeAuth(Long authId, Long operatorId) { ... }
```

### 登录日志

在 `AuthServiceImpl.login()` 方法中，调用 `asyncLogService.logLogin()` 记录每次登录尝试。

---

## 任务 7.5：日志查询接口

**新建文件**: `controller/LogController.java`

```java
@RestController
@RequestMapping("/api/system/log")
public class LogController {

    @GetMapping("/login")
    @PreAuthorize("hasAuthority('system:log:view')")
    public ApiResponse<PageResult<SysLoginLog>> listLoginLogs(LoginLogQueryDTO query) { ... }

    @GetMapping("/operation")
    @PreAuthorize("hasAuthority('system:log:view')")
    public ApiResponse<PageResult<SysOperationLog>> listOperationLogs(OperationLogQueryDTO query) { ... }
}
```

**新建文件**: `dto/log/LoginLogQueryDTO.java`

```java
@Data
public class LoginLogQueryDTO {
    private String username;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 10;
}
```

**新建文件**: `dto/log/OperationLogQueryDTO.java`

```java
@Data
public class OperationLogQueryDTO {
    private String username;
    private String operation;
    private String module;
    private Integer result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 10;
}
```

---

## 任务 7.6：启用异步支持

**修改文件**: `DataAgentApplication.java`

在启动类上添加 `@EnableAsync`：

```java
@SpringBootApplication
@EnableAsync
public class DataAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataAgentApplication.class, args);
    }
}
```

**修改文件**: `application.yml`

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 2
        max-size: 10
        queue-capacity: 100
      thread-name-prefix: async-log-
```
