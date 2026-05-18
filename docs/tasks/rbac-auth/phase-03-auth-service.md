# 第三阶段：认证服务

> 目标：实现完整的登录/退出/Token 刷新/验证码功能。

---

## 任务 3.1：DTO 定义

**新建文件**（均在 `dto/auth/` 包下）:

### LoginRequest.java
```java
@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码（登录失败3次后必填） */
    private String captchaCode;

    /** 验证码 key */
    private String captchaKey;

    /** 记住我 */
    private Boolean rememberMe;
}
```

### LoginResponse.java
```java
@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfoDTO userInfo;
    private List<MenuTreeDTO> menus;
    private List<String> permissions;
}
```

### UserInfoDTO.java
```java
@Data
@Builder
public class UserInfoDTO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private List<String> roles;
}
```

### MenuTreeDTO.java
```java
@Data
@Builder
public class MenuTreeDTO {
    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String path;
    private String component;
    private String permission;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private List<MenuTreeDTO> children;
}
```

### RefreshTokenRequest.java
```java
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
```

### CaptchaResponse.java
```java
@Data
@Builder
public class CaptchaResponse {
    private String captchaKey;
    /** Base64 编码的验证码图片 */
    private String captchaImage;
}
```

### ChangePasswordRequest.java
```java
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
```

### ResetPasswordRequest.java
```java
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
```

---

## 任务 3.2：认证 Service 接口

**新建文件**: `service/auth/AuthService.java`

```java
public interface AuthService {
    /** 用户登录 */
    LoginResponse login(LoginRequest request, String ip, String userAgent);

    /** 用户退出 */
    void logout(String accessToken);

    /** 刷新 Token */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /** 获取验证码 */
    CaptchaResponse getCaptcha();

    /** 获取当前用户信息（含菜单+权限） */
    LoginResponse getCurrentUser(Long userId);
}
```

---

## 任务 3.3：认证 Service 实现

**新建文件**: `service/auth/AuthServiceImpl.java`

**注入依赖**:
- `SysUserMapper`
- `SysRoleMapper`
- `SysMenuMapper`
- `SysPermissionMapper`
- `JwtTokenProvider`
- `TokenBlacklistService`
- `PasswordEncoder` (BCrypt)
- `JwtProperties`
- `RedisTemplate<String, Object>` (用于登录失败计数和验证码缓存)
- `SysLoginLogMapper`

**login 方法逻辑**:

```
1. 根据 username 查询用户
   - 不存在 → 记录登录失败日志，返回 401001
2. 校验用户状态
   - 禁用 (status=0) → 返回 401004
3. 校验账号是否被锁定
   - lockTime > now → 返回 401005，提示剩余锁定时间
4. 查询 Redis 登录失败次数
   - >= 3 次 → 校验验证码
     - 验证码为空 → 返回 400，要求输入验证码
     - 验证码错误 → 返回 400，验证码错误
5. BCrypt 比对密码
   - 错误 → 递增失败次数
     - 失败次数 >= 5 → 锁定账号 30 分钟
     - 记录登录失败日志
     - 返回 401001
   - 正确 → 清零失败次数
6. 查询用户角色列表
7. 查询用户权限列表（通过角色关联）
8. 签发 Access Token + Refresh Token
9. 更新最后登录时间和 IP
10. 记录登录成功日志
11. 构建菜单树（根据角色查询菜单，构建三级树形结构）
12. 返回 LoginResponse
```

**logout 方法逻辑**:
```
1. 解析 Token，获取过期时间
2. 计算剩余有效期
3. 将 Token 加入 Redis 黑名单
```

**refreshToken 方法逻辑**:
```
1. 校验 Refresh Token 有效性
2. 校验 Refresh Token 是否在黑名单中
3. 从 Refresh Token 提取 userId
4. 查询用户信息，校验状态
5. 查询角色和权限
6. 签发新的 Access Token
7. 返回 LoginResponse（仅包含 accessToken，不含 refreshToken）
```

**getCurrentUser 方法逻辑**:
```
1. 根据 userId 查询用户
2. 查询角色列表
3. 查询权限列表
4. 构建菜单树
5. 返回 LoginResponse
```

**菜单树构建算法**:
```java
private List<MenuTreeDTO> buildMenuTree(List<SysMenu> menus) {
    // 1. 过滤 menuType != 2（按钮不进入菜单树）
    // 2. 按 parentId 分组
    // 3. 递归构建树形结构
    // 4. 按 sortOrder 排序
}
```

**Redis Key 设计**:
- 登录失败计数: `login:fail:{username}` — TTL 30 分钟
- 验证码: `captcha:{captchaKey}` — TTL 5 分钟
- Token 黑名单: `token:blacklist:{tokenHash}` — TTL = Token 剩余有效期

