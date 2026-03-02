# DataAgent 账号初始化与权限管理体系需求文档

## 1. 概述

本文档描述 **DataAgent** 平台的**首次使用初始化流程**和**账号权限管理体系**的 Java 实现方案，涵盖系统初始化、用户账号生命周期管理、角色权限控制等核心功能模块。

**实现方式：** Java + Spring Boot + Spring Security
**架构特点：** 单租户架构，去除工作空间概念，简化权限模型

---

## 2. 首次使用初始化流程

### 2.1 系统初始化状态检测

**功能描述：**
- 系统提供 `/api/setup` 接口用于查询初始化状态
- 支持两种状态：`not_started`（未初始化）和 `finished`（已完成）
- 该接口无需认证，用于首次部署时的引导

**初始化流程：**
```
1. 前端调用 GET /api/setup 检查系统状态
2. 如果状态为 not_started，显示初始化页面
3. 用户填写管理员信息（邮箱、名称、密码）
4. 调用 POST /api/setup 完成初始化
5. 系统自动创建管理员账号，记录初始化状态
```

**Java 实现：**

```java
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    @Autowired
    private SetupService setupService;

    @GetMapping
    public SetupStatusResponse getSetupStatus() {
        boolean isSetup = setupService.isSystemInitialized();
        if (isSetup) {
            SystemSetup setup = setupService.getSetupRecord();
            return new SetupStatusResponse("finished", setup.getSetupAt());
        }
        return new SetupStatusResponse("not_started", null);
    }

    @PostMapping
    public ResponseEntity<SetupResponse> setupSystem(@Valid @RequestBody SetupRequest request) {
        if (setupService.isSystemInitialized()) {
            throw new AlreadySetupException("系统已完成初始化");
        }
        setupService.initializeSystem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SetupResponse("success"));
    }
}
```

### 2.2 初始化参数

```java
public class SetupRequest {
    @NotBlank @Email
    private String email;         // 管理员邮箱地址

    @NotBlank @Size(max = 30)
    private String name;          // 管理员名称（最多30字符）

    @NotBlank
    private String password;      // 管理员密码（需符合密码强度规则）

    private String language;      // 界面语言偏好（可选）
}
```

### 2.3 初始化安全机制

- **INIT_PASSWORD 验证**：当配置项 `dataagent.init-password` 存在时，需先通过验证才能初始化
- **一次性初始化**：系统初始化后无法再次执行，防止重复初始化
- **事务保护**：初始化过程使用 `@Transactional`，失败时自动回滚

**Java 实现：**

```java
@Service
public class SetupService {

    @Value("${dataagent.init-password:}")
    private String initPassword;

    @Autowired
    private SystemSetupRepository setupRepository;
    @Autowired
    private AccountService accountService;

    public boolean isSystemInitialized() {
        return setupRepository.count() > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void initializeSystem(SetupRequest request) {
        // 创建管理员账号
        Account admin = accountService.createAccount(
            request.getEmail(),
            request.getName(),
            request.getPassword(),
            Role.ADMIN,
            true  // isSetup
        );
        admin.setInitializedAt(LocalDateTime.now());
        // 记录初始化状态
        SystemSetup setup = new SystemSetup();
        setup.setVersion(appVersion);
        setupRepository.save(setup);
    }
}
```

---

## 3. 账号数据模型

### 3.1 账号（Account）实体

```java
@Entity
@Table(name = "account")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;                              // 账号唯一标识

    @Column(nullable = false, length = 255)
    private String name;                            // 用户名称

    @Column(nullable = false, unique = true, length = 255)
    private String email;                           // 邮箱地址（唯一）

    @Column(length = 255)
    private String password;                        // 加密后的密码（BCrypt）

    @Column(name = "password_salt", length = 255)
    private String passwordSalt;                    // 密码盐值

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;                  // 角色

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status = AccountStatus.ACTIVE;  // 账号状态

    @Column(name = "initialized_at")
    private LocalDateTime initializedAt;            // 账号激活时间

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;              // 最后登录时间

    @Column(name = "last_login_ip", length = 255)
    private String lastLoginIp;                     // 最后登录IP

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // 权限检查方法
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean hasEditPermission() {
        return role == Role.ADMIN || role == Role.EDITOR;
    }
}
```

### 3.2 账号状态枚举

```java
public enum AccountStatus {
    PENDING,        // 待激活（管理员创建但未登录）
    UNINITIALIZED,  // 未初始化（需要完善信息）
    ACTIVE,         // 正常活跃
    BANNED,         // 已封禁
    CLOSED          // 已关闭
}
```

### 3.3 系统初始化状态实体

```java
@Entity
@Table(name = "system_setup")
public class SystemSetup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setup_at", nullable = false)
    private LocalDateTime setupAt = LocalDateTime.now();

    @Column(name = "version", length = 32)
    private String version;
}
```

---

## 4. 角色与权限体系

### 4.1 角色定义（Role 枚举）

| 角色     | 标识     | 权限级别                     |
| -------- | -------- | ---------------------------- |
| 管理员   | `ADMIN`  | 最高权限，可执行所有管理操作 |
| 编辑者   | `EDITOR` | 编辑权限，可创建和编辑应用   |
| 普通用户 | `USER`   | 使用权限，只能使用已有功能   |

**Java 实现：**

```java
public enum Role {
    ADMIN,      // 系统管理员
    EDITOR,     // 编辑者
    USER;       // 普通用户

    /** 是否为管理员（特权角色） */
    public boolean isPrivileged() {
        return this == ADMIN;
    }

    /** 是否具有编辑权限（ADMIN 或 EDITOR） */
    public boolean hasEditPermission() {
        return this == ADMIN || this == EDITOR;
    }
}
```

### 4.2 角色权限矩阵

| 权限项             | ADMIN | EDITOR | USER |
| ------------------ | ----- | ------ | ---- |
| 系统初始化         | ✅     | ❌      | ❌    |
| 用户管理（增删改） | ✅     | ❌      | ❌    |
| 角色分配           | ✅     | ❌      | ❌    |
| 创建/编辑应用      | ✅     | ✅      | ❌    |
| 使用应用           | ✅     | ✅      | ✅    |
| 查看个人信息       | ✅     | ✅      | ✅    |
| 修改个人密码       | ✅     | ✅      | ✅    |

---

## 5. 权限控制机制

### 5.1 Spring Security 注解权限

| 注解                                            | 功能               |
| ----------------------------------------------- | ------------------ |
| `@PreAuthorize("hasRole('ADMIN')")`             | 要求管理员角色     |
| `@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")` | 要求编辑及以上权限 |
| `@PreAuthorize("isAuthenticated()")`            | 要求已登录         |

### 5.2 Spring Security 配置

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/setup").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 5.3 系统初始化检查拦截器

```java
@Component
public class SetupCheckInterceptor implements HandlerInterceptor {

    @Autowired
    private SetupService setupService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/setup") || uri.startsWith("/api/auth/login")) {
            return true;
        }
        if (!setupService.isSystemInitialized()) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_REQUIRED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"setup_required\",\"message\":\"系统尚未初始化\"}");
            return false;
        }
        return true;
    }
}
```

### 5.4 账号状态检查过滤器

```java
@Component
public class AccountStatusFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountDetails details) {
            Account account = details.getAccount();
            if (account.getStatus() == AccountStatus.BANNED) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"account_banned\",\"message\":\"账号已被封禁\"}");
                return;
            }
            if (account.getStatus() == AccountStatus.UNINITIALIZED) {
                if (!request.getRequestURI().startsWith("/api/me/init")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"account_not_initialized\",\"message\":\"请先完善账号信息\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
```

---

## 6. 用户管理功能

### 6.1 管理员创建用户

**功能描述：**
- 管理员（ADMIN）直接创建用户账号并分配角色
- 无需邮件邀请流程，简化操作
- 支持用户列表查询、角色修改、状态变更、账号删除