---

## 任务 3.4：认证 Controller

**新建文件**: `controller/AuthController.java`

```java
@RestController
@RequestMapping("/api/auth")
public class AuthService {

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request,
                                             HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ApiResponse.success(authService.login(request, ip, userAgent));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        authService.logout(token);
        return ApiResponse.success(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request));
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> getCaptcha() {
        return ApiResponse.success(authService.getCaptcha());
    }

    @GetMapping("/current-user")
    public ApiResponse<LoginResponse> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(authService.getCurrentUser(userId));
    }
}
```

**注意**: `/api/auth/login`、`/api/auth/refresh`、`/api/auth/captcha` 已在 SecurityConfig 中配置为白名单。

---

## 任务 3.5：Security 工具类

**新建文件**: `security/SecurityUtils.java`

```java
public class SecurityUtils {
    /** 获取当前登录用户 ID */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(401003, "未认证");
        }
        // 从 principal 中提取 userId
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        // userId 存储在 UserDetails 的 username 字段（约定 username 格式为 "userId:username"）
        // 或者使用自定义 UserDetails 实现
        return Long.parseLong(userDetails.getUsername());
    }

    /** 获取当前登录用户名 */
    public static String getCurrentUsername() { ... }

    /** 获取当前用户角色列表 */
    public static List<String> getCurrentRoles() { ... }

    /** 获取当前用户权限列表 */
    public static List<String> getCurrentPermissions() { ... }

    /** 检查当前用户是否有指定权限 */
    public static boolean hasPermission(String permission) { ... }

    /** 检查当前用户是否有指定角色 */
    public static boolean hasRole(String role) { ... }
}
```

**实现细节**: 使用自定义 `DataAgentUserDetails` 实现，封装 userId、username、roles、permissions。

**新建文件**: `security/DataAgentUserDetails.java`

```java
@Data
public class DataAgentUserDetails implements UserDetails {
    private Long userId;
    private String username;
    private String password;
    private Integer status;
    private List<String> roles;
    private List<String> permissions;
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 将 permissions 转换为 SimpleGrantedAuthority
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return status != 0; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return status == 1; }
}
```

---

## 任务 3.6：验证码生成

**新建文件**: `security/CaptchaService.java`

```java
public interface CaptchaService {
    /** 生成验证码，返回 captchaKey 和 Base64 图片 */
    CaptchaResponse generateCaptcha();

    /** 校验验证码，校验成功后删除缓存 */
    boolean validateCaptcha(String captchaKey, String captchaCode);
}
```

**新建文件**: `security/CaptchaServiceImpl.java`

**实现**:
- 使用 `easy-captcha` 库生成算术验证码（`ArithmeticCaptcha`）
- captchaKey 使用 UUID
- 验证码文本存入 Redis，key = `captcha:{captchaKey}`，TTL = 5 分钟
- 校验时比对（忽略大小写），校验成功后删除 Redis key
- Redis 不可用时降级：`validateCaptcha` 返回 true（记录 WARN 日志）

**单元测试**:
- 测试生成验证码返回有效 Base64 图片
- 测试正确验证码校验通过
- 测试错误验证码校验失败
- 测试过期验证码校验失败
- 测试 Redis 不可用时降级

---

## 任务 3.7：全局异常处理

**修改文件**: `aop/ExceptionAdvice.java`

新增异常处理：

```java
/** 认证异常 - 401 */
@ExceptionHandler(AuthenticationException.class)
public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
    if (e instanceof BadCredentialsException) {
        return ApiResponse.error(401001, "用户名或密码错误");
    } else if (e instanceof DisabledException) {
        return ApiResponse.error(401004, "账号已被禁用");
    } else if (e instanceof LockedException) {
        return ApiResponse.error(401005, "账号已被锁定");
    } else if (e instanceof CredentialsExpiredException) {
        return ApiResponse.error(401002, "Token已过期");
    }
    return ApiResponse.error(401003, "认证失败");
}

/** 权限异常 - 403 */
@ExceptionHandler(AccessDeniedException.class)
public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
    return ApiResponse.error(403001, "无权限访问该资源");
}

/** 业务异常 */
@ExceptionHandler(BizException.class)
public ApiResponse<Void> handleBizException(BizException e) {
    return ApiResponse.error(e.getCode(), e.getMessage());
}
```

**新建文件**: `exception/BizException.java`

```java
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

---

## 任务 3.8：ApiResponse 适配

**修改文件**: `vo/ApiResponse.java`

确保 `ApiResponse` 支持错误码和消息：

```java
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }
}
```

**注意**: 检查现有 `ApiResponse` 是否已有此结构，避免重复定义。