**Java 实现：**

```java
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired
    private AccountService accountService;

    @GetMapping
    public PageResult<UserDTO> listUsers(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return accountService.listUsers(page, size);
    }

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        Account account = accountService.createAccount(
            request.getEmail(), request.getName(), request.getPassword(),
            request.getRole(), false
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDTO.from(account));
    }

    @PutMapping("/{id}/role")
    public void updateUserRole(@PathVariable String id,
                                @Valid @RequestBody UpdateRoleRequest request) {
        accountService.updateRole(id, request.getRole());
    }

    @PutMapping("/{id}/status")
    public void updateUserStatus(@PathVariable String id,
                                  @Valid @RequestBody UpdateStatusRequest request) {
        accountService.updateStatus(id, request.getStatus());
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable String id) {
        accountService.deleteAccount(id);
    }
}
```

### 6.2 用户管理业务逻辑

```java
@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public Account createAccount(String email, String name, String password,
                                  Role role, boolean isSetup) {
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("邮箱已被注册");
        }
        Account account = new Account();
        account.setEmail(email);
        account.setName(name);
        account.setPassword(passwordEncoder.encode(password));
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        if (isSetup) {
            account.setInitializedAt(LocalDateTime.now());
        }
        return accountRepository.save(account);
    }

    @Transactional
    public void updateRole(String id, Role newRole) {
        Account account = findById(id);
        account.setRole(newRole);
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    @Transactional
    public void updateStatus(String id, AccountStatus status) {
        Account account = findById(id);
        account.setStatus(status);
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(String id) {
        Account account = findById(id);
        if (account.getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("不能删除自己的账号");
        }
        accountRepository.delete(account);
    }

    private Account findById(String id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException("账号不存在"));
    }
}
```

### 6.3 当前用户信息管理

```java
@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    @Autowired
    private AccountService accountService;

    @GetMapping
    public UserDTO getCurrentUser() {
        return UserDTO.from(SecurityUtils.getCurrentAccount());
    }

    @PutMapping
    public UserDTO updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Account account = SecurityUtils.getCurrentAccount();
        account.setName(request.getName());
        account.setUpdatedAt(LocalDateTime.now());
        return UserDTO.from(accountService.save(account));
    }

    @PutMapping("/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(
            SecurityUtils.getCurrentUserId(),
            request.getOldPassword(),
            request.getNewPassword()
        );
    }
}
```

---

## 7. 认证与授权机制

### 7.1 登录认证流程

**支持方式：** 邮箱密码登录

**登录流程：**
```
1. 用户提交邮箱和密码
2. 系统验证账号状态（不能为 BANNED / CLOSED）
3. 验证密码正确性（BCrypt）
4. 检查登录错误频率限制
5. 生成 JWT Token 对（access_token + refresh_token）
6. 记录登录日志，返回 Token 给客户端
```

**Java 实现：**

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                                HttpServletRequest httpRequest) {
        return authService.login(request.getEmail(), request.getPassword(),
                                  httpRequest.getRemoteAddr());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
    }

    @PostMapping("/refresh")
    public TokenResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.getRefreshToken());
    }
}
```

### 7.2 认证业务逻辑

```java
@Service
public class AuthService {

    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private LoginLogService loginLogService;
    @Autowired private RateLimiterService rateLimiterService;

    public LoginResponse login(String email, String password, String ipAddress) {
        // 频率限制检查
        rateLimiterService.checkLoginRate(email);

        Account account = accountRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("邮箱或密码错误"));

        // 账号状态检查
        if (account.getStatus() == AccountStatus.BANNED) {
            throw new AccountBannedException("账号已被封禁");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException("账号已关闭");
        }

        // 密码验证
        if (!passwordEncoder.matches(password, account.getPassword())) {
            rateLimiterService.recordLoginFailure(email);
            loginLogService.record(account.getId(), "PASSWORD", "FAILED", ipAddress, "密码错误");
            throw new AuthenticationException("邮箱或密码错误");
        }

        // 更新登录信息
        account.setLastLoginAt(LocalDateTime.now());
        account.setLastLoginIp(ipAddress);
        accountRepository.save(account);

        // 生成 Token
        String accessToken = jwtTokenProvider.generateAccessToken(account);
        String refreshToken = jwtTokenProvider.generateRefreshToken(account);

        loginLogService.record(account.getId(), "PASSWORD", "SUCCESS", ipAddress, null);
        rateLimiterService.clearLoginAttempts(email);

        return new LoginResponse(accessToken, refreshToken);
    }

    public void logout(String token) {
        jwtTokenProvider.blacklistToken(token.replace("Bearer ", ""));
    }

    public TokenResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("Refresh Token 无效或已过期");
        }
        String accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("账号不存在"));
        return new TokenResponse(jwtTokenProvider.generateAccessToken(account));
    }
}
```

### 7.3 JWT Token 管理

```java
@Component
public class JwtTokenProvider {

    @Value("${dataagent.jwt.secret}")
    private String jwtSecret;

    @Value("${dataagent.jwt.access-token-expiration:3600}")
    private long accessTokenExpiration;  // 默认1小时

    @Value("${dataagent.jwt.refresh-token-expiration:2592000}")
    private long refreshTokenExpiration;  // 默认30天

    @Autowired
    private TokenBlacklistRepository blacklistRepository;

    public String generateAccessToken(Account account) {
        return Jwts.builder()
            .setSubject(account.getId())
            .claim("email", account.getEmail())
            .claim("role", account.getRole().name())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration * 1000))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
            .compact();
    }

    public String generateRefreshToken(Account account) {
        return Jwts.builder()
            .setSubject(account.getId())
            .claim("type", "refresh")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration * 1000))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build().parseClaimsJws(token);
            return !isTokenBlacklisted(token);
        } catch (JwtException e) {
            return false;
        }
    }

    public String getAccountIdFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
            .build().parseClaimsJws(token)
            .getBody().getSubject();
    }

    public void blacklistToken(String token) {
        TokenBlacklist entry = new TokenBlacklist();
        entry.setToken(token);
        entry.setAccountId(getAccountIdFromToken(token));
        blacklistRepository.save(entry);
    }

    private boolean isTokenBlacklisted(String token) {
        return blacklistRepository.existsByToken(token);
    }
}
```

### 7.4 JWT 认证过滤器

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private AccountRepository accountRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String accountId = jwtTokenProvider.getAccountIdFromToken(token);
            Account account = accountRepository.findById(accountId).orElse(null);
            if (account != null) {
                AccountDetails details = new AccountDetails(account);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

### 7.5 频率限制

| 操作     | 限制策略            |
| -------- | ------------------- |
| 登录错误 | 5次错误后锁定30分钟 |
| 重置密码 | 每邮箱每60秒1次     |
| 修改密码 | 每账号每60秒1次     |

**Java 实现：**

```java
@Service
public class RateLimiterService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 30;

    public void checkLoginRate(String email) {
        String key = "login_attempts:" + email;
        String attempts = redisTemplate.opsForValue().get(key);
        if (attempts != null && Integer.parseInt(attempts) >= MAX_LOGIN_ATTEMPTS) {
            throw new RateLimitExceededException("登录错误次数过多，请稍后重试");
        }
    }

    public void recordLoginFailure(String email) {
        String key = "login_attempts:" + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    public void clearLoginAttempts(String email) {
        redisTemplate.delete("login_attempts:" + email);
    }
}
```

---

## 8. 核心文件清单

| 文件路径                                   | 功能描述             |
| ------------------------------------------ | -------------------- |
| `controller/SetupController.java`          | 系统初始化 API       |
| `controller/AuthController.java`           | 登录认证 API         |
| `controller/UserController.java`           | 用户管理 API         |
| `controller/CurrentUserController.java`    | 当前用户信息 API     |
| `entity/Account.java`                      | 账号实体             |
| `entity/SystemSetup.java`                  | 系统初始化实体       |
| `entity/TokenBlacklist.java`               | Token黑名单实体      |
| `entity/LoginLog.java`                     | 登录日志实体         |
| `entity/OperationLog.java`                 | 操作日志实体         |
| `enums/Role.java`                          | 角色枚举             |
| `enums/AccountStatus.java`                 | 账号状态枚举         |
| `service/SetupService.java`                | 系统初始化业务逻辑   |
| `service/AccountService.java`              | 账号管理业务逻辑     |
| `service/AuthService.java`                 | 认证授权业务逻辑     |
| `service/RateLimiterService.java`          | 频率限制服务         |
| `service/LoginLogService.java`             | 登录日志服务         |
| `security/SecurityConfig.java`             | Spring Security 配置 |
| `security/JwtTokenProvider.java`           | JWT Token 管理       |
| `security/JwtAuthenticationFilter.java`    | JWT 认证过滤器       |
| `security/SetupCheckInterceptor.java`      | 初始化检查拦截器     |
| `security/AccountStatusFilter.java`        | 账号状态检查过滤器   |
| `security/AccountDetails.java`             | 用户认证详情         |
| `security/SecurityUtils.java`              | 安全工具类           |
| `repository/AccountRepository.java`        | 账号数据访问         |
| `repository/SystemSetupRepository.java`    | 初始化状态数据访问   |
| `repository/TokenBlacklistRepository.java` | Token黑名单数据访问  |
| `repository/LoginLogRepository.java`       | 登录日志数据访问     |
| `dto/UserDTO.java`                         | 用户数据传输对象     |
| `dto/LoginRequest.java`                    | 登录请求对象         |
| `dto/SetupRequest.java`                    | 初始化请求对象       |
| `exception/GlobalExceptionHandler.java`    | 全局异常处理         |

---

## 9. 数据库表设计

### 9.1 账号表 (account)

```sql
CREATE TABLE account (
    id              VARCHAR(36) PRIMARY KEY COMMENT '账号ID，UUID',
    name            VARCHAR(255) NOT NULL COMMENT '用户名称',
    email           VARCHAR(255) NOT NULL UNIQUE COMMENT '邮箱地址',
    password        VARCHAR(255) COMMENT '加密后的密码',
    password_salt   VARCHAR(255) COMMENT '密码盐值',
    role            VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/EDITOR/USER',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：PENDING/UNINITIALIZED/ACTIVE/BANNED/CLOSED',
    initialized_at  DATETIME COMMENT '账号激活时间',
    last_login_at   DATETIME COMMENT '最后登录时间',
    last_login_ip   VARCHAR(255) COMMENT '最后登录IP',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号表';
```

### 9.2 系统初始化表 (system_setup)

```sql
CREATE TABLE system_setup (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    setup_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '初始化完成时间',
    version     VARCHAR(32) COMMENT '系统版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统初始化状态表';
```

### 9.3 登录日志表 (login_log)

```sql
CREATE TABLE login_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    account_id      VARCHAR(36) NOT NULL COMMENT '账号ID',
    login_type      VARCHAR(32) NOT NULL COMMENT '登录类型：PASSWORD/EMAIL_CODE/REFRESH',
    login_status    VARCHAR(16) NOT NULL COMMENT '登录状态：SUCCESS/FAILED',
    ip_address      VARCHAR(255) COMMENT '登录IP',
    user_agent      VARCHAR(512) COMMENT '用户代理',
    fail_reason     VARCHAR(255) COMMENT '失败原因',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_account_id (account_id),
    INDEX idx_login_status (login_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';
```

### 9.4 Token黑名单表 (token_blacklist)

```sql
CREATE TABLE token_blacklist (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    token       VARCHAR(512) NOT NULL COMMENT 'Token内容',
    token_type  VARCHAR(32) NOT NULL COMMENT 'Token类型：ACCESS/REFRESH',
    account_id  VARCHAR(36) NOT NULL COMMENT '账号ID',
    expire_at   DATETIME NOT NULL COMMENT '过期时间',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_token (token(255)),
    INDEX idx_account_id (account_id),
    INDEX idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token黑名单表';
```

### 9.5 操作日志表 (operation_log)

```sql
CREATE TABLE operation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    account_id      VARCHAR(36) COMMENT '操作人账号ID',
    operation_type  VARCHAR(64) NOT NULL COMMENT '操作类型：USER_CREATE/USER_UPDATE/USER_DELETE/PASSWORD_CHANGE等',
    resource_type   VARCHAR(64) COMMENT '资源类型：USER/SYSTEM等',
    resource_id     VARCHAR(36) COMMENT '资源ID',
    old_value       TEXT COMMENT '变更前内容（JSON）',
    new_value       TEXT COMMENT '变更后内容（JSON）',
    ip_address      VARCHAR(255) COMMENT '操作IP',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_account_id (account_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_resource_type (resource_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
```

### 9.6 数据库表关系图

```
┌─────────────────┐
│   system_setup  │
│  (系统初始化状态) │
└─────────────────┘

┌─────────────────┐     ┌─────────────────┐
│     account     │────▶│   login_log     │
│    (账号表)      │     │   (登录日志)     │
└─────────────────┘     └─────────────────┘
         │
         │              ┌─────────────────┐
         └─────────────▶│  operation_log  │
                        │   (操作日志)     │
                        └─────────────────┘

┌─────────────────┐
│ token_blacklist │
│  (Token黑名单)  │
└─────────────────┘
```

### 9.7 初始化数据

```sql
-- 插入初始管理员账号（密码为 admin123，需使用BCrypt加密）
-- 注意：实际使用时密码应通过程序加密后插入
INSERT INTO account (id, name, email, password, password_salt, role, status, initialized_at, created_at, updated_at)
VALUES (
    'admin-0000-0000-0000-000000000001',
    '系统管理员',
    'admin@example.com',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO',  -- BCrypt加密后的admin123
    '',
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW(),
    NOW()
);

-- 标记系统已初始化
INSERT INTO system_setup (setup_at, version) VALUES (NOW(), '1.0.0');
```

---

## 10. 与 Python 原版对比

### 10.1 权限检查方式对比

| 功能         | Python 原版 (Dify)                             | Java 版 (DataAgent)                                                                  |
| ------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------ |
| 权限控制     | `@login_required`, `@edit_permission_required` | `@PreAuthorize("hasRole('ADMIN')")`                                                  |
| 当前用户获取 | `current_user`                                 | `SecurityContextHolder.getContext().getAuthentication()`                             |
| 角色检查     | `current_user.is_admin_or_owner`               | `authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))` |
| 方法级权限   | 自定义装饰器                                   | `@PreAuthorize("@authService.hasEditPermission(#id)")`                               |

### 10.2 架构简化对比

| 功能模块           | Python 原版 (Dify)                         | Java 版 (DataAgent)                 |
| ------------------ | ------------------------------------------ | ----------------------------------- |
| 工作空间（Tenant） | 多对多关联，支持多工作空间                 | **移除**，单系统单组织              |
| 角色体系           | owner/admin/editor/normal/dataset_operator | ADMIN/EDITOR/USER                   |
| 成员管理           | 邮件邀请 + Token激活流程                   | **简化**，管理员直接创建账号        |
| 工作空间切换       | 需要切换当前工作空间                       | **移除**，无工作空间概念            |
| 权限检查           | 多层级（系统 + 工作空间 + 功能）           | 单层级（系统级角色控制）            |
| 企业版功能         | SSO/SAML/OIDC、插件权限、计费计划          | **移除**，仅保留核心功能            |
| 技术栈             | Flask + SQLAlchemy + Flask-Login           | Spring Boot + Spring Security + JPA |